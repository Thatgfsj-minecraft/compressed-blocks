// 压缩方块 E2E：RCON 服务端权威断言（无需玩家在线：方块用 setblock/assert，
// 物品挂到盔甲架主手再 /data get 读组件）。
// 用法：服务器启动后 `node e2e.js`；结束自动 /stop，退出码 = 失败数。
const { Rcon } = require('./rcon');

// RCON 端口可用环境变量覆盖（多套测试服并行时错开端口）
const PORT = parseInt(process.env.RCON_PORT || '25576', 10);
const NS = 'compressedblocks';

const LEVELS = ['1x', '2x', '3x', '4x', '5x', '6x', '7x', '8x', '9x'];
const MATS = ['cobblestone', 'stone', 'cobbled_deepslate', 'deepslate',
  'oak_log', 'spruce_log', 'birch_log', 'jungle_log', 'acacia_log', 'dark_oak_log',
  'mangrove_log', 'cherry_log', 'pale_oak_log',
  'dirt', 'sand', 'gravel', 'netherrack', 'end_stone', 'obsidian'];
const TOOL_MATS = MATS.slice(0, 13);
const TOOLS = ['pickaxe', 'axe', 'shovel', 'hoe', 'sword'];

// 与 CompressedBlocks.java 一致的期望值
const BASE_DUR = { stone: 131, wood: 59 };
const SPEED_LADDER = [6.0, 8.0, 12.0, 18.0, 27.0, 40.5, 60.75, 91.125, 136.6875];
const UNBREAKABLE_FROM = 6;

function durability(level, kind) {
  if (level >= UNBREAKABLE_FROM) return 2147483647;
  return BASE_DUR[kind] * Math.pow(9, level);
}

const BLOCKS = MATS.flatMap((m) => LEVELS.map((p) => `${p}_${m}`));
const STICKS = LEVELS.map((p) => `${p}_stick`);
const TOOLS_ALL = TOOL_MATS.flatMap((m) => LEVELS.flatMap((p) => TOOLS.map((t) => `${p}_${m}_${t}`)));

let failures = 0;
function check(name, ok, detail) {
  if (!ok) {
    failures++;
    console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`);
  }
}

(async () => {
  const rcon = await new Rcon().connect(PORT, '127.0.0.1', 'testpass');
  const cmd = (c) => rcon.command(c);

  // 0) 探针自检：先证明测试工具自身可靠（超平坦 0 -64 0 必是基岩）
  // 测试区先 forceload，防区块卸载导致实体丢失/方块断言失败
  await cmd('forceload add -48 -48 47 47');
  const probe1 = await cmd('execute if block 0 -64 0 minecraft:bedrock');
  check('probe-self bedrock', /Test passed/.test(probe1), probe1);
  const probe2 = await cmd('execute if block 0 -64 0 minecraft:stone');
  check('probe-self negative', /Test failed/.test(probe2), probe2);

  // 1) 全矩阵方块：放置 + 服务端方块断言（171 个）
  let slot = 0;
  for (const b of BLOCKS) {
    const x = 2 + (slot % 15);
    const z = 2 + Math.floor(slot / 15);
    slot++;
    const pos = `${x} -60 ${z}`;
    await cmd(`setblock ${pos} ${NS}:${b}`);
    const res = await cmd(`execute if block ${pos} ${NS}:${b}`);
    check(`block ${b}`, /Test passed/.test(res), res.trim());
  }
  // 清场（抑制后续干扰）
  for (let i = 0; i < slot; i++) {
    await cmd(`setblock ${2 + (i % 15)} -60 ${2 + Math.floor(i / 15)} minecraft:air`);
  }

  // 2) 战利品表：破坏掉落自身（抽样：二重压缩石头）
  await cmd('kill @e[type=minecraft:item]');
  await cmd('setblock 2 -60 20 minecraft:air');
  await cmd('setblock 2 -60 20 minecraft:air');
  await cmd(`setblock 2 -60 20 ${NS}:2x_stone`);
  await cmd(`setblock 2 -60 20 ${NS}:2x_stone destroy`);
  const drop = await cmd('execute if entity @e[type=minecraft:item,x=2,y=-60,z=20,distance=..5]');
  check('loot drop 2x_stone', /Test passed/.test(drop), drop.trim());
  await cmd('kill @e[type=minecraft:item]');
  await cmd('setblock 2 -60 20 minecraft:air');

  // 3) 全矩阵物品：盔甲架主手换装 + 读 id 断言（594 个：585 工具 + 9 木棍）。
  //    注意：1.21.11 的 /data get 只序列化非默认组件补丁，默认 max_damage 不落 NBT——
  //    组件数值（耐久 9ⁿ / unbreakable / 速度阶梯）已由服务端 SELF-TEST 权威覆盖，
  //    这里验证"物品存在 + 是真 Item + 组件补丁可写"。
  await cmd('kill @e[type=minecraft:armor_stand]');
  const summon = await cmd('summon minecraft:armor_stand 0 -60 4 {Tags:["cbprobe"],Invisible:1b,Marker:1b,NoAI:1b,Invulnerable:1b}');
  check('summon probe stand', !/Unknown|Failed/i.test(summon), summon.trim());
  const stand = '@e[tag=cbprobe,limit=1]';

  // 装备 NBT 路径自适应：1.21.5+ 是 equipment.mainhand，1.21.1 是 HandItems[0]
  await cmd(`item replace entity ${stand} weapon.mainhand with minecraft:stone`);
  let handPath = 'equipment.mainhand';
  if (/Found no elements/.test(await cmd(`data get entity ${stand} ${handPath}.id`))) {
    handPath = 'HandItems[0]';
  }

  for (const item of [...TOOLS_ALL, ...STICKS]) {
    const full = `${NS}:${item}`;
    const rep = await cmd(`item replace entity ${stand} weapon.mainhand with ${full}`);
    check(`item ${item} replace`, !/Unknown|Failed|No item|mismatch/i.test(rep), rep.trim());
    const id = await cmd(`data get entity ${stand} ${handPath}.id`);
    check(`item ${item} in hand`, id.includes(`"${full}"`), id.trim().slice(0, 100));
  }

  // 组件补丁管线抽样：damage 补丁应落进 NBT（证明默认带耐久组件的真物品）
  for (const sample of ['1x_cobblestone_pickaxe', '9x_oak_log_sword', '2x_stick']) {
    const rep = await cmd(`item replace entity ${stand} weapon.mainhand with ${NS}:${sample}[minecraft:damage=1]`);
    const dmg = await cmd(`data get entity ${stand} ${handPath}.components."minecraft:damage"`);
    check(`component patch ${sample}`, /: 1$/.test(dmg.trim()) || /"minecraft:damage": 1/.test(dmg), `${rep.trim()} | ${dmg.trim()}`);
  }
  await cmd('kill @e[tag=cbprobe]');

  // 4) 配方：给机器人不可行，改用配方簿数据侧已由服务器加载校验（日志检查在 bash 侧做），
  //    这里抽验解压链的产物数值语义：用 /execute if 数据不了配方，跳过（日志侧兜底）。

  await cmd('stop');
  rcon.destroy();
  console.log(failures === 0 ? 'E2E ALL PASS' : `E2E FAILURES: ${failures}`);
  process.exit(failures);
})().catch((e) => {
  console.error('E2E ERROR:', e);
  process.exit(99);
});
