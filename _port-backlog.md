# 压缩方块 mod 多版本移植 backlog

> 分仓策略（用户定稿）：`main` 分支保持 1.21.11 正式线（代码绝不改动）；所有移植版本都在本仓库内的长期分支 `port/<版本>` 上进行，每版本使用独立 git worktree（`O:/clawwork/chuansongmen/cb-port-<版本>`），版本代码与 main 彻底隔离，等效于从组织主线中分出。
> `main` 上只提交本文件的路线图/状态镜像，代码一律不进 main。各版本的详细进度笔记在对应 `port/<版本>` 分支上的本文件中维护（此处的笔记为摘要镜像）。

## 版本路线图与状态（2026-10-04 本轮后）

| 分支 | 目标 MC 版本 | 加载器 | 状态 | worktree | 备注 |
|---|---|---|---|---|---|
| port/26.3 | 26.3 "Wilderness Bound"（26.x 新版本线；2026-09-15 发布，当时最新 stable；26.60 于 10-27 发布） | Fabric（Loader 0.19.5 / API 0.161.0+26.3 / loom 1.18.2 / Gradle 9.7.1 / Java 25 toolchain） | 进行中-进度笔记 | O:/clawwork/chuansongmen/cb-port-26.3 | ①~⑥ 全量移植完成，主 mod+附属 gradle build 通过；待实机运行验证 |
| port/1.16.5 | 1.16.5 | Forge 36.2.39（Architectury Loom 1.3.358 + Gradle 8.8 + official mappings + toolchain JDK8） | 已完成 | O:/clawwork/chuansongmen/cb-port-1.16.5 | ①~⑥ 全部完成，gradle build 全绿，jar 4.1MB/10654 条目；功能降级清单见分支笔记 |
| port/1.12.2 | 1.12.2 | Forge 14.23.x | **已完成** | （本 worktree） | ①~⑥ 全部完成，gradle build 全绿（见下方进度笔记） |
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
- Gradle 缓存统一：`GRADLE_USER_HOME=O:/clawwork/chuansongmen/.gradle-home`（1.16.5 线已把代理 systemProp 写在该共享 gradle.properties，不进仓库）。
- 网络直连失败时用代理 `http://127.0.0.1:7897`；git push 先试 `git -c http.proxy= push`，失败改用 `git -c http.proxy=http://127.0.0.1:7897 push`。
- 纪律：写源码一律用 Write/Edit 工具；绝不 taskkill 全部 java；不部署任何东西到 H:\Minecraft；不新建/删除定时任务；不动 main 分支代码。

## 进度笔记镜像（详情见各分支上的 `_port-backlog.md`）

### port/1.12.2（2026-10-05，已完成）

#### 已完成阶段（①~⑥ 全部）

1. **① 核心 36 材料×9 储存方块 + 22 兼容金属 × 9**：注册/贴图/模型/配方（3×3 压缩与无序解压链、挖掘档位随重数上浮）。材料集 36 种 = 1.16.5 的 39 种再剔除 玄武岩/黑石（1.16 内容）/蓝冰（1.13 内容）。兼容金属 1x 配方走 `forge:ore_dict`（blockTin/blockLead/…，OreDictionary 惯例名），并注册 1x 金属块到对应 ore 名。
2. **② 树叶/树苗(6 木)/甘蔗(37 线)/耕地/作物**：1.12.2 无 configured feature——压缩树苗走**代码 WorldGenerator**（CompressedTreeGen：直干+球形树冠，干叶用同重数压缩原木/树叶）；树叶走原版凋落算法（Forge beginLeavesDecay）+ `canRenderInLayer` 透明层（1.12.2 无模型 render_type 字段）；耕地 9 级（锄压缩泥土需对应重数锄头）；作物 4 种 × 3 级。
3. **③ 工具/盔甲/食物**：1.12.2 无 Tier/ArmorMaterial 接口——`EnumHelper.addToolMaterial`（2 线 × 9 重）与 `addArmorMaterial`（2 线 × 9 重）动态枚举；伤害公式与 1.16.5 同源（石线 pick=3×1.6^n−1、木线 2×1.6^n−1；material damage=总伤−1，剑/锹自动落位，斧用 `ItemAxe(mat,damage,speed)` 三参构造器直传规避枚举索引越界）；7/8/9 重=铁/钻/下界合金档、6 重起耐久 MAX；护甲防御每重+1 封顶 10、溢出转抗性提升、8 重飞行、九重满套免疫一切伤害+饱食常满（Forge 事件桥）；食物 18 种 × 3 级（营养 ×9^n、溢出转回升）。
4. **④ 压缩箱子/潜影盒（243 格滚动容器）+ 刷石机 ×3**：1.16.5 的"固定槽位映射+客户端同序重建槽位列表"滚动方案直接平移（1.12.2 Slot 坐标同样 final）；GUI 走 `IGuiHandler` + `player.openGui`（1.12.2 无 MenuType/NetworkHooks）；箱子破坏洒内容、潜影盒内容随 BlockEntityTag 物品走；刷石机 `ITickable`、六向优先级压箱。
5. **⑤ 盆栽家族**：pot/hopper_pot（Botany Pots 机制：土壤族掩码/土壤等级提速 5%/成熟收割自动补种/斧头拔出/漏斗盆栽自动压箱补种）。
6. **⑥ 附属 mod compressedblockspot**：**同 jar 双 mod**（mcmod.info 双条目）；压缩盆栽/压缩漏斗盆栽（SpotPotBlock 复用主 mod 盆栽 BE，SPOT_UNLOCKED 门控），配方 = 5× 一重压缩方块船形（compressedblocks:pot_material 数据包标签）+ 漏斗无序。

