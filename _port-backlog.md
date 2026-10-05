# 压缩方块 mod 多版本移植 backlog

> 分仓策略（用户定稿）：`main` 分支保持 1.21.11 正式线（代码绝不改动）；所有移植版本都在本仓库内的长期分支 `port/<版本>` 上进行，每版本使用独立 git worktree（`O:/clawwork/chuansongmen/cb-port-<版本>`），版本代码与 main 彻底隔离，等效于从组织主线中分出。
> `main` 上只提交本文件的路线图/状态镜像，代码一律不进 main。各版本的详细进度笔记在对应 `port/<版本>` 分支上的本文件中维护。

## 版本路线图与状态

| 分支 | 目标 MC 版本 | 加载器 | 状态 | worktree | 备注 |
|---|---|---|---|---|---|
| port/26.3 | 26.3 "Wilderness Bound"（26.x 新版本线；2026-09-15 发布） | Fabric + Fabric API 0.161.0+26.3 | 进行中-进度笔记 | O:/clawwork/chuansongmen/cb-port-26.3 | 主 mod + 附属全量移植 + headless 服务端实机验证通过（2026-10-05，见下方进度笔记） |
| port/1.16.5 | 1.16.5 | Forge 36.x | 未开始 | O:/clawwork/chuansongmen/cb-port-1.16.5 | main 已带早期 1.16.5/forge 脚手架（FG 4.1），可复用/现代化 |
| port/1.12.2 | 1.12.2 | Forge 14.23.x | 未开始 | （尚未建） | 排队中 |
| port/1.7.10 | 1.7.10 | Forge 10.x | 未开始 | （尚未建） | 排队中 |

状态取值：`未开始` / `进行中-进度笔记` / `已完成`。每轮从本表自上而下取最多 2 个"未开始或进行中"的版本开工。

## 移植阶段（每个版本通用顺序，能做多少做多少）

1. ① 核心 43 材料 × 9 级重压缩储存方块（注册/贴图/模型/配方）
2. ② 树叶/树苗/甘蔗（及耕地/作物）
3. ③ 压缩工具/盔甲/食物
4. ④ 压缩箱子/潜影盒（243 格滚动容器）
5. ⑤ 盆栽家族（盆栽+漏斗盆栽）与刷石机 ×3
6. ⑥ 附属 mod compressedblockspot（压缩盆栽+压缩漏斗盆栽，1.21.11 参考实现在 `1.21.11/addon/`）

贴图/模型/配方优先改造 `scripts/` 下的生成器（gen_assets.py / gen_resources.py / gen_addon_assets.py）批量产出旧版本格式资源。

## 环境备忘

- 本机 JDK：仅 JDK21（`C:/Program Files/Amazon Corretto/jdk21.0.10_7`）。旧版本所需的其他 Java（8/17/25 等）用 Gradle toolchains + foojay resolver 自动下载，无法解决则把阻塞点记入进度笔记，不要卡死。
- Gradle 缓存统一：`GRADLE_USER_HOME=O:/clawwork/chuansongmen/.gradle-home`。
- 网络直连失败时用代理 `http://127.0.0.1:7897`；git push 先试 `git -c http.proxy= push`，失败改用 `git -c http.proxy=http://127.0.0.1:7897 push`。
- 纪律：写源码一律用 Write/Edit 工具；绝不 taskkill 全部 java；不部署任何东西到 H:\Minecraft；不新建/删除定时任务；不动 main 分支代码。

## 进度笔记（镜像入口）

各版本详细进度见各分支上的 `_port-backlog.md`（本轮开工后由各分支自行维护）。

---

# port/26.3 进度笔记（2026-10-04）

## 完成阶段

