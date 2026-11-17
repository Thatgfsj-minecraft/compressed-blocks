// 压缩方块 E2E：RCON 服务端权威断言（无需玩家在线：方块用 setblock/assert，
// 物品挂到盔甲架主手后用 /data get 读 id；默认数据组件不落实体物品 NBT，改用
// /execute if items ... *[组件=值] 逐档对拍工具挖掘速度/耐久/不可破坏与护甲最大耐久/防御属性；
// 护甲实际防御再用僵尸 /attribute get 复核）。
// 覆盖：方块矩阵/等级门/战利品/熔炉挖掘门/全物品清单（1561）/组件阶梯/刷石机兜底。
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
  'moss_block', 'snow', 'blue_ice', 'mud', 'gunpowder'];
// 甘蔗 = 纯甘蔗 + 每种储存材料一条线（id 材料段去掉 _block 后缀）
const CANES = ['cane', ...STORAGE_MATS.map((m) => m.replace(/_block$/, '') + '_cane')];
const POTS = ['pot'];
const GENERATORS = ['cobblestone_generator'];
const COMPAT = ['tin', 'lead', 'zinc', 'plastic', 'silver', 'nickel', 'bronze', 'brass', 'electrum',
  'invar', 'constantan', 'steel', 'manasteel', 'uranium', 'osmium', 'signalum', 'enderium',
  'refined_obsidian', 'refined_glowstone', 'lumium', 'terrasteel', 'elementium'];
const COMPAT_BLOCKS = COMPAT.flatMap((k) => LEVELS.map((p) => `${p}_${k}_block`));
const WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak'];
const CROPS = ['wheat', 'carrot', 'potato', 'beetroot'];
const CROP_LEVELS = LEVELS.slice(0, 3);
const FOODS = ['bread', 'beef', 'cooked_beef', 'porkchop', 'cooked_porkchop', 'mutton',
  'cooked_mutton', 'chicken', 'cooked_chicken', 'rabbit', 'cooked_rabbit', 'cod',
  'cooked_cod', 'salmon', 'cooked_salmon', 'melon', 'rotten_flesh', 'baked_potato', 'apple'];
const ARMOR = ['helmet', 'chestplate', 'leggings', 'boots'];
const TOOLS = ['pickaxe', 'axe', 'shovel', 'hoe', 'sword'];
const TOOL_LINES = ['cobblestone', 'wood'];

// 与 CompressedBlocks.java 一致的期望值（本节常量全部在 4b 的 RCON 组件断言里使用）
const BASE_DUR = { cobblestone: 131, wood: 59 };
// 挖掘速度阶梯按线分开：对挖掘类工具的 minecraft:tool 组件逐档对拍（剑不参与该阶梯）
const SPEED_LADDER = {
  // 石线：一重 6.0（铁）、二重 8.0（钻）、三重 12.0（金），四重起 ×1.5 逐级连乘
  cobblestone: [6.0, 8.0, 12.0, 18.0, 27.0, 40.5, 60.75, 91.125, 136.6875],
  // 木线 = 石线 × 0.95（木线整体定位在石线之下）
  wood: [5.7, 7.6, 11.4, 17.1, 25.65, 38.475, 57.7125, 86.56875, 129.853125],
};
const UNBREAKABLE_FROM = 6;
const ARMOR_BASE = { helmet: 2, chestplate: 6, leggings: 5, boots: 2 };
// 护甲耐久基准（CompressedBlocks.ARMOR_DURABILITY_BASE），六重起用 Integer.MAX_VALUE/16 作乘数
const ARMOR_DUR_BASE = { helmet: 11, chestplate: 16, leggings: 15, boots: 13 };
const ARMOR_SLOT = { helmet: 'head', chestplate: 'chest', leggings: 'legs', boots: 'feet' };
// 挖掘工具 → 方块标签；剑的 tool 组件是另一套（无速度阶梯），只对拍耐久
const TOOL_TAGS = {
  pickaxe: 'minecraft:mineable/pickaxe',
  axe: 'minecraft:mineable/axe',
  shovel: 'minecraft:mineable/shovel',
  hoe: 'minecraft:mineable/hoe',
};