#### 构建工具链最终选型

- **RetroFuturaGradle 2.0.6 + Gradle 9.8.0（wrapper）+ toolchain JDK8（temurin-8 共享缓存）**，Forge 14.23.5.2847（RFG 默认）、MCP 数据 = forge maven 的 mcp-1.12.2-srg.zip + mcp_stable-39 csv。
- **RFG 2.0.6 是 Java 25 字节码**：Gradle 守护进程必须跑在 JDK25 上——`org.gradle.java.home` 指向共享缓存 corretto-25（项目 gradle.properties 内记录，换机器需改路径）。Gradle 8.8 连 RFG 插件 jar 都无法 instrument（class file major 69）。
- **MCP 数据源阻塞点**：`maven.mcmod.dev` 本机不可达（代理 7897 也已失效），forge maven 只有 `mcp-1.12.2-srg.zip`（含 joined.srg/joined.exc/exceptor.json/patches）而无 `mcp_stable-39-1.12.2.zip`——手动解包喂到 `GRADLE_USER_HOME/caches/minecraft/de/oceanlabs/mcp/mcp_stable/39/`（csv 从历史缓存的 39-1.12 zip 取，内容同 1.12.2）。**换机器首次构建需重做此步**（或恢复 mcmod.dev 连通）。
- 构建产物：`1.12.2/forge/build/libs/compressedblocks-forge-1.12.2-0.1.0.jar`（3.0MB，8406 条目：约 3253 blockstates/models、2684 配方、58 类、双 mod mcmod.info）。
- 首轮编译错误 64 个清零（API 名差异：`ChatFormatting→TextFormatting`、`CreativeTabs.createIcon`、`Material.PLANTS`、`EnumFacing.byIndex`、ResourceLocation 无 domain/path 访问器、`Items.COBBLESTONE/PUMPKIN` 1.12.2 不存在、`Blocks.PODZOL` 是 DIRT 的 meta、IInventory 需 isEmpty/getInventoryStackLimit、GuiScreen 无 mouseX/mouseY 字段、protected setSoundType 走 `BlockAccessors` 反射等）。

#### 资源生成器（scripts/）

- `gen_1122_assets.py`：贴图 1071 方块 + 303 物品 + 36 盔甲层（textures/blocks、textures/items 复数目录）+ icon；`--src-root`（默认主仓库 _asset-src，只读）与 `--out-root` 参数化。
- `gen_1122_resources.py`：blockstate/模型/配方/loot_tables/tags/.lang 全量 1.12.2 格式（991 方块 / 1219 物品 / 2684 配方）。
- `gen_1122_addon_assets.py`：附属盆栽贴图加深 + 模型/配方/loot/.lang。

#### 1.12.2 stable_39 映射与 1.16.5 official 的主要差异（实测记录）

- 注册：`RegistryEvent.Register<Block/Item>`（MOD 构造器先建实例；1.16.5 是构造期注册表）；TE 走 `GameRegistry.registerTileEntity` + `ITileEntityProvider`；GUI 走 `IGuiHandler` + `player.openGui`（无 MenuType）。
- 方块属性是构造器 setter 链（无 Block.Properties）：`setHardness/setResistance(×3 语义)/setSoundType(protected)/setLightLevel(0-1 比例)/setHarvestLevel`；爆炸抗性 1200 → setResistance(400)。
- 材质/模型：blockstates 变体键 `normal`（无属性方块）；树叶 decayable/check_decay 4 变体；甘蔗 age 0..15；作物 age 0..7；**lang 是 .lang 键值**（`tile.<ns>.<name>.name`）；pack_format 3；物品模型需 Java 侧 `ModelLoader.setCustomModelResourceLocation` 逐个注册（ModelRegistryEvent）。
- 物品：log/log2 是 metadata 方块（配方带 `"data": N`）；无 #forge 标签生态（兼容金属走 OreDictionary）；`RandomizableContainerBlockEntity→TileEntityLockableLoot`；`CropBlock` 抽象 getSeed/getCrop；`ItemFood` 用 onFoodEaten；药水常量 `MobEffects.RESISTANCE/HASTE/...`；飞行走 capabilities.setFlySpeed。