- ①~⑤ 主 mod 全量移植完成：43 材料×9 重储存方块、树叶/树苗/耕地/作物/压缩甘蔗（51 材料系）、
  盆栽+漏斗盆栽、刷石机×3、压缩箱子/潜影盒（243 格滚动容器）、压缩工具/盔甲/食物、22 兼容金属——
  Java 源码从 1.21.11/fabric 全量带入并完成 26.3 API 适配，贴图/模型/方块状态/配方/战利品表/
  lang 等 1.3 万个资源文件直接复用（26.3 未变更这些 JSON 格式，运行时待最终验证）。
- ⑥ 附属 mod compressedblockspot 移植完成：`26.3/addon/fabric/` 独立 gradle 子项目
  （沿用 1.21.11/addon 结构），gradle build 通过。

## 版本号依据

| 项 | 版本 | 依据 |
|---|---|---|
| MC | 26.3 stable | https://meta.fabricmc.net/v2/versions/game（唯一 26.x stable；26.60 未发布） |
| Fabric Loader | 0.19.5 | https://meta.fabricmc.net/v2/versions/loader（唯一 stable） |
| Fabric API | 0.161.0+26.3 | Modrinth fabric-api 版本列表（2026-09-18 发布） |
| fabric-loom | 1.18.2 | https://maven.fabricmc.net/net/fabricmc/fabric-loom/ maven-metadata release |
| Gradle | 9.7.1（wrapper） | loom 1.18.2 插件元数据要求 Gradle 9.7.0+（9.5.1 会在插件解析时报 variant 不匹配） |
| Java | 25（守护进程+编译） | 26.x 要求 Java 25；本机仅 JDK21，用共享缓存 `.gradle-home/jdks/corretto-25.0.4.10.1`（foojay 已下载） |

## 构建方式（26.x 与 1.21.x 的构建脚本差异）

按 FabricMC 官方 fabric-example-mod（26.3 模板）重写：
- 插件 id：`fabric-loom` → `net.fabricmc.fabric-loom`
- **不声明 mappings**（26.1+ 游戏去混淆发布，Fabric 直接用官方名；1.21.11 的
  `loom.officialMojangMappings()` 在 26.3 会报 "Failed to find official mojang mappings"）
- loader/fabric-api 从 `modImplementation` 改 `implementation`（无 intermediary，无需 remap，
  构建产物也不再走 remapJar）
- access widener 头：`accessWidener v2 named` → `v2 official`
- loom 1.18 默认关闭 mixin annotation processor（不再生成 refmap；`loom.mixin` 配置块已删）
- 守护进程 JVM：loom 1.18 本身要求 JVM 25 运行 Gradle，`gradle.properties` 里
  `org.gradle.java.home` 指向共享缓存的 corretto-25

## build 结论

- `26.3/fabric`：`gradlew build` **BUILD SUCCESSFUL**，产出 `compressedblocks-fabric-26.3-0.3.2.jar`
- `26.3/addon/fabric`：`gradlew build` **BUILD SUCCESSFUL**，产出 `compressedblockspot-fabric-26.3-0.3.2.jar`
- 编译错误从首轮 174 个按错误驱动清零，全部为 1.21.11→26.3 的 API 漂移

## 主要 API 漂移与适配（1.21.11 → 26.3）

- 工具类：`AxeItem`/`ShovelItem`/`HoeItem` 等具体类删除，统一 `Item.Properties` 工厂
  （`props.axe/hoe/shovel/pickaxe/sword(material, dmg, speed)`）+ `new Item(...)`；
  斧头判定改 `#minecraft:axes` 物品标签（盆栽拔植物）
- 树苗生长：`ConfiguredFeature` 注册表并入 `Feature`（`Registries.CONFIGURED_FEATURE` 消失）；
  `TreeGrower` 构造器改为三张 `WeightedList<ResourceKey<Feature>>`（树/巨型树/花树）+ 必填
  最小型树键；数据目录 `worldgen/configured_feature/` → `worldgen/feature/`，
  状态提供器 `Name/Properties` → `id/properties`，`dirt_provider` → `below_trunk_provider`
  （126 个树特征 JSON 由新脚本 `scripts/gen_assets_26.py` 批量转换）
