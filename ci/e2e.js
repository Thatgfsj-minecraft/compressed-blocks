// 压缩方块 E2E：RCON 服务端权威断言（无需玩家在线：方块用 setblock/assert，
// 物品挂到盔甲架主手再 /data get 读组件；护甲属性用僵尸装备验证）。
// 用法：服务器启动后 `node e2e.js`；结束自动 /stop，退出码 = 失败数。
const { Rcon } = require('./rcon');

// RCON 端口可用环境变量覆盖（多套测试服并行时错开端口）
const PORT = parseInt(process.env.RCON_PORT || '25576', 10);
const NS = 'compressedblocks';

const LEVELS = ['1x', '2x', '3x', '4x', '5x', '6x', '7x', '8x', '9x'];
// 与 gen_resources.py 严格一致的内容清单
const STORAGE_MATS = ['cobblestone', 'stone', 'cobbled_deepslate', 'deepslate',
  'oak_log', 'spruce_log', 'birch_log', 'jungle_log', 'acacia_log', 'dark_oak_log',
  'mangrove_log', 'cherry_log', 'pale_oak_log',
  'dirt', 'sand', 'gravel', 'netherrack', 'end_stone', 'obsidian',
  'coal_block', 'copper_block', 'iron_block', 'lapis_block', 'gold_block',
  'redstone_block', 'emerald_block', 'diamond_block',
  'granite', 'diorite', 'andesite', 'calcite', 'tuff', 'sandstone', 'red_sandstone',
  'basalt', 'blackstone', 'dripstone_block', 'terracotta', 'quartz_block', 'purpur_block',
  'prismarine', 'amethyst_block', 'glowstone', 'clay', 'hay_block', 'bone_block',
  'moss_block', 'snow', 'ice', 'packed_ice', 'mud'];
const CANES = ['cane', 'dirt_cane', 'sand_cane', 'clay_cane', 'cobblestone_cane', 'mineral_cane'];
const WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak'];
const CROPS = ['wheat', 'carrot', 'potato', 'beetroot'];
const CROP_LEVELS = LEVELS.slice(0, 3);
const FOODS = ['bread', 'beef', 'melon', 'rotten_flesh'];
const ARMOR = ['helmet', 'chestplate', 'leggings', 'boots'];
const TOOLS = ['pickaxe', 'axe', 'shovel', 'hoe', 'sword'];
const TOOL_LINES = ['cobblestone', 'wood'];

// 与 CompressedBlocks.java 一致的期望值
const BASE_DUR = { cobblestone: 131, wood: 59 };
const SPEED_LADDER = [6.0, 8.0, 12.0, 18.0, 27.0, 40.5, 60.75, 91.125, 136.6875];
const UNBREAKABLE_FROM = 6;
const ARMOR_BASE = { helmet: 2, chestplate: 6, leggings: 5, boots: 2 };

function durability(level, line) {
  if (level >= UNBREAKABLE_FROM) return 2147483647;
  return BASE_DUR[line] * Math.pow(9, level);
}
function defense(level, piece) {
  return Math.min(ARMOR_BASE[piece] + level - 1, 10);
}

