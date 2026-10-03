# 夜间维护审批报告（2026-10-03 23:00 班次）

流程：审查员（反方）全面代码审批 → 测试员（正方）实证测试与逐条辩论验证 → 主控裁决 → 仅维护级修复 → 全量回归。

## 测试基线（修复前，全部通过）

- 构建：fabric / neoforge / addon-fabric / addon-neoforge 全部 BUILD SUCCESSFUL
- 双 loader 核心类字节一致：PASS（50 个 class，排除 loader 专属包）
- SELF-TEST：PASS（blocks=1315 items=1549）
- E2E：ALL PASS
- 资源生成器幂等：PASS（gen_resources 重跑零改动）

## 辩论裁决

| 编号 | 反方（审查员）主张 | 正方（测试员）验证 | 裁决 |
|---|---|---|---|
| P1-1 | neoforge 附属 SpotPotBlockEntity 缺 preRemoveSideEffects，破坏盆栽吞土壤+作物 | **确认**（无任何兜底路径；BlockEntity 默认实现因不 implements Container 而为 no-op） | ✅ 修 |
| P1-2 | 附属盆栽收割传 age=0 状态进带 age 条件的战利品表 → 成熟收获只得种子 | **确认**（1x_wheat_plant.json 产物条目带 age=7 条件；种植存 defaultBlockState） | ✅ 修 |
| P1-3 | 潜影盒放置时不从 CONTAINER 组件还原 243 格 | **证伪**（BaseContainerBlockEntity.applyImplicitComponents 默认就处理 CONTAINER，字节码为证；审查员把 API 记反） | ❌ 撤销，留档 |
| P2-1 | insertInto 部分插入不回滚仍返回 false → 冷却重试重掷产量表重复产出 | **确认**（逐叠早退 return false 无回滚；ITEM_SINK 路径无预模拟） | ✅ 修 |
| P2-2 | Item Scroller 黑名单反射可能未命中/未捕获 RuntimeException | **反射可命中**（GUI_BLACKLIST 在顶层 Configs、HashSet 可变、全限定名匹配） | ❌ 不修，留档 |
| P3×7 | 字节一致两处漂移 / 过时注释×4 / 悬空标签 / setChanged 每 tick / 滚动边界 | 逐条核实成立 | ✅ 修（见下） |
| P3×2 | 附属设计差异未文档化 / e2e 覆盖缺口（附属无 e2e、潜影盒放置回读断言缺失） | 成立 | 📋 留档 |

## 已修复（本轮提交）

1. **[附属·neoforge] SpotPotBlockEntity 补 preRemoveSideEffects 覆写**——neoforge 上破坏盆栽不再吞土壤与作物（P1-1）。
2. **[附属·双 loader] drops() 收割按满龄状态结算战利品表**——压缩作物盆栽成熟收获恢复正常产物+时运（P1-2）。
3. **[主 mod·双 loader] insertInto 语义改为"有进展即成功"**——部分插入不再触发重掷产量表，消除储物抽屉场景的重复产出（P2-1）。
4. **[附属·双 loader] insertIntoBelow 返回插入数量**——玩家收割保持"全塞下才入箱否则掉地上"；自动收割部分插入算成功、全没插进才等待（P2-1 附属侧）。
5. **[双 loader] 盆栽 serverTick setChanged 节流为每 20 tick**——大量盆栽时显著减少区块存盘写放大。
6. **[双 loader] 字节一致漂移归零**——CobblestoneGeneratorBlockEntity 注释、CompressedShulkerRenderer import 顺序统一。
7. **[gen_resources.py] 补生成 c:storage_blocks/compressedblocks 子标签**——消除启动时悬空标签警告；父标签引用自此完整。
8. **[双 loader] 滚动界面滚轮仅在面板区域内翻页**；轨道点击翻页后抓取偏移夹进滑块（防拖动跳变）。
9. 注释修正×4：51 材料（原写 43）、自动收割冷却与 gameTime 无关、gen_resources 的 onRemove→preRemoveSideEffects、e2e 物品计数 711→1549。

## 留档（不修，后续参考）

- P1-3 教训：审查结论需字节码级验证，原版 BaseContainerBlockEntity 默认处理 CONTAINER/CUSTOM_NAME/LOCK。
- 附属与主 mod 的刻意差异：附属无压缩土壤翻倍、土壤族无下界系（下界岩填不进附属盆）；如需对齐另开任务。
- e2e 覆盖缺口：附属 mod 无 e2e/selfTest；方块放置矩阵不含 hopper_pot/compressed_chest/compressed_shulker_box；建议后续补"带内容潜影盒破坏→放置→内容回读"断言（放置链已由正方字节码验证为通）。
- Item Scroller 配置重载（Configs.load）会 clear 黑名单，运行中重载可冲掉自动加的条目；界面每次打开 init() 会重新 add，天然自愈。
- 漏斗盆栽 fitsInto 模拟不含 canPlaceItem 检查（与实插存在理论不对称），实际影响未观测到。

## 回归结果（修复后）

- 资源生成：gen_resources 重跑 → 新增 c/tags/item/storage_blocks/compressedblocks.json（×2 loader），计数 1315/1549 不变
- 构建：fabric / neoforge / addon×2 全部 BUILD SUCCESSFUL
- 双 loader 核心类字节一致：PASS（重建 jar 后复核）
- SELF-TEST：PASS（blocks=1315 items=1549）
- E2E：ALL PASS
- 启动日志确认：c:storage_blocks/compressedblocks 悬空标签警告已消失
