// 压缩方块 0.4.1 附属 mod（compressedblockspot）E2E：RCON 服务端权威断言 + 本地资源/版本断言。
//
// 【服务端前置】测试服 mods/ 必须同时放入：
//   1) 主 mod：compressedblocks-fabric-1.21.11-0.4.1.jar
//   2) 附属 mod：compressedblockspot-fabric-1.21.11-0.4.1.jar
//   3) Fabric API（如 fabric-api-0.141.6+1.21.11.jar）
//   4) Carpet（可选但建议）：用例 D 需要在线玩家才能执行 `recipe give <targets> <recipe>`
//      —— RCON 控制台不是玩家，@s / @a 会先报 "No player was found"。装了 Carpet 时脚本用
//      `/player <bot> spawn` 造一个假人；没有 Carpet 时自动降级为「直接读 recipe JSON」（SKIP，不计失败）。
//   服务端需 enable-rcon=true、rcon.password=testpass（端口用 RCON_PORT 覆盖）；
//   建议 level-type=minecraft:flat（脚本在 y=-64 做基岩探针），并允许 forceload。
//   版本必须成对：主 mod 与附属 mod 同为 0.4.1（附属依赖 compressedblocks >=0.4.1 <0.5.0）。
//
// 【用法】
//   node ci/e2e-addon.js                  # 连 127.0.0.1:25576（RCON_PORT / RCON_HOST / RCON_PASS 可覆盖）
//   RCON_PORT=25577 node ci/e2e-addon.js  # 多套测试服并行时错开端口
//   node ci/e2e-addon.js --files-only     # 不连服务器，只跑 D-file / E / F（PR 早期自检；E2E_FILES_ONLY=1 同效）
//   RECIPE_MODE=live|file|auto            # 用例 D 的在线断言策略，默认 auto
//   REPO_ROOT=/path/to/worktree         # 指定要断言的仓库根（默认脚本所在仓库），可对其它 worktree 做交叉校验
//   结束自动 /stop（Promise.race 防 RCON socket 关闭吞退出码），退出码 = 失败数。
//
// 【用例】
//   A 9 档方块：setblock + execute if block + #minecraft:mineable/pickaxe（且不属于 needs_* 工具标签）
//   B 9 档产物：机器 (x,-59,z) + 正下方箱子 (x,-60,z)；tick sprint 40t（40 tick = 2 个产物）
//     → 每箱恰好 2 个 compressedblocks:Nx_cobblestone，且箱内无其它物品（防隔壁方块/容器串味）
//   C 原版级：compressedblocks:cobblestone_generator 同样 40t → 箱内恰好 2 个 minecraft:cobblestone
//   D 配方：live = recipe give/take 假人看返回文本；file = 读 addon recipe JSON（产物 id/数量/原料）
//   E 版本：主/附 4 个 build.gradle 解析到 0.4.1；附属 fabric.mod.json 依赖 ">=0.4.1 <0.5.0"、
//     neoforge.mods.toml 依赖 "[0.4.1,0.5.0)"；主/附版本必须相同
//   F 附属资源：9 档 blockstate/items/模型（parent 链与贴图引用可解析）/战利品表/双语名/
//     创造栏 lang/挖掘标签文件；live 额外用 setblock ... destroy 断言每档战利品掉自身
//
// 【时间确定性】generator 用例前 `tick freeze`，`tick sprint 40t` 精确跑 40 tick，最后 `tick unfreeze`；
// 这样即使测试服没进入空服暂停（或有人在线）也不会多产，避免「恰好 2 个」抖动。
//
// 【待确认假设】recipe id 与资源路径集中在下面常量，以 PR-2 最终实现为准：
//   合成 compressedblockspot:Nx_cobblestone_generator（1x = 9 个主 mod 原版级刷石机
//     compressedblocks:cobblestone_generator；N≥2 = 9 个 (N-1)x 密刷石机）
//   拆解 compressedblockspot:unpack_Nx_cobblestone_generator（→ 9 个主 mod 原版级刷石机 / 9 个 (N-1)x）
//   以上 id 与 1x 配方原料已按 PR-2 分支实际生成物校准（2026-11-18）。
const fs = require('fs');
const path = require('path');
const { Rcon } = require('./rcon');

// 仓库根默认按脚本位置解析（ci/ 的上一级），任意 cwd 都能跑；REPO_ROOT 可指向其它 worktree
const ROOT = path.resolve(process.env.REPO_ROOT || path.resolve(__dirname, '..'));