#### 相对 1.16.5/1.21.11 的功能降级（backlog，后续可补）

1. **渲染**：盆栽无 BER（盆内植物不可见）；压缩箱子/潜影盒无开盖动画 BER（整块渲染，开关音保留）——1.16.5 同款降级。
2. **九重工具挖基岩**：需 mixin（MixinBootstrap/SpongeMixin），本轮不引入，未实现。
3. **压缩树苗**：无骨粉分级加速（自写 BlockBush，无原版 stage 属性），骨粉直接长树；树形统一 blob 树冠。
4. **树叶 loot**：简化版（剪刀/丝触掉自身 + 5% 树苗 + 2% 木棍），走 Block.getDrops + harvesters ThreadLocal。
5. **木线工具配方**：1.12.2 标签合成生态弱——按 6 原木展开独立配方（_from_<wood> 后缀，270 条）；石甲两套（石头/圆石来源）。
6. **盆栽收割表**：1.12.2 无竹子/甜果丛/火把花；西瓜/南瓜收获的是方块形态（1.13+ 才有独立物品）。
7. **配方解锁 advancement 全部跳过**（1.12.2 合成/JEI 不受影响）；1.16.5 线的同类降级继续有效（附属同 jar、无运行时自检、未做游戏内冒烟测试）。

#### 提交索引（本轮）

- port/1.12.2：`cb653ca9` 构建骨架、`9d328ae8` 资源生成器、`337aa39c` ①~⑥ 全量源码+资源（待 push origin/port/1.12.2）

### port/26.3（2026-10-04，进行中-进度笔记）
- ①~⑤ 主 mod 全量移植：43×9 储存方块、22 兼容金属、树叶/树苗/耕地/作物/压缩甘蔗（126 个树特征 JSON 由新脚本 `scripts/gen_assets_26.py` 转换）、盆栽+漏斗盆栽、刷石机×3、243 格滚动容器、工具/盔甲/食物；⑥ 附属 `26.3/addon/fabric/` 独立子项目。
- 构建：`26.3/fabric` 与 `26.3/addon/fabric` 均 BUILD SUCCESSFUL（compressedblocks-fabric-26.3-0.3.2.jar / compressedblockspot-fabric-26.3-0.3.2.jar）；编译错误从首轮 174 个清零（1.21.11→26.3 API 漂移：26.1+ 去混淆无 mappings、GuiGraphics 删除改 extract 管线、BER SpriteId/SpriteGetter、TreeGrower 加权列表、工具类工厂等）。
- 版本依据：meta.fabricmc.net 唯一 26.x stable=26.3；26.60 当时未发布。
- 待办：实机运行验证（树生成/盆栽渲染/滚动容器交互/mixin 注入点命中）；换机器需删 gradle.properties 里的 `org.gradle.java.home` 触发 foojay 重下 JDK25。

### port/1.16.5（2026-10-04，已完成）
- ①~⑥ 全部完成：39 材料×9（剔除 12 种 1.17+ 方块）+22 金属、6 木×9 树叶树苗+甘蔗 40 线/耕地/作物、工具盔甲食物（Forge 事件桥满套免疫）、243 格滚动容器+刷石机、盆栽家族、附属同 jar 双 mod。
- 构建：FG6 在 1.16.5 管线不完整（已绕过），最终选型 **Architectury Loom 1.3.358 + Gradle 8.8 + official mappings + foojay JDK8 toolchain**，BUILD SUCCESSFUL（compressedblocks-forge-1.16.5-0.3.0.jar）；三个新 Python 生成器（gen_1165_assets/resources/addon_assets.py）产出 1.16.5 全格式资源（1045 方块/1273 物品/2330 配方/993 loot）。
- 已知降级（详见分支笔记）：盆栽与箱子无 BER 渲染、九重工具挖基岩需 mixin 未做、树叶 loot 简化、附属非独立子项目、未做游戏内冒烟测试。

### 提交索引（本轮）
- port/26.3：`f10d0563` 主 mod ①~⑤、`654dc8fa` 附属 ⑥、`e26f26fa` backlog（已 push origin/port/26.3）
- port/1.16.5：`50239152` 工具链+①~④、`6521f37f` ⑤+jar、`c1839bbc` ⑥+backlog（已 push origin/port/1.16.5）