- 聊天提示：`displayClientMessage(msg, overlay)` 拆成 `sendSystemMessage` / `sendOverlayMessage`
- 渲染（BER）：`Material`/`MaterialSet` 删除 → `SpriteId(atlas, texture)` + `SpriteGetter`
  （`ctx.sprites()`）；`ctx.blockRenderDispatcher()` 删除 → 方块模型/土壤/作物渲染统一改
  `MovingBlockRenderState` + `collector.submitMovingBlock`（原版下落方块同款管线，自带
  生物群系染色与位置光照）；`PoseStack.mulPose(Quaternionf)` 删除 → `mulPose(Matrix4f)`；
  `CameraRenderState` 挪包 `client.renderer.state.level`；`RenderTypes.entityCutoutNoCull(id,b)`
  → `entityCutout(id,b)`；`ChunkSectionLayer` 只剩 SOLID/CUTOUT/TRANSLUCENT
- Screen（滚动容器 UI）：`GuiGraphics` 与 `render`/`renderBg` 删除，改 `GuiGraphicsExtractor`
  extract 管线（blit/blitSprite/fill 指令在 `extractRenderState` 里提交，带 RenderPipeline 参数）；
  `imageWidth`/`imageHeight` 变 final，经新构造器 `super(menu, inv, title, w, h)` 传入
- 杂项：`Level.random` 收紧 protected → `getRandom()`；`PushReaction.DESTROY` → `POPPED`；
  `ItemContainerContents.nonEmptyStream()` → `nonEmptyItemCopyStream()`；
  `playSound` 的 BlockPos 重载只收 `SoundEvent`，`SoundEvents.HOE_TILL`（Holder）取 `.value()`

## 遗留与下一步（backlog 项）

1. **运行时验证未做**：只过了编译与打包，未启动 26.3 客户端/服务端实机验证
   （创造栏图标、树特征生成、盆栽渲染观感、滚动容器交互、access widener 生效、
   mixin 三个注入点在 26.3 字节码上是否命中——26.3 无 refmap，运行时靠 loader 解析）。
2. 附属 `fabric.mod.json` 依赖 `compressedblocks >=0.3.0 <0.4.0` 不变；实机需与主 mod 同装。
3. `scripts/gen_assets.py`（贴图合成）未改造：26.3 贴图格式与 1.21.11 相同，直接复用了
   已生成的资源；若以后贴图格式漂移再参数化 `SUBPROJECTS` 增加 `26.3/fabric`。
4. 若未来升 26.4：留意 `TreeGrower`/BER 管线继续演进，本轮 API 摘要可直接对照适配。

---

# port/26.3 进度笔记（2026-10-05 下午：headless 服务端实机验证）

## 验证环境

- `./gradlew runServer`（loom 1.18.2，运行目录 `26.3/fabric/run/`，已在 .gitignore）
- server.properties：level-type=flat、online-mode=false、level-name=test、enable-rcon=true
  （RCON 25575/cbport-test，注入命令用；关服一律 stdin 注入 stop，见纪律）
- 命令注入：`scripts/rcon_26.3.py`（备用通道；say 标记要从 `run/logs/latest.log` 读）
- addon jar 拷入 `run/mods/` 实现双 mod 共服

## 验证结论（全部通过）