const PORT = parseInt(process.env.RCON_PORT || '25576', 10);
const HOST = process.env.RCON_HOST || '127.0.0.1';
const PASS = process.env.RCON_PASS || 'testpass';
const FILES_ONLY = process.argv.includes('--files-only') || process.env.E2E_FILES_ONLY === '1';
const RECIPE_MODE = (process.env.RECIPE_MODE || 'auto').toLowerCase();

// 命名空间：附属 compressedblockspot，主 mod compressedblocks
const MAIN_NS = 'compressedblocks';
const ADDON_NS = 'compressedblockspot';
const VERSION = '0.4.1';
const DEP_FABRIC = '>=0.4.1 <0.5.0';
const DEP_NEOFORGE = '[0.4.1,0.5.0)';
const LEVELS = [1, 2, 3, 4, 5, 6, 7, 8, 9];
const GEN = 'cobblestone_generator';
const addonGen = (n) => `${ADDON_NS}:${n}x_${GEN}`;
const productId = (n) => `${MAIN_NS}:${n}x_cobblestone`;
// 合成配方 id 与方块 id 相同（沿用主 mod 的 1x_xxx / unpack_1x_xxx 命名约定）
const recipeCraft = (n) => addonGen(n);
const recipeUnpack = (n) => `${ADDON_NS}:unpack_${n}x_${GEN}`;

// 测试区：与 ci/e2e.js 的坐标（x/z 2..47、z -40 等）完全错开，避免连续/并行跑互相污染
const AREA = {
  x0: 200,
  y: -59,
  z: 200,
  step: 3,
  baseX: 200 + LEVELS.length * 3,
  lootY: -55,
  lootZ: 210,
  bot: 'qaaddon_recipe',
  botPos: '200 -60 220',
};
const FORCELOAD = '192 192 240 240'; // forceload add <x1> <z1> <x2> <z2>（方块坐标，含端点）

// 附属资源同时生成到 Fabric / NeoForge 两个子项目（scripts/gen_addon_assets.py 的 SUBS）
const ADDON_RES = [
  path.join(ROOT, '1.21.11', 'addon', 'fabric', 'src', 'main', 'resources'),
  path.join(ROOT, '1.21.11', 'addon', 'neoforge', 'src', 'main', 'resources'),
];

let failures = 0;
let checks = 0;
let skipped = 0;

function check(name, ok, detail) {
  checks++;
  if (!ok) {
    failures++;
    console.log(`FAIL ${name}${detail ? ' :: ' + String(detail).trim().slice(0, 300) : ''}`);
  }
}
function skip(name, reason) {
  skipped++;
  console.log(`SKIP ${name} :: ${reason}`);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 括号/引号感知地按顶层逗号切开 SNBT compound，避免 components 里嵌套同名字段误命中
function splitTopFields(body) {
  const parts = [];
  let depth = 0;
  let inStr = false;
  let start = 1; // 跳过开头的 {
  for (let i = 1; i < body.length - 1; i++) {
    const ch = body[i];
    if (inStr) {
      if (ch === '\\') { i++; continue; }
      if (ch === '"') inStr = false;
      continue;
    }
    if (ch === '"') { inStr = true; continue; }
    if (ch === '{' || ch === '[') { depth++; continue; }
    if (ch === '}' || ch === ']') { depth--; continue; }
    if (ch === ',' && depth === 0) {
      parts.push(body.slice(start, i));
      start = i + 1;
    }
  }
  parts.push(body.slice(start, body.length - 1));
  return parts.map((s) => s.trim()).filter((s) => s.length > 0);
}

// 引号/括号感知地切出 `data get block <pos> Items` 返回的每个 compound，取 id / count。
// 比 e2e.js 的 stackCount（只数 id 出现次数）更准：箱子会把同物品并堆，
// 2 个产物会落在 1 个槽位（count:2），只数 id 出现次数会误判成 1 个。
function parseItems(nbt) {
  const items = [];
  if (!nbt) return items;
  const start = nbt.indexOf('[');
  if (start < 0) return items;
  let depth = 0;
  let inStr = false;
  let objStart = -1;
  for (let i = start; i < nbt.length; i++) {
    const ch = nbt[i];
    if (inStr) {
      if (ch === '\\') { i++; continue; }
      if (ch === '"') inStr = false;
      continue;
    }
    if (ch === '"') { inStr = true; continue; }
    if (ch === '[' || ch === '{') {
      if (ch === '{' && depth === 1) objStart = i;
      depth++;
      continue;
    }
    if (ch === '}' || ch === ']') {
      depth--;
      if (ch === '}' && depth === 1 && objStart >= 0) {
        const fields = {};
        for (const part of splitTopFields(nbt.slice(objStart, i + 1))) {
          const m = part.match(/^([A-Za-z0-9_]+)\s*:\s*([\s\S]*)$/);
          if (m) fields[m[1]] = m[2];
        }
        const idm = (fields.id || '').match(/^"((?:[^"\\]|\\.)*)"$/);
        if (idm) {
          const count = parseInt(fields.count, 10);
          items.push({ id: idm[1], count: Number.isFinite(count) ? count : 1 });
        }
        objStart = -1;
      }
      if (depth === 0) break;
    }
  }
  return items;
}

