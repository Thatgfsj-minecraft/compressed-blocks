# Compressed Blocks（压缩方块）

把常见方块 3×3 压缩成 1~9 重，方便携带；压缩方块和压缩木棍可以做成工具，**耐久按 9ⁿ 指数上升、挖掘速度逐重提升，六重及以上直接不可破坏**。

Thatgfsj-minecraft 组织出品，开发约定见 [mc-mod-dev-skill](https://github.com/Thatgfsj-minecraft/mc-mod-dev-skill)。

## 特性

- **171 种压缩方块**（19 材料 × 1~9 重）：
  - 石族：圆石、石头、深板岩圆石、深板岩
  - 原木：橡木/云杉/白桦/丛林/金合欢/深色橡木/红树/樱花/苍白橡木
  - 其他：泥土、沙子、沙砾、下界岩、末地石、黑曜石
- **9 重压缩木棍**：9 木棍 → 压缩木棍 → 二重压缩木棍 → …
- **585 件压缩工具**（13 材料 × 镐/斧/锹/锄/剑 × 9 重）
  - **耐久 = 9ⁿ × 基础**（石 131：1179 → 10611 → 95499 → 859491 → 7735419；木 59 同式）；**六重起不可破坏**
  - **挖掘速度逐级连乘**：一重 = 铁镐（6.0）、二重 = 钻镐（8.0）、三重 = 金镐（12.0）；**四重 = 三重 ×1.2、五重 = 四重 ×1.2……**（14.4 → 17.28 → 20.74 → 24.88 → 29.86 → 35.83）；剑不参与（保持原版）
  - **攻击伤害 = 原版 + 重数**（一重即原版 +1，九重 = 原版 +9，如石剑 4 → 13）
  - 攻击速度/附魔能力 = 原版石级/木级不变；铁砧修复材料 = 该材料任意重数压缩方块
- 合成：3×3 压缩（9→1）、无序解压（1→9）、工具按原版排列——**材料位 = 同重数压缩方块，棍位 = 同重数压缩木棍**
- 纯数据驱动，无任何交互逻辑，客户端零行为改动；独立创造物品栏**「压缩」**（排在原版标签之后，排序 = 方块 → 工具类型 → 等级）

## 数量核算（每个子项目）

765 物品（171 方块 + 585 工具 + 9 木棍）｜945 配方｜171 战利品表｜765 贴图｜双语语言文件

## 构建 / 测试

```bash
cd 1.21.11/fabric   && GRADLE_USER_HOME=<干净目录> ./gradlew build
cd 1.21.11/neoforge && GRADLE_USER_HOME=<干净目录> ./gradlew build
# 产物：build/libs/compressedblocks-<loader>-1.21.11-<version>.jar
```

- 环境与版本坐标（Loom 1.17.21 / ModDevGradle 2.0.147 / Gradle 9.5.1 / JDK 21）见 `../mc-mod-dev-skill/SKILL.md`
- **GRADLE_USER_HOME 必须指向无 init.gradle 的干净目录**（全局阿里云镜像会破坏 NeoForge 解析）
- 贴图与全部 JSON 由脚本生成：`python scripts/gen_assets.py && python scripts/gen_resources.py`
- E2E：`ci/` 下的专用服 + RCON 断言（`node ci/e2e.js`，环境变量 `RCON_PORT` 指定端口），服务器启动日志搜 `SELF-TEST PASS`（服务端权威比对注册数/耐久公式/不可破坏/速度阶梯）

## 素材说明

全部贴图由 `scripts/gen_assets.py` 从**原版**方块/工具 sprite 程序化重绘（逐级加深 + 嵌套环），未复制任何第三方模组像素，仅借鉴压缩类模组的通用视觉语言。工具头部 = 石工具 sprite 灰色像素几何（原版各材质工具几何一致），六重起头部白色高光 = "不可破坏"标记。

原版基础 sprite 不入库：首次生成前把它们放到 `_asset-src/vanilla/`（命名如 `block_stone.png`、`item_stick.png`，共 22 张 = 19 张方块底图 + 石/木工具各 5 张 + 木棍，从 Gradle Loom 缓存的 `minecraft-client.jar` 内 `assets/minecraft/textures/` 解出，文件名 = jar 内路径把 `/` 换成 `_`；本地已有时跳过）。


## License

MIT，见根目录 LICENSE。