| 项 | 结论 |
|---|---|
| 启动 | 干净启动无 FATAL/Exception；45 mods（双 mod + fabric-api）；Done 0.7s |
| 数据包 | `/datapack list`：vanilla + compressedblocks + compressedblockspot + fabric 2 个，全部启用 |
| selfTest（SERVER_STARTED 自动跑） | PASS：blocks=1315 items=1549（90 工具/72 盔甲/60 食物）；盆栽流程/刷石机/容器/潜影盒掉落/兼容金属审计全过 |
| 树特征 | `/place feature` 126 个 JSON 全部放置成功（9 木×9 级+变体/mega） |
| 树苗生长 | 7 木实机观测长成压缩树（acacia/jungle/cherry/birch 探测 GREW；oak/spruce/mangrove growTree=true 日志）。**教训：randomTickSpeed=3 下 12000 tick 期望只有 ~1.3 次生长尝试，"没长"多为抽样不足假象，测前先 `gamerule randomTickSpeed 1000`** |
| 方块实体 | 主 mod pot/hopper_pot、addon compressed_pot/compressed_hopper_pot：放置存活 + BE 数据正确（id/data get 均对） |
| 压缩箱子 | compressed_chest BE 正常（Items 空容器） |
| 刷石机 | 3x 机 BE ticker 实测产出：sprint 600 后 Buffer=59×2x_cobblestone、Progress 走字（产出逻辑+暂存+上限正确） |
| 压缩耕地 | 1x_farmland 放置存活（canSurvive 底座逻辑正常工作——强放在草方块上会被弹出，设计如此） |
| 战利品表 | `/loot spawn` 主 mod 1x_stone/1x_oak_log 表解析+掉落正确 |
| mixin | 三注入点（AnvilMenu/BlockBehaviour/ServerPlayerGameMode）required=true 无失配崩；selfTest 里 Class.forName 主动加载三目标类成功（headless 下把"命中"变成可验证事实） |
| access widener | 日志无任何 AW 警告 |
| tick sprint | 9482 tps / 0.11 ms per tick，无报错 |

## 发现并处理的问题

1. **树苗"不生长"（前两轮误报，非缺陷）**：oak/spruce/mangrove 连续两轮 NOT，
   插桩 advanceTree 后证实 growTree=true——是随机刻期望尝试次数过低（见上表教训）+ 长成的
   树冠压住探测位。无代码改动。
2. **addon jar 版本错位（已修正并复验）**：`run/mods/` 里曾放入 10:03 旧构建（战利品表还是转换中途的
   嵌套错误格式 `"conditions":[{"condition":...}]`，被原版静默容忍不报错），已换成 10:46 正确
   构建（pool 条件为 26.3 单数 `"condition":{...}`，与原版 flower_pot.json 对照一致）。
   换 jar 重启后 addon 专项复验通过：双盆栽放置存活、BE id `compressedblockspot:pot` 正确、
   `/loot spawn` 两个 addon 战利品表实测正确掉落 Compressed Pot / Compressed Hopper Pot，
   最终一轮日志 0 error/exception/parse-failure。
   **教训：改完资源必须重新拷 jar 到 run/mods 再验证。**
3. **22 兼容金属配方 WARN（预期行为，不修）**：`#c:brass_ingots` 等依赖第三方 mod 的 common
   tag，纯净环境 "empty ingredients will be ignored" 属设计跳过，与 1.21.11 正式线写法逐字节一致。
4. **26.3 setblock 语义备忘**：对已是同状态的方块重复 setblock 会报 "Could not set the block"
   （no-op 失败），不是放置缺陷。

## 静态核对（对照 26.3 原版 jar 逐格式确认）

配方（shaped/key 直接物品 id）✓；树特征（type 顶层并列、`below_trunk_provider`、`id/properties`、
provider 类型键剥 `_state_provider/_block_provider` 后缀）✓；战利品表（pool 级单数 `condition`
对象；entry 级支持字符串简写）✓；配方解锁 advancement（`conditions.recipes` 单数资源 id、
`items` 字符串简写）✓。

## 剩余待人工客户端验证（headless 验证不了，不臆造）

- 创造栏图标与物品渲染、方块贴图观感
- 盆栽内植物/压缩盆栽的 BER 渲染（MovingBlockRenderState 新管线）
- 滚动容器（243 格压缩箱/潜影盒）Screen UI（GuiGraphicsExtractor extract 管线）
- 树叶染色/树苗实际观感、压缩甘蔗渲染
- mixin 三注入点在**客户端**路径（AnvilMenu 服务端侧已验证）的行为