function totals(items) {
  const map = new Map();
  for (const it of items) map.set(it.id, (map.get(it.id) || 0) + it.count);
  return map;
}
function formatTotals(items) {
  const list = [...totals(items)].map(([id, n]) => `${id}×${n}`);
  return list.length ? list.join(', ') : '(空)';
}

async function checkChest(cmd, label, pos, expectId, expectCount) {
  const nbt = await cmd(`data get block ${pos} Items`);
  const items = parseItems(nbt);
  const map = totals(items);
  const got = map.get(expectId) || 0;
  check(`${label} 箱内 ${expectId} 恰好 ${expectCount} 个`, got === expectCount,
    `实际 ${got}；箱内 ${formatTotals(items)}；原始 ${nbt.trim()}`);
  const stray = [...map.keys()].filter((id) => id !== expectId);
  check(`${label} 箱内无其它物品`, stray.length === 0,
    `混入 ${stray.join(', ')}；箱内 ${formatTotals(items)}`);
}

// ---------- 本地文件断言（版本 / 资源 / 配方） ----------

const short = (p) => path.relative(ROOT, p);
function isFile(p) {
  try { return fs.statSync(p).isFile(); } catch (e) { return false; }
}
function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}
// "ns:path" 资源引用 → 仓库内文件；非附属命名空间（如 minecraft:）返回 null（无法本地校验）
function resolveRef(resRoot, ref, kind, ext) {
  if (typeof ref !== 'string') return null;
  const m = ref.match(/^([a-z0-9_.-]+):(.+)$/);
  if (!m || m[1] !== ADDON_NS) return null;
  return path.join(resRoot, 'assets', ADDON_NS, kind, `${m[2]}.${ext}`);
}

function collectModelRefs(blockstate) {
  const refs = [];
  const push = (v) => { if (typeof v === 'string') refs.push(v); };
  const variants = blockstate.variants || {};
  for (const v of Object.values(variants)) {
    if (Array.isArray(v)) v.forEach((x) => push(x && x.model));
    else if (v) push(v.model);
  }
  if (Array.isArray(blockstate.multipart)) {
    for (const part of blockstate.multipart) {
      const apply = part && part.apply;
      if (Array.isArray(apply)) apply.forEach((x) => push(x && x.model));
      else if (apply) push(apply.model);
    }
  }
  return refs;
}

function collectItemModelRefs(def) {
  const refs = [];
  const walk = (model) => {
    if (typeof model === 'string') { refs.push(model); return; }
    if (Array.isArray(model)) { model.forEach(walk); return; }
    if (model && typeof model === 'object') {
      if (typeof model.model === 'string') refs.push(model.model);
      if (Array.isArray(model.models)) model.models.forEach(walk);
    }
  };
  if (def) walk(def.model);
  return refs;
}

// 迭代解析模型链（parent 最多 8 层）：模型文件存在、parent 可解析、textures 引用的 PNG 存在
function checkModelChain(resRoot, ref, label) {
  let cur = ref;
  let depth = 0;
  let seenLocal = false;
  const seen = new Set();
  while (cur && depth < 8) {
    const file = resolveRef(resRoot, cur, 'models', 'json');
    if (!file) break; // 指向原版模型（minecraft:block/...）→ 不在仓库内，停止下钻
    if (seen.has(file)) break;
    seen.add(file);
    if (!isFile(file)) {
      check(`${label} 模型文件存在 ${cur}`, false, short(file));
      return;
    }
    seenLocal = true;
    let json;
    try {
      json = readJson(file);
    } catch (e) {
      check(`${label} 模型 JSON 合法 ${cur}`, false, `${short(file)}: ${e.message}`);
      return;
    }
    if (json.textures && typeof json.textures === 'object') {
      for (const [key, val] of Object.entries(json.textures)) {
        if (typeof val !== 'string' || val.startsWith('#')) continue;
        const tex = resolveRef(resRoot, val, 'textures', 'png');
        if (tex) check(`${label} 贴图存在 ${key}=${val}`, isFile(tex), short(tex));
      }
    }
    cur = typeof json.parent === 'string' ? json.parent : null;
    depth++;
  }
  check(`${label} 模型链可解析 ${ref}`, seenLocal, '模型引用不在附属命名空间内，无法校验');
}