const STORAGE_BLOCKS = STORAGE_MATS.flatMap((m) => LEVELS.map((p) => `${p}_${m}`));
const LEAVES = WOODS.flatMap((w) => LEVELS.map((p) => `${p}_${w}_leaves`));
const SAPLINGS = WOODS.flatMap((w) => LEVELS.map((p) => `${p}_${w}_sapling`));
const FARMLAND = LEVELS.map((p) => `${p}_farmland`);
const CROP_BLOCKS = CROPS.flatMap((c) => CROP_LEVELS.map((p) => `${p}_${c}_plant`));
const CANE_BLOCKS = CANES.flatMap((c) => LEVELS.map((p) => `${p}_${c}`));
const TOOL_IDS = TOOL_LINES.flatMap((l) => LEVELS.flatMap((p) => TOOLS.map((t) => `${p}_${l}_${t}`)));
const STICKS = LEVELS.map((p) => `${p}_stick`);
const ARMOR_IDS = LEVELS.flatMap((p) => ARMOR.map((a) => `${p}_stone_${a}`));
const FOOD_IDS = FOODS.flatMap((f) => CROP_LEVELS.map((p) => `${p}_${f}`));
const PRODUCE = [...CROP_LEVELS.map((p) => `${p}_wheat`), ...CROP_LEVELS.map((p) => `${p}_beetroot`)];
const CROP_ITEMS = ['wheat', 'beetroot'].flatMap((c) => CROP_LEVELS.map((p) => `${p}_${c}_seeds`))
  .concat(['carrot', 'potato'].flatMap((c) => CROP_LEVELS.map((p) => `${p}_${c}`)));
const ALL_ITEMS = [...STORAGE_BLOCKS, ...LEAVES, ...SAPLINGS, ...CANE_BLOCKS, ...CROP_ITEMS,
  ...TOOL_IDS, ...ARMOR_IDS, ...FOOD_IDS, ...PRODUCE, ...STICKS];

