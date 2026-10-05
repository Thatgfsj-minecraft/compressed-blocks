# 压缩方块 mod 多版本移植 backlog

> 分仓策略（用户定稿）：`main` 分支保持 1.21.11 正式线（代码绝不改动）；所有移植版本都在本仓库内的长期分支 `port/<版本>` 上进行，每版本使用独立 git worktree（`O:/clawwork/chuansongmen/cb-port-<版本>`），版本代码与 main 彻底隔离，等效于从组织主线中分出。
> `main` 上只提交本文件的路线图/状态镜像，代码一律不进 main。各版本的详细进度笔记在对应 `port/<版本>` 分支上的本文件中维护（此处的笔记为摘要镜像）。

## 版本路线图与状态（2026-10-05 第二轮后）

| 分支 | 目标 MC 版本 | 加载器 | 状态 | worktree | 备注 |
|---|---|---|---|---|---|
| port/26.3 | 26.3 "Wilderness Bound"（26.x 新版本线；2026-09-15 发布，当时最新 stable；26.60 于 10-27 发布） | Fabric（Loader 0.19.5 / API 0.161.0+26.3 / loom 1.18.2 / Gradle 9.7.1 / Java 25 toolchain） | 进行中-进度笔记 | O:/clawwork/chuansongmen/cb-port-26.3 | ①~⑥ 全量移植完成，build 通过；**headless 服务端实机验证通过**（2026-10-05：自检/树特征/盆栽/刷石机/战利品表/mixin 全过，真缺陷 1 个已修）；剩客户端渲染/UI 待人工客户端验证 |
| port/1.16.5 | 1.16.5 | Forge 36.2.39（Architectury Loom 1.3.358 + Gradle 8.8 + official mappings + toolchain JDK8） | 已完成 | O:/clawwork/chuansongmen/cb-port-1.16.5 | ①~⑥ 全部完成，gradle build 全绿，jar 4.1MB/10654 条目；功能降级清单见分支笔记；**待修复：18 种压缩食物未注册（1.12.2 轮发现的跨线缺陷）** |
| port/1.12.2 | 1.12.2 | Forge 14.23.5.2847（RetroFuturaGradle 2.0.6 + Gradle 9.8 + toolchain JDK8） | **已完成** | O:/clawwork/chuansongmen/cb-port-1.12.2 | ①~⑥ 全部完成，gradle build 全绿，jar 3.0MB/8406 条目；MCP 数据需手动喂缓存（见笔记） |
| port/1.7.10 | 1.7.10 | Forge 10.x | 未开始 | （尚未建） | 排队中；可直接复用 1.12.2 线的 RFG 2.0.6 工具链与 MCP 缓存手法（RFG 同样支持 1.7.10） |

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
- Gradle 缓存统一：`GRADLE_USER_HOME=O:/clawwork/chuansongmen/.gradle-home`（代理 systemProp 已写在该共享 gradle.properties，不进仓库；jdks/ 内已缓存 temurin-8 与 corretto-25）。
- 网络直连失败时用代理 `http://127.0.0.1:7897`；git push 先试 `git -c http.proxy= push`，失败改用 `git -c http.proxy=http://127.0.0.1:7897 push`。
- 纪律：写源码一律用 Write/Edit 工具；绝不 taskkill 全部 java；不部署任何东西到 H:\Minecraft；不新建/删除定时任务；不动 main 分支代码。

## 进度笔记镜像（详情见各分支上的 `_port-backlog.md`）

### port/1.12.2（2026-10-05，已完成）
- ①~⑥ 全部完成：36 材料×9（=1.16.5 的 39 种再剔玄武岩/黑石/蓝冰）+22 兼容金属（OreDictionary）、6 木树苗走代码 WorldGenerator+甘蔗 37 线/耕地/作物、工具盔甲（EnumHelper 动态枚举、九重满套免疫）+食物 18×3（并补上物品注册）、243 格滚动容器（IGuiHandler 平移固定槽位映射方案）+刷石机×3、盆栽家族、附属同 jar 双 mod。
- 构建：**RetroFuturaGradle 2.0.6 + Gradle 9.8.0 + Forge 14.23.5.2847 + toolchain JDK8**，BUILD SUCCESSFUL（compressedblocks-forge-1.12.2-0.1.0.jar）；关键坑：RFG 2.0.6 是 Java 25 字节码，守护进程必须 JDK25（org.gradle.java.home 指向共享缓存 corretto-25）；**maven.mcmod.dev 不可达，MCP 数据（mcp_stable-39 csv + srg）需手动解包喂进 GRADLE_USER_HOME/caches/minecraft，换机器首次构建要重做**。
- 生成器：gen_1122_assets/resources/addon_assets.py 产出 1.12.2 全格式资源（991 方块/1219 物品/2684 配方/.lang 键值格式/pack_format 3）。
- 降级清单（详见分支笔记）：无 BER、无 mixin（九重挖基岩未做）、无骨粉分级、配方 advancement 跳过、未做游戏内冒烟测试。
- **跨线发现**：1.16.5 线的 18 种压缩食物有资源但物品未注册（1.12.2 移植时在自己线上补了注册），port/1.16.5 待修复。

