# 多版本移植 backlog（分仓=仓库内多版本分支）

> 策略（用户定稿）：main = 1.21.11 正式线不动；每版本一条 port/<版本> 长期分支；子代理用独立 git worktree 并发（O:/clawwork/chuansongmen/cb-port-<版本>）。

## 版本路线图与状态

| 分支 | 目标版本 | 状态 | 备注 |
|---|---|---|---|
| port/26.x | 最新正式版 | 未开始 | 优先；以实际可查的最新 MC 版本为准 |
| port/1.16.5 | 1.16.5 Forge | **已完成** | ①-⑥ 全部完成，gradle build 全绿（见下方进度笔记） |
| port/1.12.2 | 1.12.2 Forge | 未开始 | Forge 14.23.x，需 toolchain JDK8 |
| port/1.7.10 | 1.7.10 Forge | 未开始 | Forge 10.x，需 toolchain JDK8 |

---

## port/1.16.5 进度笔记（2026-10-04）

### 已完成阶段（①~⑥ 全部）

1. **① 核心 39×9 储存方块 + 22 兼容金属 × 9**：注册/贴图/模型/配方（压缩 3×3 与解压无序链、挖掘档位随重数上浮）。材料集 39 种 = 1.21.11 的 51 种剔除 12 种 1.17+ 方块（深板岩系/铜块/红树樱花苍白原木/方解石/凝灰岩/钟乳石/紫水晶/苔藓/泥巴）。
2. **② 树叶/树苗(6 木)/甘蔗/耕地/作物**：6 木 = 剔除红树/樱花/苍白橡树；压缩树苗生长走**代码注册 configured feature**（BlobFoliagePlacer + StraightTrunkPlacer，树叶 distance=7 自动凋落）；甘蔗 40 线 × 9（8 甘蔗 + 核心方块居中配方）；耕地 9 级（锄压缩泥土需对应重数锄头）；作物 4 种 × 3 级（只能种在等级足够的压缩耕地上）。
3. **③ 工具/盔甲/食物**：工具 2 线 × 5 类 × 9 重（伤害 ×1.6^重、速度阶梯、7/8/9 重=铁/钻/合金档、6 重起不可破坏）；护甲石/木各 4 件 × 9 级（溢出防御转抗性提升、8 重飞行、九重满套免疫一切伤害+饱食常满，Forge 事件桥）；食物 18 × 3（营养 ×9^重、溢出转回升）。
4. **④ 压缩箱子/潜影盒（243 格滚动容器）**：27×9 格 BE（潜影盒内容随物品走 BlockEntityTag、箱子破坏洒内容）、Menu 槽位固定映射 + 客户端同序重建槽位列表式滚动（1.16.5 Slot 坐标是 final）、Screen blit 原版 generic_54 + 自绘滚动条、右键 NetworkHooks.openGui、刷石机 ×3（TickableBlockEntity、六向优先级压箱）。
5. **⑤ 盆栽家族**：pot/hopper_pot（Botany Pots 机制：土壤族掩码/土壤等级提速 5%/成熟收割自动补种/斧头拔出/漏斗盆栽自动压箱补种）。
6. **⑥ 附属 mod compressedblockspot**：1.16.5 与主 mod **同 jar 双 mod**（mods.toml 两个 [[mods]]）；压缩盆栽/压缩漏斗盆栽（SPOT_UNLOCKED 门控解锁压缩系种植），配方 = 5× 一重压缩方块船形（#compressedblocks:pot_material）/ + 漏斗无序。

### 构建工具链最终选型

- **Architectury Loom 1.3.358 + Gradle 8.8（wrapper）+ official (Mojang) mappings + Java 8 toolchain（foojay-resolver 自动下载 Temurin 8）**，Forge 1.16.5-36.2.39。
- 本机只有 JDK21：Gradle 本体跑在 21 上，编译用 toolchain JDK8（`java.toolchain.languageVersion = 8`）。
- **网络**：JDK8 下载与依赖解析需要代理——已写入共享 `O:/clawwork/chuansongmen/.gradle-home/gradle.properties`（systemProp http/https proxy 127.0.0.1:7897，**不进仓库**）。
- 构建产物：`1.16.5/forge/build/libs/compressedblocks-forge-1.16.5-0.3.0.jar`（4.1MB，10654 条目：1046 blockstates、2330 配方、993 战利品表、1454 贴图、51 类、双 mod mods.toml）。

### 资源生成器（scripts/）