function durability(level, line) {
  if (level >= UNBREAKABLE_FROM) return 2147483647;
  return BASE_DUR[line] * Math.pow(9, level);
}
function armorDurability(level, piece) {
  const mult = level >= UNBREAKABLE_FROM ? Math.floor(2147483647 / 16) : Math.pow(9, level);
  return ARMOR_DUR_BASE[piece] * mult;
}
function defense(level, piece) {
  return Math.min(ARMOR_BASE[piece] + level - 1, 10);
}
// 石线 1~6 重原版石级、7 铁、8 钻、9 下界合金；木线整体低一级
function incorrectToolTag(line, level) {
  if (line === 'cobblestone') {
    if (level >= 9) return 'minecraft:incorrect_for_netherite_tool';
    if (level === 8) return 'minecraft:incorrect_for_diamond_tool';
    if (level === 7) return 'minecraft:incorrect_for_iron_tool';
    return 'minecraft:incorrect_for_stone_tool';
  }
  if (level >= 9) return 'minecraft:incorrect_for_diamond_tool';
  if (level === 8) return 'minecraft:incorrect_for_iron_tool';
  if (level === 7) return 'minecraft:incorrect_for_stone_tool';
  return 'minecraft:incorrect_for_wooden_tool';
}
// ToolMaterial.applyToolProperties 的完整 tool 组件：规则 1 拒绝错误等级掉落，规则 2 给速度
function toolComponent(line, level, kind) {
  const speed = SPEED_LADDER[line][level - 1].toFixed(6);
  return `{rules:[{blocks:"#${incorrectToolTag(line, level)}",correct_for_drops:false},`
    + `{blocks:"#${TOOL_TAGS[kind]}",speed:${speed}f,correct_for_drops:true}],`
    + 'default_mining_speed:1.0f,damage_per_block:1,can_destroy_blocks_in_creative:true}';
}
// ArmorMaterial.createAttributes 的完整 attribute_modifiers：armor + armor_toughness
function armorModifiers(piece, level) {
  const slot = ARMOR_SLOT[piece];
  const id = `minecraft:armor.${piece}`;
  const amount = defense(level, piece);
  return `[{type:"minecraft:armor",id:"${id}",amount:${amount}.0,operation:"add_value",slot:"${slot}"},`
    + `{type:"minecraft:armor_toughness",id:"${id}",amount:0.0,operation:"add_value",slot:"${slot}"}]`;
}
// 统计 data get 文本中某物品 id 出现的槽位数（引号感知，避免子串误判）
function stackCount(nbt, id) {
  return (nbt.match(new RegExp(`id:\\s*"${id}"`, 'g')) || []).length;
}

const STORAGE_BLOCKS = STORAGE_MATS.flatMap((m) => LEVELS.map((p) => `${p}_${m}`));
const LEAVES = WOODS.flatMap((w) => LEVELS.map((p) => `${p}_${w}_leaves`));
const SAPLINGS = WOODS.flatMap((w) => LEVELS.map((p) => `${p}_${w}_sapling`));
const FARMLAND = LEVELS.map((p) => `${p}_farmland`);
const CROP_BLOCKS = CROPS.flatMap((c) => CROP_LEVELS.map((p) => `${p}_${c}_plant`));
const CANE_BLOCKS = CANES.flatMap((c) => LEVELS.map((p) => `${p}_${c}`));
const TOOL_IDS = TOOL_LINES.flatMap((l) => LEVELS.flatMap((p) => TOOLS.map((t) => `${p}_${l}_${t}`)));
const STICKS = LEVELS.map((p) => `${p}_stick`);
const ARMOR_IDS = LEVELS.flatMap((p) => ARMOR.flatMap((a) => [`${p}_stone_${a}`, `${p}_wood_${a}`]));
const FOOD_IDS = FOODS.flatMap((f) => CROP_LEVELS.map((p) => `${p}_${f}`));
const PRODUCE = [...CROP_LEVELS.map((p) => `${p}_wheat`), ...CROP_LEVELS.map((p) => `${p}_beetroot`)];
const EXTRA_SEEDS = ['pumpkin_seeds', 'melon_seeds', 'torchflower_seeds', 'pitcher_pod'];
const EXTRA_SEED_ITEMS = EXTRA_SEEDS.flatMap((k) => CROP_LEVELS.map((p) => `${p}_${k}`));
const CROP_ITEMS = ['wheat', 'beetroot'].flatMap((c) => CROP_LEVELS.map((p) => `${p}_${c}_seeds`))
  .concat(['carrot', 'potato'].flatMap((c) => CROP_LEVELS.map((p) => `${p}_${c}`)));
