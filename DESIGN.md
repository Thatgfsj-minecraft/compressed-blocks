# 压缩方块（Compressed Blocks）内容规格 v2（最终）

目标版本：1.21.1 / 1.21.11 × Fabric / NeoForge（组织标准 2×2 四子项目）。

## 1. 身份

| 项 | 值 |
|---|---|
| modid | `compressedblocks` |
| 显示名 | 压缩方块 / Compressed Blocks |
| 包名 | `dev.compressedblocks`（沿用组织 `dev.<modid>` 风格） |
| 版本 | 0.1.0 |

## 2. 压缩方块矩阵（19 材料 × 1~9 重 = 171 方块）

每一重 = 9 个上一重（3×3 有序合成）；反向：1 个第 n 重 → 9 个第 n-1（无序）。

### 可做工具的材料（13）

| key | 中文名 | en | 工具级别 |
|---|---|---|---|
| cobblestone | 圆石 | Cobblestone | 石 |
| stone | 石头 | Stone | 石 |
| cobbled_deepslate | 深板岩圆石 | Cobbled Deepslate | 石 |
| deepslate | 深板岩 | Deepslate | 石 |
| oak_log | 橡木原木 | Oak Log | 木 |
| spruce_log | 云杉原木 | Spruce Log | 木 |
| birch_log | 白桦原木 | Birch Log | 木 |
| jungle_log | 丛林原木 | Jungle Log | 木 |
| acacia_log | 金合欢原木 | Acacia Log | 木 |
| dark_oak_log | 深色橡木原木 | Dark Oak Log | 木 |
| mangrove_log | 红树原木 | Mangrove Log | 木 |
| cherry_log | 樱花原木 | Cherry Log | 木 |
| pale_oak_log | 苍白橡木原木 | Pale Oak Log | 木 |

### 仅方块的材料（6）

泥土 dirt、沙子 sand、沙砾 gravel、下界岩 netherrack、末地石 end_stone、黑曜石 obsidian。

### 命名

- 方块 id：`compressed_<mat>`、`double_compressed_<mat>`、`triple_…`、`quadruple_…`、`quintuple_…`、`sextuple_…`、`septuple_…`、`octuple_…`、`nonuple_…`
- zh：`压缩圆石 / 二重压缩圆石 / 三重 / 四重 / 五重 / 六重 / 七重 / 八重 / 九重压缩圆石`
- en：`Compressed / Double / Triple / Quadruple / Quintuple / Sextuple / Septuple / Octuple / Nonuple Compressed …`

## 3. 压缩木棍（9 个物品）

- `compressed_stick`、`double_compressed_stick`、… `nonuple_compressed_stick`
- 合成：9 木棍 → 压缩木棍；9 第 n-1 重 → 第 n 重；可无序解压
- **第 n 重工具的配方里，木棍位必须用第 n 重压缩木棍**
- 创造栏：独立页签「压缩」的末位

## 4. 压缩工具（13 材料 × 5 类型 × 9 重 = 585 件）

- 类型：镐 pickaxe / 斧 axe / 锹 shovel / 锄 hoe / 剑 sword；id `<前缀>_compressed_<mat>_<tool>`
- **耐久** = 9ⁿ × 基础（石 131 / 木 59），n=1..5 为真实数值；**n≥6 不可破坏**（`minecraft:unbreakable` 组件，MAX_DAMAGE=Int.MAX）
- **挖掘速度**（1.21.11 ToolMaterial.speed，全部工具材料统一阶梯，逐级连乘）：
  一重=铁 6.0、二重=钻 8.0、三重=金 12.0；**四重=三重×1.2、五重=四重×1.2……** 即 n≥4 时 v(n)=12×1.2^(n-3)
  | n | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
  |---|---|---|---|---|---|---|---|---|---|
  | 速度 | 6.0 | 8.0 | 12.0 | 14.4 | 17.28 | 20.74 | 24.88 | 29.86 | 35.83 |
- **攻击伤害 = 原版 + 重数**（一重即原版 +1，n 重 = 原版 + n）：实现为 ToolMaterial.attackDamageBonus += n
  石级九重：镐 11 / 斧 17 / 锹 11.5 / 锄 9 / 剑 13；木级九重：镐 10 / 斧 15 / 锹 10.5 / 锄 9 / 剑 12
- 攻击速度：原版石级/木级不变；附魔能力：石 5 / 木 15（原版不变）；铁砧修复材料 = 该材料任意重数压缩方块

## 5. 合成表

1. 压缩：3×3，9×第 n-1 重 → 1×第 n 重（方块 171 + 木棍 9 条链）
2. 解压：无序，1×第 n 重 → 9×第 n-1 重
3. 工具：原版排列，材料位 = 同重数压缩方块，棍位 = 同重数压缩木棍

## 6. 方块属性（随重数缩放）

hardness = 基础硬度 × n；blast = 基础爆炸抗性 × n；需要正确工具与基础一致：
- 石/圆石/下界岩/末地石/黑曜石/深板岩系：requiresCorrectToolForDrops + mineable/pickaxe；深板岩系加 needs_stone_tool，黑曜石加 needs_diamond_tool
- 泥土/沙子/沙砾：mineable/shovel；原木：mineable/axe、无需工具
- 声音：石族 STONE（深板岩系 DEEPSLATE）、泥土/沙砾 GRAVEL、沙子 SAND、下界岩 NETHERRACK、原木 WOOD；黑曜石基础 50/1200
- 全部用普通 cube（原木不带 AXIS、沙子沙砾不坠落），战利品表掉自身 + survives_explosion

## 7. 素材方案

- 全部程序化重绘（原版贴图打底），不复制任何第三方 mod 像素，只借鉴压缩类模组"逐级变暗"的通用视觉语言
- 方块：field 0.84^(n-1) 加深 + 深色嵌套环逐级累积，九重加白色核心点
- 工具：石工具 sprite 灰像素几何 = 头部遮罩，重着色为材料色；六重起头部轮廓白色高光 = "不可破坏"标记
- 压缩木棍：木棍 sprite 逐级加深 + 剪影描边
- 生成器 `scripts/gen_assets.py`；mod 图标 = 九重压缩圆石 8× 放大

## 8. 数量核算（每子项目）

| 类别 | 数量 |
|---|---|
| 方块 | 171 |
| 物品 | 171 BlockItem + 585 工具 + 9 压缩木棍 = 765 |
| 配方 | 180 压缩 + 180 解压 + 585 工具 = 945 |
| 贴图 | 765 PNG |
| 战利品表 | 171 |
| 模型 JSON | blockstates 171 + models/block 171 + items 765 + models/item 594 |
| 标签 | mineable 3 + needs_stone_tool + needs_diamond_tool + repair 13 |

## 9. 验收

- 1.21.11 两 jar 构建全绿
- 启动自检（服务启动后跑）：注册计数（blocks=171/items=765）、n≤5 耐久 = 9ⁿ×基础、n≥6 带 unbreakable 且 isDamageableItem()==false、速度阶梯正确 → 日志 `SELF-TEST PASS`
- E2E（专用 Fabric 服 + RCON）：`/setblock`+`/execute if block` 方块断言、`/item replace`+`/data get entity` 读 max_damage/unbreakable 组件、配方加载无报错