let failures = 0;
function check(name, ok, detail) {
  if (!ok) {
    failures++;
    console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`);
  }
}

(async () => {
  if (ALL_ITEMS.length !== 840) throw new Error(`item list ${ALL_ITEMS.length} != 840`);
  const rcon = await new Rcon().connect(PORT, '127.0.0.1', 'testpass');
  const cmd = (c) => rcon.command(c);

  // 0) 探针自检：先证明测试工具自身可靠（超平坦 0 -64 0 必是基岩）
  // 测试区先 forceload，防区块卸载导致实体丢失/方块断言失败
  await cmd('forceload add -48 -48 47 47');
  const probe1 = await cmd('execute if block 0 -64 0 minecraft:bedrock');
  check('probe-self bedrock', /Test passed/.test(probe1), probe1);
  const probe2 = await cmd('execute if block 0 -64 0 minecraft:stone');
  check('probe-self negative', /Test failed/.test(probe2), probe2);

  // 1) 全矩阵方块：放置 + 服务端方块断言
  //    储存方块/树叶/耕地直接放；树苗/作物要放在各自可站立的支撑上
  let slot = 0;
  const pos = (s, dy = 0) => `${2 + (s % 15)} ${-60 + dy} ${2 + Math.floor(s / 15)}`;
  const clear = (s) => cmd(`setblock ${pos(s, 1)} minecraft:air`).then(() =>
    cmd(`setblock ${pos(s)} minecraft:air`));
  for (const b of [...STORAGE_BLOCKS, ...LEAVES, ...FARMLAND]) {
    await cmd(`setblock ${pos(slot)} ${NS}:${b}`);
    const res = await cmd(`execute if block ${pos(slot)} ${NS}:${b}`);
    check(`block ${b}`, /Test passed/.test(res), res.trim());
    await clear(slot);
    slot++;
  }
  for (const s of SAPLINGS) {
    const lvl = s.split('_')[0];
    const dirt = `${lvl}_dirt`;
    await cmd(`setblock ${pos(slot)} ${NS}:${dirt}`);
    await cmd(`setblock ${pos(slot, 1)} ${NS}:${s}`);
    const res = await cmd(`execute if block ${pos(slot, 1)} ${NS}:${s}`);
    check(`block ${s}`, /Test passed/.test(res), res.trim());
    await clear(slot);
    slot++;
  }
  for (const c of CROP_BLOCKS) {
    const lvl = c.split('_')[0];
    await cmd(`setblock ${pos(slot)} ${NS}:${lvl}_farmland`);
    await cmd(`setblock ${pos(slot, 1)} ${NS}:${c}`);
    const res = await cmd(`execute if block ${pos(slot, 1)} ${NS}:${c}`);
    check(`block ${c}`, /Test passed/.test(res), res.trim());
    await clear(slot);
    slot++;
  }
  // 压缩甘蔗：种在同重数压缩泥土上应存活（沙子路线由 place 门与 mayPlaceOn 覆盖，此处验证泥土路线）
  for (const cb of CANE_BLOCKS) {
    const lvl = cb.split('_')[0];
    await cmd(`setblock ${pos(slot)} ${NS}:${lvl}_dirt`);
    await cmd(`setblock ${pos(slot, 1)} ${NS}:${cb}`);
    const res = await cmd(`execute if block ${pos(slot, 1)} ${NS}:${cb}`);
    check(`block ${cb}`, /Test passed/.test(res), res.trim());
    await clear(slot);
    slot++;
  }

  // 2) 作物/树苗等级门：放错等级支撑上，邻居更新后应弹掉
  await cmd(`setblock ${pos(0)} ${NS}:1x_farmland`);
  await cmd(`setblock ${pos(0, 1)} ${NS}:3x_wheat_plant`);
  await cmd(`setblock ${pos(0, 2)} minecraft:stone`);
  await cmd(`setblock ${pos(0, 2)} minecraft:air`);
  await new Promise((r) => setTimeout(r, 300));
  const cropPopped = await cmd(`execute if block ${pos(0, 1)} minecraft:air`);
  check('crop pops on low-level farmland', /Test passed/.test(cropPopped), cropPopped.trim());
  await clear(0);

  await cmd(`setblock ${pos(1)} minecraft:dirt`);
  await cmd(`setblock ${pos(1, 1)} ${NS}:2x_oak_sapling`);
  await cmd(`setblock ${pos(1, 2)} minecraft:stone`);
  await cmd(`setblock ${pos(1, 2)} minecraft:air`);
  await new Promise((r) => setTimeout(r, 300));
  const sapPopped = await cmd(`execute if block ${pos(1, 1)} minecraft:air`);
  check('sapling pops on vanilla dirt', /Test passed/.test(sapPopped), sapPopped.trim());
  await clear(1);
  // 同级支撑应存活（放置后伴随方块更新仍在）
  await cmd(`setblock ${pos(2)} ${NS}:2x_dirt`);
  await cmd(`setblock ${pos(2, 1)} ${NS}:2x_oak_sapling`);
  await cmd(`setblock ${pos(2, 2)} minecraft:stone`);
  await cmd(`setblock ${pos(2, 2)} minecraft:air`);
  await new Promise((r) => setTimeout(r, 300));
  const sapStay = await cmd(`execute if block ${pos(2, 1)} ${NS}:2x_oak_sapling`);
  check('sapling survives on matching dirt', /Test passed/.test(sapStay), sapStay.trim());
  await clear(2);

  // 3) 战利品表：压缩方块掉自身；耕地掉对应等级压缩泥土
  await cmd('kill @e[type=minecraft:item]');
  await cmd(`setblock 2 -60 20 ${NS}:2x_stone`);
  await cmd(`setblock 2 -60 20 ${NS}:2x_stone destroy`);
  const drop = await cmd('execute if entity @e[type=minecraft:item,x=2,y=-60,z=20,distance=..5]');
  check('loot drop 2x_stone', /Test passed/.test(drop), drop.trim());
  const dropId = await cmd('data get entity @e[type=minecraft:item,limit=1,sort=nearest] Item.id');
  check('loot id 2x_stone', dropId.includes('2x_stone'), dropId.trim());
  await cmd('kill @e[type=minecraft:item]');
  await cmd(`setblock 2 -60 20 ${NS}:1x_farmland`);
  await cmd(`setblock 2 -60 20 ${NS}:1x_farmland destroy`);
  const farmDrop = await cmd('data get entity @e[type=minecraft:item,limit=1,sort=nearest] Item.id');
  check('farmland drops 1x_dirt', farmDrop.includes('1x_dirt'), farmDrop.trim());
  await cmd('kill @e[type=minecraft:item]');
  await cmd('setblock 2 -60 20 minecraft:air');

  // 4) 全矩阵物品：盔甲架主手换装 + 读 id 断言（711 个）。
  //    组件数值（耐久 9ⁿ / unbreakable / 速度阶梯 / 护甲防御 / 食物数值）已由服务端
  //    SELF-TEST 权威覆盖，这里验证"物品存在 + 可装备 + 组件补丁可写"。
  await cmd('kill @e[type=minecraft:armor_stand]');
  const summon = await cmd('summon minecraft:armor_stand 0 -60 4 {Tags:["cbprobe"],Invisible:1b,Marker:1b,NoAI:1b,Invulnerable:1b}');
  check('summon probe stand', !/Unknown|Failed/i.test(summon), summon.trim());
  const stand = '@e[tag=cbprobe,limit=1]';
  await cmd(`item replace entity ${stand} weapon.mainhand with minecraft:stone`);
  let handPath = 'equipment.mainhand';
  if (/Found no elements/.test(await cmd(`data get entity ${stand} ${handPath}.id`))) {
    handPath = 'HandItems[0]';
  }
  for (const item of ALL_ITEMS) {
    const full = `${NS}:${item}`;
    const rep = await cmd(`item replace entity ${stand} weapon.mainhand with ${full}`);
    check(`item ${item} replace`, !/Unknown|Failed|No item|mismatch/i.test(rep), rep.trim());
    const id = await cmd(`data get entity ${stand} ${handPath}.id`);
    check(`item ${item} in hand`, id.includes(`"${full}"`), id.trim().slice(0, 100));
  }
  // 组件补丁管线抽样
  for (const sample of ['1x_cobblestone_pickaxe', '9x_wood_sword', '2x_stick']) {
    const rep = await cmd(`item replace entity ${stand} weapon.mainhand with ${NS}:${sample}[minecraft:damage=1]`);
    const dmg = await cmd(`data get entity ${stand} ${handPath}.components."minecraft:damage"`);
    check(`component patch ${sample}`, /: 1$/.test(dmg.trim()) || /"minecraft:damage": 1/.test(dmg), `${rep.trim()} | ${dmg.trim()}`);
  }
  await cmd('kill @e[tag=cbprobe]');

  // 5) 护甲属性：僵尸（基础 2.0）穿九重胸甲（+10）→ 实时 armor 属性 = 12。
  //    装备属性修饰符是瞬态的，不落 NBT，必须用 /attribute get 读实时值。
  await cmd('kill @e[type=minecraft:zombie]');
  const zsum = await cmd('summon minecraft:zombie 0 -60 6 {Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:["cbz"]}');
  check('summon zombie probe', !/Unknown|Failed/i.test(zsum), zsum.trim());
  const equip = await cmd(`item replace entity @e[tag=cbz,limit=1] armor.chest with ${NS}:9x_stone_chestplate`);
  check('zombie equip 9x chestplate', !/Unknown|Failed|No item/i.test(equip), equip.trim());
  await new Promise((r) => setTimeout(r, 600));
  // 无人暂停（pause-when-empty）会让装备属性永不结算，强推 30 tick 保证结算
  await cmd('tick sprint 30t');
  const armorVal = await cmd('attribute @e[tag=cbz,limit=1] minecraft:armor get');
  check('9x chestplate armor = 2+10', /is 12(\.0)?($|\s|,)/.test(armorVal.trim()), armorVal.trim());
  await cmd('kill @e[tag=cbz]');

  await cmd('stop');
  rcon.destroy();
  console.log(failures === 0 ? 'E2E ALL PASS' : `E2E FAILURES: ${failures}`);
  process.exit(failures);
})().catch((e) => {
  console.error('E2E ERROR:', e);
  process.exit(99);
});