const ALL_ITEMS = [...STORAGE_BLOCKS, ...LEAVES, ...SAPLINGS, ...CANE_BLOCKS, ...CROP_ITEMS,
  ...POTS.flatMap((p) => [p]), ...GENERATORS, ...COMPAT_BLOCKS, ...TOOL_IDS, ...ARMOR_IDS, ...FOOD_IDS, ...PRODUCE,
  ...STICKS, ...EXTRA_SEED_ITEMS, 'compressed_chest', 'compressed_shulker_box', 'hopper_pot',
  // 4 压缩熔炉 + 3 压缩漏斗 + 基础木漏斗/3 压缩木漏斗（此前漏登记，与 SELF-TEST 的 1561 对不上）
  ...LEVELS.slice(0, 4).map((p) => `${p}_compressed_furnace`),
  ...LEVELS.slice(0, 3).map((p) => `${p}_compressed_hopper`),
  'wooden_hopper', ...LEVELS.slice(0, 3).map((p) => `${p}_compressed_wooden_hopper`)];

let failures = 0;
function check(name, ok, detail) {
  if (!ok) {
    failures++;
    console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`);
  }
}

(async () => {
  // 与 CompressedSelfTest 的 1561 条注册项一致；漏项会让下面的逐条换装断言直接少测
  if (ALL_ITEMS.length !== 1561) throw new Error(`item list ${ALL_ITEMS.length} != 1561`);
  const rcon = await new Rcon().connect(PORT, '127.0.0.1', 'testpass');
  const cmd = (c) => rcon.command(c);

  // 0) 探针自检：先证明测试工具自身可靠（超平坦 0 -64 0 必是基岩）
  // 测试区先 forceload，防区块卸载导致实体丢失/方块断言失败
  // （注意：空服无玩家时边界加载区块不推进方块实体 tick，BE/随机刻断言不在本脚本覆盖）
  await cmd('forceload add -160 -160 47 47');
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
  // 压缩甘蔗：专用条带（泥土 + 相邻水源，模拟真实农田），种在同重数压缩泥土上应存活
  for (let i = 0; i < CANE_BLOCKS.length; i++) {
    const cb = CANE_BLOCKS[i];
    const lvl = cb.split('_')[0];
    const x = 2 + (i % 12), z = -40 - Math.floor(i / 12) * 3;
    await cmd(`setblock ${x} -60 ${z + 1} minecraft:water`);
    await cmd(`setblock ${x} -60 ${z} ${NS}:${lvl}_dirt`);
    await cmd(`setblock ${x} -59 ${z} ${NS}:${cb}`);
    const res = await cmd(`execute if block ${x} -59 ${z} ${NS}:${cb}`);
    check(`block ${cb}`, /Test passed/.test(res), res.trim());
    await cmd(`setblock ${x} -59 ${z} minecraft:air`);
    await cmd(`setblock ${x} -60 ${z} minecraft:air`);
    await cmd(`setblock ${x} -60 ${z + 1} minecraft:air`);
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

  // 3b) 压缩熔炉挖掘门（issue #36）：与原版 minecraft:furnace 一致 —— 任意等级镐（含木镐）都能挖掉
  //     并掉落本体，因此只能出现在 #mineable/pickaxe，绝不能进 needs_stone/iron/diamond_tool。
  //     注意：不能拿 /loot ... mine <tool> 判等级门 —— 该命令只做战利品表求值，
  //     不校验"正确工具"（requiresCorrectToolForDrops 是 playerDestroy 路径的判定），
  //     原版铁矿空手 /loot 也一样掉 Raw Iron。等级门只能用方块标签断言。
  for (const tier of ['1x', '2x', '3x', '4x']) {
    const furnaceId = `${tier}_compressed_furnace`;
    await cmd(`setblock 2 -60 20 ${NS}:${furnaceId}`);
    const inPickaxe = await cmd('execute if block 2 -60 20 #minecraft:mineable/pickaxe');
    check(`furnace ${furnaceId} in #mineable/pickaxe`, /Test passed/.test(inPickaxe), inPickaxe.trim());
    for (const tag of ['needs_stone_tool', 'needs_iron_tool', 'needs_diamond_tool']) {
      const gated = await cmd(`execute if block 2 -60 20 #minecraft:${tag}`);
      check(`furnace ${furnaceId} not in #${tag}`, /Test failed/.test(gated), gated.trim());
    }
  }
  await cmd('setblock 2 -60 20 minecraft:air');
  // 战利品表冒烟：压缩熔炉破坏必掉本体（与原版 3.5 硬度、无 noLootTable 一致）
  await cmd('kill @e[type=minecraft:item]');
  await cmd(`setblock 2 -60 20 ${NS}:1x_compressed_furnace`);
  await cmd('setblock 2 -60 20 minecraft:air destroy');
  const furnaceDrop = await cmd(
    'data get entity @e[type=minecraft:item,x=2,y=-59,z=20,distance=..3,limit=1,sort=nearest] Item.id');
  check('furnace break drops itself',
    furnaceDrop.includes(`"${NS}:1x_compressed_furnace"`), furnaceDrop.trim());
  await cmd('kill @e[type=minecraft:item]');

  // 4) 全矩阵物品：盔甲架主手换装 + 读 id 断言（全部 1561 个物品）。
  //    这里验证"物品存在 + 可装备 + 组件补丁可写"；默认组件数值在 4b 逐档对拍。
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
  // 4b) 数据组件逐档对拍：实体物品 NBT 只保存与物品默认值的差异，
  //     /data get 读不到 minecraft:tool / max_damage / attribute_modifiers 等默认组件，
  //     改用 /execute if items ... *[组件=值] 让服务端按物品定义解析后做权威断言。
  const inHand = `entity ${stand} weapon.mainhand`;
  const hasComponent = async (predicate) => /Test passed/.test(
    await cmd(`execute if items ${inHand} *[${predicate}]`));
  const equipItem = async (name) => cmd(`item replace entity ${stand} weapon.mainhand with ${NS}:${name}`);

  // 工具：耐久 9ⁿ×基础（六重起不可破坏）；挖掘类工具再对拍速度阶梯与挖掘等级标签
  for (const line of TOOL_LINES) {
    for (let level = 1; level <= LEVELS.length; level++) {
      const prefix = LEVELS[level - 1];
      const dur = durability(level, line);
      for (const kind of TOOLS) {
        const name = `${prefix}_${line}_${kind}`;
        await equipItem(name);
        check(`tool ${name} max_damage=${dur}`, await hasComponent(`minecraft:max_damage=${dur}`));
        if (TOOL_TAGS[kind]) {
          check(`tool ${name} speed=${SPEED_LADDER[line][level - 1]}`,
            await hasComponent(`minecraft:tool=${toolComponent(line, level, kind)}`));
        }
        if (level >= UNBREAKABLE_FROM) {
          check(`tool ${name} unbreakable`, await hasComponent('minecraft:unbreakable={}'));
        }
      }
    }
  }

  // 护甲：max_damage = 铁级基准 × 9ⁿ（六重起不可破坏）；attribute_modifiers 的 armor 值逐档对拍
  for (const line of ['stone', 'wood']) {
    for (let level = 1; level <= LEVELS.length; level++) {
      const prefix = LEVELS[level - 1];
      for (const piece of ARMOR) {
        const name = `${prefix}_${line}_${piece}`;
        const dur = armorDurability(level, piece);
        await equipItem(name);
        check(`armor ${name} max_damage=${dur}`, await hasComponent(`minecraft:max_damage=${dur}`));
        check(`armor ${name} armor=${defense(level, piece)}`,
          await hasComponent(`minecraft:attribute_modifiers=${armorModifiers(piece, level)}`));
        if (level >= UNBREAKABLE_FROM) {
          check(`armor ${name} unbreakable`, await hasComponent('minecraft:unbreakable={}'));
        }
      }
    }
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

  // 6) 盆栽：放置 + BE NBT 读写回路 + 破坏掉落（preRemoveSideEffects 路径）。
  //    生长→自动收割→补种闭环不在本脚本覆盖：空服无玩家时方块实体不 tick、
  //    RCON tick sprint 驱不动 BE，SELF-TEST 也没有 serverTick 覆盖。
  await cmd(`setblock 4 -59 20 ${NS}:pot`);
  const potMerge = await cmd(
    `data merge block 4 -59 20 {Soil:"${NS}:1x_dirt",Plant:"${NS}:1x_cane",Seed:"${NS}:1x_cane",Growth:5}`);
  check('pot BE merge', !/Unknown|Failed|Could not/i.test(potMerge), potMerge.trim());
  const potGrowth = await cmd('data get block 4 -59 20 Growth');
  // 断言 Growth 可写且落在合法区间（未 tick 时保持写入值 5；tick 时可能已递增）
  const g = parseInt((potGrowth.match(/block data: (\d+)/) || [])[1] ?? '-1', 10);
  check('pot BE growth round-trip', g >= 5 && g <= 600, potGrowth.trim());
  const potPlant = await cmd('data get block 4 -59 20 Plant');
  check('pot BE plant round-trip', /1x_cane/.test(potPlant.trim()), potPlant.trim());
  // 破坏带土带苗的盆栽：应掉落盆本体 + 土壤 + 作物（preRemoveSideEffects 移除路径）
  const merge2 = await cmd(`data merge block 4 -59 20 {Soil:"${NS}:1x_dirt",Plant:"${NS}:1x_cane",Seed:"${NS}:1x_cane"}`);
  check('pot BE re-merge', !/Unknown|Failed|Could not/i.test(merge2), merge2.trim());
  await cmd('kill @e[type=minecraft:item]');
  const broke = await cmd(`setblock 4 -59 20 minecraft:air destroy`);
  check('pot break executes', !/Unknown|Failed|Could not/i.test(broke), broke.trim());
  await new Promise((r) => setTimeout(r, 400));
  async function expectDrop(id) {
    const res = await cmd(
      `execute if entity @e[type=minecraft:item,x=4,y=-59,z=20,distance=..4,nbt={Item:{id:"${NS}:${id}"}}]`);
    return /Test passed/.test(res);
  }
  check('pot break drops soil', await expectDrop('1x_dirt'));
  check('pot break drops plant', await expectDrop('1x_cane'));
  check('pot break drops pot', await expectDrop('pot'));
  await cmd('kill @e[type=minecraft:item]');
  // 7) 刷石机防御式输出（issue #39）：缓存校验/方向回退/addItem 语义/兜底掉落/红石锁
  const gpos = '10 -59 30', hpos = '10 -60 30', upos = '10 -58 30';
  const genBuf = (nbt) => {
    const bc = nbt.match(/BufferCount: (\d+)/);
    if (bc) return parseInt(bc[1], 10);
    const m = nbt.match(/Buffer: \{[^}]*count: (\d+)/);
    return m ? parseInt(m[1], 10) : 0;
  };
  const resetGenArea = async () => {
    await cmd('kill @e[type=minecraft:item]');
    await cmd('setblock 11 -60 30 minecraft:air');
    await cmd(`setblock ${upos} minecraft:air`);
    await cmd(`setblock ${gpos} minecraft:air`);
    await cmd(`setblock ${hpos} minecraft:air`);
    await cmd('setblock 10 -61 30 minecraft:grass_block');
  };
  const genItems = async () => (await cmd(`data get block ${hpos} Items`)).trim();
  const genTags = async () => (await cmd(`data get block ${gpos}`)).trim();

  // 7a) 木质漏斗（用户场景）应能接住产物
  await resetGenArea();
  await cmd(`setblock ${hpos} ${NS}:wooden_hopper`);
  await cmd(`setblock ${gpos} ${NS}:cobblestone_generator`);
  await cmd('tick sprint 40t');
  await new Promise((r) => setTimeout(r, 1600));
  const genHop = await genItems();
  check('generator pushes into wooden_hopper', /minecraft:cobblestone/.test(genHop) && !/Items: \[\]/.test(genHop), genHop);

  // 7b) 下方木漏斗被异种物品占住 + 上方空木漏斗 → 回退到上方方向
  await resetGenArea();
  await cmd(`setblock ${hpos} ${NS}:wooden_hopper`);
  await cmd(`data merge block ${hpos} {Items:[{Slot:0b,id:"minecraft:dirt",count:1}]}`);
  await cmd(`setblock ${upos} ${NS}:wooden_hopper`);
  await cmd(`setblock ${gpos} ${NS}:cobblestone_generator`);
  await cmd('tick sprint 40t');
  await new Promise((r) => setTimeout(r, 1600));
  const genUp = await cmd(`data get block ${upos} Items`);
  const genDown = await genItems();
  // 下行仍必须是原 dirt（引号感知计数）：不能只看 "dirt," 子串，NBT 里是 id: "minecraft:dirt"
  check('generator falls back to other direction',
    /minecraft:cobblestone/.test(genUp) && stackCount(genDown, 'minecraft:cobblestone') === 0
      && stackCount(genDown, 'minecraft:dirt') === 1,
    `up=${genUp.trim()} | down=${genDown}`);

  // 7c) 红石锁死（相邻红石块 → enabled=false）的漏斗不接收，产物留在缓冲
  await resetGenArea();
  await cmd(`setblock ${hpos} ${NS}:wooden_hopper`);
  await cmd('setblock 11 -60 30 minecraft:redstone_block');
  await cmd(`setblock ${gpos} ${NS}:cobblestone_generator`);
  await cmd('tick sprint 40t');
  await new Promise((r) => setTimeout(r, 1400));
  const lockedItems = await genItems();
  const lockedBuf = genBuf(await genTags());
  check('redstone-locked hopper receives nothing', /Items: \[\]/.test(lockedItems) || lockedItems.endsWith('[]'), lockedItems);
  check('redstone-locked hopper leaves buffer', lockedBuf > 0, `buffer=${lockedBuf}`);

  // 7d) 目标被移除/替换为非容器后自动重扫；放回漏斗继续进物
  await resetGenArea();
  await cmd(`setblock ${hpos} ${NS}:wooden_hopper`);
  await cmd(`setblock ${gpos} ${NS}:cobblestone_generator`);
  await cmd('tick sprint 40t');
  await new Promise((r) => setTimeout(r, 1400));
  const rescanBefore = await genItems();
  await cmd(`setblock ${hpos} minecraft:stone`);
  await cmd('tick sprint 30t');
  await new Promise((r) => setTimeout(r, 900));
  await cmd(`setblock ${hpos} ${NS}:wooden_hopper`);
  await cmd('tick sprint 40t');
  await new Promise((r) => setTimeout(r, 1400));
  const rescanAfter = await genItems();
  check('generator rescans after target replaced', /minecraft:cobblestone/.test(rescanAfter), `before=${rescanBefore} after=${rescanAfter}`);

  // 7e) 全方向堵死 + 缓冲达上限 → 产物以掉落物弹到机器上方
  await resetGenArea();
  await cmd(`setblock ${hpos} ${NS}:wooden_hopper`);
  await cmd(`data merge block ${hpos} {Items:[{Slot:0b,id:"minecraft:cobblestone",count:64}]}`);
  await cmd(`setblock ${gpos} ${NS}:cobblestone_generator`);
  await cmd(`data merge block ${gpos} {Buffer:{id:"minecraft:cobblestone",count:64},Progress:19}`);
  await cmd('tick sprint 10t');
  await new Promise((r) => setTimeout(r, 600));
  const overflowEntity = '@e[type=minecraft:item,x=10,y=-59,z=30,distance=..5,limit=1]';
  const overflowDropId = await cmd(`data get entity ${overflowEntity} Item.id`);
  check('generator drops overflow above when full', /minecraft:cobblestone/.test(overflowDropId), overflowDropId.trim());
  const overflowCount = await cmd(`data get entity ${overflowEntity} Item.count`);
  check('generator overflow drop keeps count', /: 64$/.test(overflowCount.trim()), overflowCount.trim());
  const overflowBuf = await cmd(`data get block ${gpos}`);
  const overflowBufCount = genBuf(overflowBuf);
  check('generator overflow drop empties buffer', overflowBufCount < 8, `buffer=${overflowBufCount}`);
  await resetGenArea();

  // /stop 后服务端会立刻关闭 RCON 连接，响应可能在 socket close 前读不回来；若直接
  // await 该命令，socket 关闭后事件循环变空会让 Node 以 0 退出，把 failures 吞掉。
  await Promise.race([
    cmd('stop').catch(() => {}),
    new Promise((r) => setTimeout(r, 1000)),
  ]);
  rcon.destroy();
  console.log(failures === 0 ? 'E2E ALL PASS' : `E2E FAILURES: ${failures}`);
  process.exit(failures);
})().catch((e) => {
  console.error('E2E ERROR:', e);
  process.exit(99);
});