// gradle 版本解析：支持字面量、project.xxx / project.property('xxx') / ${xxx} / 裸属性名
function readProps(file) {
  const props = {};
  if (!isFile(file)) return props;
  for (const raw of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const m = line.match(/^([^=:\s]+)\s*[=:]\s*(.*)$/);
    if (m) props[m[1]] = m[2].trim();
  }
  return props;
}

function gradleVersion(buildDir) {
  const buildFile = path.join(buildDir, 'build.gradle');
  if (!isFile(buildFile)) return { value: null, source: `缺少 ${short(buildFile)}` };
  const text = fs.readFileSync(buildFile, 'utf8');
  const m = text.match(/^\s*version\s*=\s*(.+?)\s*$/m);
  if (!m) return { value: null, source: `build.gradle 无 version 行` };
  const expr = m[1].replace(/\s*\/\/.*$/, '').trim();
  if (/^['"].*['"]$/.test(expr)) return { value: expr.slice(1, -1), source: `${short(buildFile)} 字面量` };
  const propFiles = [path.join(buildDir, 'gradle.properties'), path.join(buildDir, '..', 'gradle.properties')];
  const props = {};
  for (const p of propFiles) Object.assign(props, readProps(p));
  // 裸属性名：version = main_mod_version
  if (/^[A-Za-z_][\w.]*$/.test(expr) && props[expr] !== undefined) {
    return { value: props[expr], source: `gradle.properties 的 ${expr}` };
  }
  const names = [];
  for (const mm of expr.matchAll(/project\.(?:property\(|findProperty\()?['"]?([\w.]+)['"]?\)?/g)) names.push(mm[1]);
  for (const mm of expr.matchAll(/\$\{([\w.]+)\}/g)) names.push(mm[1]);
  for (const name of names) {
    if (props[name] !== undefined) return { value: props[name], source: `gradle.properties 的 ${name}` };
  }
  return { value: null, source: `无法解析版本表达式「${expr}」` };
}

const normRange = (s) => (typeof s === 'string' ? s.replace(/,/g, ' ').replace(/\s+/g, ' ').trim() : s);

function tomlDepRange(text, modId) {
  for (const block of text.split(/^\[\[/m)) {
    if (!/^dependencies\./.test(block)) continue;
    if (!new RegExp(`modId\\s*=\\s*"${modId}"`).test(block)) continue;
    const m = block.match(/versionRange\s*=\s*"([^"]+)"/);
    if (m) return m[1];
  }
  return null;
}

// ---------- 用例 A：9 档方块放置 + 挖掘标签 ----------
async function caseA(cmd) {
  for (const n of LEVELS) {
    const id = addonGen(n);
    const x = AREA.x0 + (n - 1) * AREA.step;
    const pos = `${x} ${AREA.y} ${AREA.z}`;
    const res = await cmd(`setblock ${pos} ${id}`);
    check(`A ${n}x setblock`, !/Unknown|Failed|Could not/i.test(res), res);
    const placed = await cmd(`execute if block ${pos} ${id}`);
    check(`A ${n}x 方块存在（execute if block）`, /Test passed/.test(placed), placed);
    const pickaxe = await cmd(`execute if block ${pos} #minecraft:mineable/pickaxe`);
    check(`A ${n}x 在 #minecraft:mineable/pickaxe`, /Test passed/.test(pickaxe), pickaxe);
    // 主 mod 刷石机不是 needs_* 工具门（任意镐可挖）；附属 9 档应与主 mod 一致
    for (const tag of ['needs_stone_tool', 'needs_iron_tool', 'needs_diamond_tool']) {
      const gated = await cmd(`execute if block ${pos} #minecraft:${tag}`);
      check(`A ${n}x 不在 #minecraft:${tag}`, /Test failed/.test(gated), gated);
    }
  }
}

// ---------- 用例 B：9 档产物 ----------
// 每档机器 (x,-59,z)，正下方 (x,-60,z) 放箱子；1~9 档横向排开、间距 3 格，
// 保证每个容器只属于自己这台机器（不被邻居的产物串味）。
async function caseB(cmd) {
  for (const n of LEVELS) {
    const x = AREA.x0 + (n - 1) * AREA.step;
    await cmd(`setblock ${x} ${AREA.y} ${AREA.z} ${addonGen(n)}`);
    await cmd(`setblock ${x} ${AREA.y - 1} ${AREA.z} minecraft:air`);
    await cmd(`setblock ${x} ${AREA.y - 1} ${AREA.z} minecraft:chest`);
  }
  // 周期 20 tick：40 tick 恰好 2 个产物；sprint 精确推进，避免空服暂停/有人在线导致数量漂移
  await cmd('tick sprint 40t');
  await sleep(1600);
  for (const n of LEVELS) {
    const x = AREA.x0 + (n - 1) * AREA.step;
    await checkChest(cmd, `B ${n}x`, `${x} ${AREA.y - 1} ${AREA.z}`, productId(n), 2);
  }
}

// ---------- 用例 C：主 mod 原版级刷石机 ----------
async function caseC(cmd) {
  const x = AREA.baseX;
  await cmd(`setblock ${x} ${AREA.y} ${AREA.z} ${MAIN_NS}:${GEN}`);
  await cmd(`setblock ${x} ${AREA.y - 1} ${AREA.z} minecraft:air`);
  await cmd(`setblock ${x} ${AREA.y - 1} ${AREA.z} minecraft:chest`);
  await cmd('tick sprint 40t');
  await sleep(1600);
  await checkChest(cmd, 'C base', `${x} ${AREA.y - 1} ${AREA.z}`, 'minecraft:cobblestone', 2);
}

// ---------- 用例 F-live：战利品表（破坏方块必掉自身） ----------
// 与服务端权威一致：setblock ... destroy 走 Block.dropResources（不做工具等级门判定，
// 与 e2e.js 的「压缩熔炉挖掘门」注释同一口径），只验证战利品表确实掉本体。
async function caseLoot(cmd) {
  for (const n of LEVELS) {
    const id = addonGen(n);
    const pos = `${AREA.x0} ${AREA.lootY} ${AREA.lootZ}`;
    await cmd(`kill @e[type=minecraft:item,x=${AREA.x0},y=${AREA.lootY},z=${AREA.lootZ},distance=..5]`);
    const set = await cmd(`setblock ${pos} ${id}`);
    check(`F-live ${n}x 放置待破坏方块`, !/Unknown|Failed|Could not/i.test(set), set);
    const broke = await cmd(`setblock ${pos} minecraft:air destroy`);
    check(`F-live ${n}x destroy 执行`, !/Unknown|Failed|Could not/i.test(broke), broke);
    await sleep(250);
    const drop = await cmd(`data get entity @e[type=minecraft:item,x=${AREA.x0},y=${AREA.lootY},z=${AREA.lootZ},distance=..5,limit=1,sort=nearest] Item.id`);
    check(`F-live ${n}x 战利品掉自身`, drop.includes('"' + id + '"'), drop);
  }
  await cmd(`kill @e[type=minecraft:item,x=${AREA.x0},y=${AREA.lootY},z=${AREA.lootZ},distance=..5]`);
}

// ---------- 用例 D-live：recipe give/take 返回文本 ----------
// RCON 控制台不是玩家：recipe 命令的 targets 参数先于 recipe 解析，@s/@a 会先报
// "No player was found"，所以必须有在线玩家（或 Carpet 假人）才能拿到配方存在性结论。
async function ensureRecipePlayer(cmd) {
  if (/Test passed/.test(await cmd('execute if entity @a'))) {
    return { selector: '@a', name: null, spawned: false };
  }
  // Carpet：commandPlayer 默认可能是关的，先打开再造假人
  await cmd('carpet commandPlayer true').catch(() => {});
  await cmd(`player ${AREA.bot} kill`).catch(() => {});
  const spawn = await cmd(`player ${AREA.bot} spawn at ${AREA.botPos} facing 0 0`);
  if (/Unknown or incomplete command|Incorrect argument|Unknown command|Usage:/i.test(spawn)) return null;
  for (let i = 0; i < 20; i++) {
    await sleep(200);
    if (/Test passed/.test(await cmd(`execute if entity ${AREA.bot}`))) {
      return { selector: AREA.bot, name: AREA.bot, spawned: true };
    }
  }
  return null;
}

async function caseDlive(cmd, bot) {
  const sel = bot.selector;
  for (const n of LEVELS) {
    for (const id of [recipeCraft(n), recipeUnpack(n)]) {
      // 先 take 清掉可能的旧解锁，避免 give 命中 "No new recipes were learned"
      const take = await cmd(`recipe take ${sel} ${id}`);
      const give = await cmd(`recipe give ${sel} ${id}`);
      const resp = take + ' | ' + give;
      const unknown = /Unknown recipe/i.test(resp);
      const noPlayer = /No player|not a player|No entity/i.test(resp);
      const gave = /Unlocked \d+ recipe\(s\)/i.test(give) || /No new recipes were learned/i.test(give);
      check(`D-live 配方存在 ${id}`, !unknown && !noPlayer && gave,
        `take=${take.trim()} | give=${give.trim()}`);
    }
  }
}

// ---------- 用例 D-file：recipe JSON 结构（不依赖服务器） ----------
function checkRecipeFile(file, label, expect) {
  if (!isFile(file)) {
    check(`${label} 文件存在`, false, short(file));
    return;
  }
  check(`${label} 文件存在`, true);
  let json;
  try {
    json = readJson(file);
  } catch (e) {
    check(`${label} JSON 合法`, false, `${short(file)}: ${e.message}`);
    return;
  }
  const result = json.result || {};
  const resultId = result.id !== undefined ? result.id : result.item;
  check(`${label} 产物 id`, resultId === expect.resultId, `期望 ${expect.resultId} 实际 ${JSON.stringify(resultId)}`);
  const count = result.count !== undefined ? result.count : 1;
  check(`${label} 产物数量`, count === expect.resultCount, `期望 ${expect.resultCount} 实际 ${count}`);
  const text = JSON.stringify(json);
  check(`${label} 原料含 ${expect.mustContain}`, text.includes(expect.mustContain), text.slice(0, 200));
}

function caseDfile() {
  for (const resRoot of ADDON_RES) {
    const where = short(resRoot);
    for (const n of LEVELS) {
      checkRecipeFile(path.join(resRoot, 'data', ADDON_NS, 'recipe', `${n}x_${GEN}.json`),
        `D-file ${where} 合成 ${n}x`, {
          resultId: addonGen(n),
          resultCount: 1,
          // 原版 Minecraft 没有刷石机：1x 的 9 个原料是主 mod 的原版级刷石机方块
          mustContain: n === 1 ? `${MAIN_NS}:${GEN}` : addonGen(n - 1),
        });
      checkRecipeFile(path.join(resRoot, 'data', ADDON_NS, 'recipe', `unpack_${n}x_${GEN}.json`),
        `D-file ${where} 拆解 ${n}x`, {
          resultId: n === 1 ? `${MAIN_NS}:${GEN}` : addonGen(n - 1),
          resultCount: 9,
          mustContain: addonGen(n),
        });
    }
  }
}

// ---------- 用例 E：版本一致性（成对发布 + 依赖区间） ----------
function caseE() {
  const builds = [
    ['1.21.11/fabric/build.gradle', path.join(ROOT, '1.21.11', 'fabric')],
    ['1.21.11/neoforge/build.gradle', path.join(ROOT, '1.21.11', 'neoforge')],
    ['1.21.11/addon/fabric/build.gradle', path.join(ROOT, '1.21.11', 'addon', 'fabric')],
    ['1.21.11/addon/neoforge/build.gradle', path.join(ROOT, '1.21.11', 'addon', 'neoforge')],
  ];
  const versions = [];
  for (const [label, dir] of builds) {
    const r = gradleVersion(dir);
    versions.push(r.value);
    check(`E ${label} 版本 = ${VERSION}`, r.value === VERSION, `解析到 ${JSON.stringify(r.value)}（${r.source}）`);
  }
  check('E 主 mod / 附属 mod 版本一致（必须成对发布）', versions[0] === versions[2], `主=${versions[0]} 附=${versions[2]}`);

  const fabricModJson = path.join(ROOT, '1.21.11', 'addon', 'fabric', 'src', 'main', 'resources', 'fabric.mod.json');
  if (!isFile(fabricModJson)) {
    check('E 附属 fabric.mod.json 存在', false, short(fabricModJson));
  } else {
    const j = readJson(fabricModJson);
    const dep = (j.depends || {}).compressedblocks;
    check(`E 附属 fabric.mod.json 依赖 compressedblocks ${DEP_FABRIC}`,
      normRange(dep) === normRange(DEP_FABRIC), `实际 ${JSON.stringify(dep)}`);
    check('E 附属 fabric.mod.json 仍是 id + ${version} 占位',
      j.id === ADDON_NS && j.version === '${version}', `id=${JSON.stringify(j.id)} version=${JSON.stringify(j.version)}`);
  }

  const tomlFile = path.join(ROOT, '1.21.11', 'addon', 'neoforge', 'src', 'main', 'resources',
    'META-INF', 'neoforge.mods.toml');
  if (!isFile(tomlFile)) {
    check('E 附属 neoforge.mods.toml 存在', false, short(tomlFile));
  } else {
    const text = fs.readFileSync(tomlFile, 'utf8');
    const range = tomlDepRange(text, 'compressedblocks');
    check(`E 附属 neoforge.mods.toml 依赖 compressedblocks ${DEP_NEOFORGE}`,
      range === DEP_NEOFORGE, `实际 ${JSON.stringify(range)}`);
  }
}

// ---------- 用例 F：附属资源存在性（blockstate / items / 模型贴图 / 战利品 / 语言 / 挖掘标签） ----------
function caseFfiles() {
  for (const resRoot of ADDON_RES) {
    const where = short(resRoot);
    for (const n of LEVELS) {
      const name = `${n}x_${GEN}`;
      // 1) blockstate → 模型引用 → 模型链（parent / 贴图引用必须能在仓库内解析到文件）
      const bsFile = path.join(resRoot, 'assets', ADDON_NS, 'blockstates', `${name}.json`);
      if (!isFile(bsFile)) {
        check(`F ${where} blockstate ${name} 存在`, false, short(bsFile));
      } else {
        check(`F ${where} blockstate ${name} 存在`, true);
        try {
          const bs = readJson(bsFile);
          const refs = collectModelRefs(bs);
          check(`F ${where} blockstate ${name} 有模型引用`, refs.length > 0, JSON.stringify(bs).slice(0, 200));
          for (const ref of refs) checkModelChain(resRoot, ref, `F ${where} ${name}`);
        } catch (e) {
          check(`F ${where} blockstate ${name} JSON 合法`, false, e.message);
        }
      }
      // 2) items/ 物品模型定义
      const itemDef = path.join(resRoot, 'assets', ADDON_NS, 'items', `${name}.json`);
      if (!isFile(itemDef)) {
        check(`F ${where} items/${name}.json 存在`, false, short(itemDef));
      } else {
        check(`F ${where} items/${name}.json 存在`, true);
        try {
          const def = readJson(itemDef);
          const refs = collectItemModelRefs(def);
          check(`F ${where} items/${name}.json 有模型引用`, refs.length > 0, JSON.stringify(def).slice(0, 200));
          for (const ref of refs) checkModelChain(resRoot, ref, `F ${where} ${name} item`);
        } catch (e) {
          check(`F ${where} items/${name}.json JSON 合法`, false, e.message);
        }
      }
      // 3) 战利品表掉自身
      const lootFile = path.join(resRoot, 'data', ADDON_NS, 'loot_table', 'blocks', `${name}.json`);
      if (!isFile(lootFile)) {
        check(`F ${where} 战利品表 ${name} 存在`, false, short(lootFile));
      } else {
        check(`F ${where} 战利品表 ${name} 存在`, true);
        const text = fs.readFileSync(lootFile, 'utf8');
        check(`F ${where} 战利品表 ${name} 掉自身`, text.includes(`${ADDON_NS}:${name}`), text.slice(0, 200));
      }
    }
    // 4) 挖掘标签：必须写在原版标签命名空间 data/minecraft/tags/block/mineable/pickaxe.json
    //    （旧 addon 的 data/compressedblockspot/minecraft/tags/... 会变成自建标签，原版不认）
    const tagRel = ['data', 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'].join('/');
    const tagFile = path.join(resRoot, tagRel);
    let values = null;
    if (isFile(tagFile)) {
      try { values = readJson(tagFile).values || []; } catch (e) { values = null; }
    }
    check(`F ${where} 挖掘标签 ${tagRel} 存在且可解析`, Array.isArray(values), short(tagFile));
    if (Array.isArray(values)) {
      for (const n of LEVELS) {
        check(`F ${where} 挖掘标签含 ${addonGen(n)}`, values.includes(addonGen(n)), values.join(', ').slice(0, 200));
      }
    }
    // 5) 双语名 + 创造栏 lang（0.4.0 起附属两栏：压缩拓展方块 / 压缩拓展工具；
    //    0.4.1 起 addon_tools 另含 10 件主 mod 超模机器）
    for (const lang of ['en_us', 'zh_cn']) {
      const langFile = path.join(resRoot, 'assets', ADDON_NS, 'lang', `${lang}.json`);
      if (!isFile(langFile)) {
        check(`F ${where} lang/${lang}.json 存在`, false, short(langFile));
        continue;
      }
      let j;
      try {
        j = readJson(langFile);
      } catch (e) {
        check(`F ${where} lang/${lang}.json JSON 合法`, false, e.message);
        continue;
      }
      for (const n of LEVELS) {
        const key = `block.${ADDON_NS}.${n}x_${GEN}`;
        check(`F ${where} ${lang} ${key}`, typeof j[key] === 'string' && j[key].length > 0, JSON.stringify(j[key]));
      }
      for (const key of ['itemGroup.compressedblockspot.addon_blocks', 'itemGroup.compressedblockspot.addon_tools']) {
        check(`F ${where} ${lang} ${key}（五栏结构）`, typeof j[key] === 'string' && j[key].length > 0, JSON.stringify(j[key]));
      }
    }
  }
}

function cleanup(cmd) {
  const steps = [];
  for (const n of LEVELS) {
    const x = AREA.x0 + (n - 1) * AREA.step;
    steps.push(`setblock ${x} ${AREA.y} ${AREA.z} minecraft:air`);
    steps.push(`setblock ${x} ${AREA.y - 1} ${AREA.z} minecraft:air`);
  }
  steps.push(`setblock ${AREA.baseX} ${AREA.y} ${AREA.z} minecraft:air`);
  steps.push(`setblock ${AREA.baseX} ${AREA.y - 1} ${AREA.z} minecraft:air`);
  steps.push(`setblock ${AREA.x0} ${AREA.lootY} ${AREA.lootZ} minecraft:air`);
  steps.push(`kill @e[type=minecraft:item,x=192,y=-64,z=192,dx=48,dy=24,dz=48]`);
  steps.push(`player ${AREA.bot} kill`);
  steps.push('tick unfreeze');
  steps.push(`forceload remove ${FORCELOAD}`);
  return steps.reduce((p, c) => p.then(() => cmd(c).catch(() => {})), Promise.resolve());
}

function finish(tag) {
  const suffix = tag ? ` <${tag}>` : '';
  if (failures === 0) {
    console.log(`E2E-ADDON ALL PASS${suffix} :: ${checks} checks, ${skipped} skipped`);
  } else {
    console.log(`E2E-ADDON FAILURES: ${failures}/${checks} checks, ${skipped} skipped${suffix}`);
  }
  process.exit(failures);
}

(async () => {
  // 纯本地断言先跑：服务端没起也能定位版本/资源/配方问题
  console.log('== 本地断言：E 版本一致性 / F 附属资源 / D-file 配方 ==');
  caseE();
  caseFfiles();
  caseDfile();
  if (FILES_ONLY) {
    finish('files-only');
    return;
  }

  let rcon;
  try {
    rcon = await new Rcon().connect(PORT, HOST, PASS);
  } catch (e) {
    check(`连接 RCON ${HOST}:${PORT}`, false, e.message);
    finish('rcon-connect-failed');
    return;
  }
  const cmd = (c) => rcon.command(c);

  try {
    console.log('== 前置：forceload / 探针自检 / tick freeze ==');
    await cmd('gamerule doMobSpawning false');
    await cmd(`forceload add ${FORCELOAD}`);
    const probe = await cmd(`execute if block ${AREA.x0} -64 ${AREA.z} minecraft:bedrock`);
    check('探针：测试区 -64 层是基岩（超平坦世界）', /Test passed/.test(probe), probe);
    const probeNeg = await cmd(`execute if block ${AREA.x0} -64 ${AREA.z} minecraft:stone`);
    check('探针：负断言可用', /Test failed/.test(probeNeg), probeNeg);
    // 冻结 tick：保证下面的 tick sprint 40t 精确 = 2 个产物（不受空服暂停/在线玩家影响）
    await cmd('tick freeze');

    console.log('== 用例 A：9 档方块放置 + 挖掘标签 ==');
    await caseA(cmd);
    console.log('== 用例 B：9 档产物（40t → 箱内恰好 2 个） ==');
    await caseB(cmd);
    console.log('== 用例 C：主 mod 原版级刷石机（40t → 箱内恰好 2 个原石） ==');
    await caseC(cmd);
    console.log('== 用例 F-live：9 档战利品掉自身 ==');
    await caseLoot(cmd);
    await cmd('tick unfreeze');

    // D-live 放在 generator 用例之后：假人在线会让服务器真实 tick，先做完定时断言
    if (RECIPE_MODE === 'file') {
      skip('D-live recipe give/take', 'RECIPE_MODE=file');
    } else {
      const bot = await ensureRecipePlayer(cmd);
      if (!bot) {
        const why = '无在线玩家且 /player 造不出 Carpet 假人';
        if (RECIPE_MODE === 'live') check('D-live recipe give/take', false, why);
        else skip('D-live recipe give/take', `${why}；配方存在性已由 D-file 覆盖`);
      } else {
        const who = bot.spawned ? `Carpet 假人 ${bot.name}` : `在线玩家 ${bot.selector}`;
        console.log(`== 用例 D-live：18 条配方存在性（${who}） ==`);
        await caseDlive(cmd, bot);
        if (bot.spawned) await cmd(`player ${bot.name} kill`).catch(() => {});
      }
    }
  } catch (e) {
    check('服务端用例执行（未预期异常）', false, e.message);
  } finally {
    // 清理测试区并恢复 tick 状态；/stop 用 Promise.race 防 socket 关闭把退出码吞成 0
    await cleanup(cmd).catch(() => {});
    await Promise.race([cmd('stop').catch(() => {}), sleep(1000)]);
    rcon.destroy();
  }
  finish('');
})().catch((e) => {
  console.error('E2E-ADDON ERROR:', e);
  process.exit(99);
});