- `gen_1165_assets.py`：贴图 1107 方块 + 303 物品 + 36 盔甲层（1.16.5 盔甲层走 textures/models/armor/<材质>_layer_N.png）+ icon；`--src-root`（默认主仓库 _asset-src，只读）与 `--out-root` 参数化，预览不写主仓库。
- `gen_1165_resources.py`：blockstate/模型/配方/loot_tables/advancements/tags/lang 全量 1.16.5 格式（1045 方块 / 1273 物品）。
- `gen_1165_addon_assets.py`：附属盆栽贴图加深 + 模型/配方/loot/lang。

### 阻塞点与关键教训

1. **ForgeGradle 6 在本机管线不完整**（路线 a 失败）：FG6 插件解析/mappings 配置成功，但 1.16.5 的 MC 处理管线未产出含 net.minecraft 类的 mapped jar（仅 forge 类 7670 条目，无 decompile/patch/recompile 产物）→ 编译期"程序包 net.minecraft 不存在"。已绕过：切 Architectury Loom（路线 b）。
2. **Loom 插件需要 4 个仓库**：gradlePluginPortal + maven.minecraftforge.net + maven.architectury.dev（loom 插件标记）+ maven.fabricmc.net（loom 的 fabricmc 依赖 stitch/mapping-io）。
3. **gradle.properties 必须 `loom.platform=forge`**。
4. **JDK8 目标 = 纯 Java 8 语法**：switch 箭头/instanceof pattern/var 全部不可用（首两轮编译错误全源于此）。
5. 1.16.5 official 映射与 1.21.x 的主要差异（对照 client_mappings.txt 校准）：`Container→AbstractContainerMenu`、`TileEntity→BlockEntity`、`EquipmentSlotType→EquipmentSlot`、`net.minecraft.item→net.minecraft.world.item`、`Component.lateral→new TextComponent/TranslatableComponent`、`playerWillDestroy` 返回 void、`SimpleStateProvider`（非 SimpleBlockStateProvider）、`UniformInt.of`（非 FeatureSpread）、`BuiltinRegistries`（非 WorldGenRegistries）、`AbstractTreeGrower.getConfiguredFeature` 返回 `ConfiguredFeature<TreeConfiguration,?>`、ticker 走 `TickableBlockEntity.tick()`（无 BlockEntityTicker）、`Slot.x/y` 是 final、推动反应由 Material 决定（无 Properties.pushReaction）、IContainerFactory（Forge）带 PacketBuffer 而 MenuSupplier 只有两参。

### 相对 1.21.11 的功能降级（backlog，后续可补）

1. **渲染**：盆栽无 BER（盆内植物不可见，功能正常）；压缩箱子/潜影盒无开盖动画 BER（整块渲染，开关音保留）。
2. **九重工具挖基岩**：需 mixin（破坏进度客户端插值）未实现。
3. **树叶战利品表**：简化版（剪刀/丝触掉自身 + 5% 树苗 + 2% 木棍），未做 1.16 时运曲线逐字节镜像。
4. **树形**：6 木统一 blob 树冠 + 直干（原版 acacia/dark_oak 特征树形未复刻；深色橡树无 2×2 四苗要求，单苗可长）。
5. **附属打包**：同 jar 双 mod（1.21.11 线是独立子项目）；附属盆栽未做独立创造栏（归入主 mod 压缩工具栏）；附属无独立模组图标。
6. **兼容金属**：1x 配方引用 `#forge:ingots/<key>` 空标签——需对应金属 mod 合并标签或手动补充才可用（9x↔1x 升降级链不受影响）。
7. **更多压缩种子**：只有南瓜/西瓜 ×3（1.16.5 无火把花/瓶子草）。
8. **滚动容器**：滚轮翻页为槽位列表重建式（协议零感知已验证设计，但未做 Item Scroller GUI 黑名单反射注入）。
9. **无运行时自检**（1.21 有 ServerStarted selfTest）与启动日志断言。
10. **未做游戏内冒烟测试**（E2E）：注册/模型/配方仅静态校验（生成器断言 + 编译），未启动客户端/服务端实测。

### 下一步建议

1. 客户端冒烟测试：进游戏抽查创造栏、压缩方块渲染、滚动容器翻页、盆栽种植收割、附属压缩盆栽配方。
2. 补 BER 渲染（盆栽植物 + 箱子/潜影盒开盖动画，1.16.5 TileEntityRenderer）。
3. mixin 实现九重工具挖基岩。
4. 树叶时运曲线与原版树形精修。