### port/26.3（2026-10-04，进行中-进度笔记）
- ①~⑤ 主 mod 全量移植：43×9 储存方块、22 兼容金属、树叶/树苗/耕地/作物/压缩甘蔗（126 个树特征 JSON 由新脚本 `scripts/gen_assets_26.py` 转换）、盆栽+漏斗盆栽、刷石机×3、243 格滚动容器、工具/盔甲/食物；⑥ 附属 `26.3/addon/fabric/` 独立子项目。
- 构建：`26.3/fabric` 与 `26.3/addon/fabric` 均 BUILD SUCCESSFUL（compressedblocks-fabric-26.3-0.3.2.jar / compressedblockspot-fabric-26.3-0.3.2.jar）；编译错误从首轮 174 个清零（1.21.11→26.3 API 漂移：26.1+ 去混淆无 mappings、GuiGraphics 删除改 extract 管线、BER SpriteId/SpriteGetter、TreeGrower 加权列表、工具类工厂等）。
- 版本依据：meta.fabricmc.net 唯一 26.x stable=26.3；26.60 当时未发布。
- **实机验证通过（2026-10-05 第二轮，headless 专用服务端）**：45 mods 干净启动（0 error，Done 0.7~0.9s）；SERVER_STARTED 自检 PASS（1315 方块/1549 物品，盆栽流程/刷石机/容器/潜影盒掉落/兼容金属审计全过）；`/place feature` 126 个树特征 JSON 全部放置成功，7 种木树苗实机长树；刷石机 sprint 600 实测产出；`/loot spawn` 主 mod+addon 掉落正确；mixin 三注入点命中（required=true 无失配 + 目标类主动加载成功）、AW 无警告；3 次启停均经 stdin `stop` 干净退出。
- **真缺陷修复**：26.3 把 loot table pool 级条件从 `"conditions":[...]` 改为单数 `"condition":{...}`（addon 战利品表受影响，已修并重启复验）。新工具 `scripts/rcon_26.3.py` 入库（RCON 命令注入，后续版本线冒烟可复用）。教训：测树苗生长前调高 randomTickSpeed（默认 3 下 12000 tick 每苗期望仅 ~1.3 次尝试，属抽样假象）。
- 双项目 build 仍 BUILD SUCCESSFUL；提交 `eff5e7ac`（含上轮被用量上限中断遗留的 4048 文件工作）已推送。
- 剩余待人工客户端验证（headless 无法覆盖）：创造栏/物品渲染、盆栽 BER（MovingBlockRenderState 新管线）、243 格滚动容器 Screen UI、树叶染色/甘蔗观感、mixin 客户端路径行为。
- 换机器需删 gradle.properties 里的 `org.gradle.java.home` 触发 foojay 重下 JDK25。

### port/1.16.5（2026-10-04，已完成）
- ①~⑥ 全部完成：39 材料×9（剔除 12 种 1.17+ 方块）+22 金属、6 木×9 树叶树苗+甘蔗 40 线/耕地/作物、工具盔甲食物（Forge 事件桥满套免疫）、243 格滚动容器+刷石机、盆栽家族、附属同 jar 双 mod。
- 构建：FG6 在 1.16.5 管线不完整（已绕过），最终选型 **Architectury Loom 1.3.358 + Gradle 8.8 + official mappings + foojay JDK8 toolchain**，BUILD SUCCESSFUL（compressedblocks-forge-1.16.5-0.3.0.jar）；三个新 Python 生成器（gen_1165_assets/resources/addon_assets.py）产出 1.16.5 全格式资源（1045 方块/1273 物品/2330 配方/993 loot）。
- 已知降级（详见分支笔记）：盆栽与箱子无 BER 渲染、九重工具挖基岩需 mixin 未做、树叶 loot 简化、附属非独立子项目、未做游戏内冒烟测试。
- **待修复（2026-10-05 发现）**：18 种压缩食物的贴图/配方/lang 齐全但物品从未注册（1.12.2 轮移植时发现的跨线缺陷），下轮在 port/1.16.5 补注册并重跑 build。

### 提交索引
- port/26.3：`f10d0563` 主 mod ①~⑤、`654dc8fa` 附属 ⑥、`e26f26fa` backlog（已 push origin/port/26.3）；`eff5e7ac` 第二轮实机验证+loot 格式修复（已 push origin/port/26.3）
- port/1.16.5：`50239152` 工具链+①~④、`6521f37f` ⑤+jar、`c1839bbc` ⑥+backlog（已 push origin/port/1.16.5）
- port/1.12.2：`cb653ca9` 构建骨架、`9d328ae8` 资源生成器、`337aa39c` ①~⑥ 全量源码+资源、`3e32322a` backlog（已 push origin/port/1.12.2）
