# 0.4.0 发布说明

> 主 mod `compressedblocks` **0.4.0** + 附属 mod `compressedblockspot` **0.4.0** 同步发布。
> **两个 jar 必须成对更新**：只换其中一个会加载失败（依赖区间阻断）或出现方块/物品缺失。

关联 Issue：#76（内核）/ #77（附属 9 档）/ #78（主 mod 收敛）/ #79（附属 E2E + 发布说明）。

## 一、破坏性变更（升级前必读）

### 1. 旧 2x / 3x 压缩刷石机被移除

- 删除的方块与物品：`compressedblocks:2x_cobblestone_generator`、`compressedblocks:3x_cobblestone_generator`。
  连同配方、战利品表、挖掘标签、创造栏条目一并删除，**不做存档迁移、不保留 legacy 方块**。
- 已放置的旧机器、以及背包/箱子里的旧刷石机物品，升级加载后会变空气/消失（物品一并丢失，不返还材料）。
- **升级前请先拆掉旧压缩刷石机**（拆下来的旧物品在新版本同样无法使用，请先用掉或丢弃）。
- 替代方案：0.4.0 起 1~9 重压缩刷石机由附属 mod 提供（见第二节），重新合成即可。

### 2. 附属 mod 依赖区间收紧

- 附属 `compressedblockspot` 依赖主 mod `compressedblocks`：Fabric `>=0.4.0 <0.5.0`、NeoForge `[0.4.0,0.5.0)`。
- 旧附属 0.3.2 无法配主 mod 0.4.0（反之亦然）：混用会被 loader 直接拒绝加载。

### 3. 创造栏重组

见第三节：旧「压缩盆栽」栏（`compressedblockspot:pots`）删除，栏位总数由 4 变 5。

## 二、刷石机：主 mod 只留原版级，1~9 重迁入附属 mod

| 档位 | 方块 id | 归属 | 产物 | 周期 |
| --- | --- | --- | --- | --- |
| 原版级（tier 0） | `compressedblocks:cobblestone_generator` | 主 mod | `minecraft:cobblestone` | 20 tick / 个 |
| 1 重 | `compressedblockspot:1x_cobblestone_generator` | 附属 | `compressedblocks:1x_cobblestone` | 20 tick / 个 |
| 2 重 | `compressedblockspot:2x_cobblestone_generator` | 附属 | `compressedblocks:2x_cobblestone` | 20 tick / 个 |
| … | … | … | … | … |
| 9 重 | `compressedblockspot:9x_cobblestone_generator` | 附属 | `compressedblocks:9x_cobblestone` | 20 tick / 个 |

- **档位语义**：tier = 产物的压缩等级（0 = 原石）。N 重机器每 20 tick 产出 1 个 N 重压缩圆石，
  「更高档 = 同样时间产出更高压缩等级」本身就是效率提升，周期与旧版/原版保持一致（不做提速）。
- **复用主 mod 内核**：输出仍是防御式策略——下方容器优先（下、上、北、南、西、东），缓存目标失效
  （被拆/替换/红石锁死）自动重扫，当前方向放不进按优先级回退，六向都堵死且缓冲满时整组弹到机器上方；
  插入走原版漏斗搬运语义（尊重容器面限制与堆叠上限）。
- **合成与拆解（双向无损）**：
  - `1x_cobblestone_generator` = 9 × `compressedblocks:cobblestone_generator`（工作台 3×3）；
  - `Nx_cobblestone_generator`（N = 2~9）= 9 × `(N-1)x_cobblestone_generator`；
  - 每档拆解：`unpack_1x_cobblestone_generator` → 9 × 原版级刷石机；`unpack_Nx_...` → 9 × `(N-1)x`。
- **挖掘**：与原版级刷石机一致——进 `minecraft:mineable/pickaxe`（任意等级镐可挖），不进 needs_* 工具门。
- **视觉**：沿用主 mod 压缩方块视觉语言，以原版级刷石机贴图为底叠加压缩环（1~5 重黑/浅黑、6~8 重深紫/浅紫、9 重最外白细圈）。

## 三、创造物品栏：5 栏（主 mod 3 + 附属 mod 2）

| 顺序 | 栏 id | 归属 | 内容 |
| --- | --- | --- | --- |
| 1 | `compressedblocks:blocks` | 主 mod | 压缩方块（不变） |
| 2 | `compressedblocks:tools` | 主 mod | 压缩工具（不变） |
| 3 | `compressedblocks:food` | 主 mod | 压缩食物（不变） |
| 4 | `compressedblockspot:addon_blocks` | 附属 | **压缩拓展方块**：原「压缩盆栽」栏展示的主 mod 压缩甘蔗 / 压缩树苗物品 |
| 5 | `compressedblockspot:addon_tools` | 附属 | **压缩拓展工具**：压缩盆栽 ×2 + 压缩刷石机 ×9 |

- 主 mod 3 栏沿用 0.3.x 的原有注册顺序与位置（`blocks`=0 / `tools`=1 / `food`=2），本轮不改内容与位置；附属 2 栏排在 TOP 行第 4、5 位（`addon_blocks`=3 / `addon_tools`=4）。
- 旧的「压缩盆栽」栏（`compressedblockspot:pots`）删除；盆栽物品本身仍在「压缩拓展工具」栏，行为不变。

## 四、升级步骤

1. **备份存档**（重点：存档里可能有旧 2x/3x 刷石机）。
2. 进入存档**拆掉所有旧压缩刷石机**（2x / 3x），并清空背包/箱子里的旧刷石机物品。
   > 这一步可跳过，但跳过就意味着这些方块/物品在 0.4.0 加载后直接消失、材料不退。
3. 关闭游戏/服务器，把主 mod 与附属 mod **两个 jar 一起**替换为 0.4.0（删除旧 jar）。
4. 启动游戏；若旧附属未替换，loader 会因依赖区间不满足而报错（这是预期行为）。
5. 新的 1~9 重刷石机在创造栏「压缩拓展工具」中，或按第二节配方合成。

## 五、构建顺序（自建 / 开发）

附属 mod 通过 `compileOnly` 引用**主 mod 构建产物**，因此必须**先构建主 mod、再构建附属**：

```bash
# 先主 mod（Fabric / NeoForge 各自构建）
cd 1.21.11/fabric   && ./gradlew build
cd 1.21.11/neoforge && ./gradlew build

# 再附属 mod（依赖上一行主 mod 产出的 dev jar / 主 jar）
cd 1.21.11/addon/fabric   && ./gradlew build
cd 1.21.11/addon/neoforge && ./gradlew build
```

- 附属的 `gradle.properties` 用 `main_mod_version` 引用主 mod 版本，`build.gradle` 里 `version` 与之对齐；
  主 mod 未构建时附属编译会直接失败（不是配置错误，是构建顺序问题）。
- 资源生成：仓库里附属的 blockstate/模型/物品定义/配方/战利品表/语言/标签/贴图由 `scripts/gen_addon_assets.py`
  生成（同时产出 Fabric 与 NeoForge 两份）；改资源请改生成器后重跑，不要手改生成物。

## 六、验证与 E2E

| 层 | 命令 | 覆盖 |
| --- | --- | --- |
| 主 mod | `node ci/e2e.js` | 方块矩阵/工具组件/刷石机内核等（本版 GENERATORS 只剩原版级） |
| 附属 mod | `node ci/e2e-addon.js` | 本版新增：9 档方块/产物/配方/附属资源/版本一致性 |

`ci/e2e-addon.js` 的服务端前置：测试服 `mods/` 需**同时**放主 mod 0.4.0 + 附属 0.4.0 + Fabric API，
建议加装 Carpet（用例 D 用 `/player <bot> spawn` 造假人执行 `recipe give <recipe>` 做在线配方断言；
没有 Carpet 时自动降级为读 recipe JSON 并标 SKIP，不计失败）。默认端口 25576，可用 `RCON_PORT` 覆盖。

用例概览：

- **A** 9 档方块放置 + `#minecraft:mineable/pickaxe` 挖掘标签；
- **B** 9 档产物：机器下方接箱子，`tick sprint 40t` 后**恰好 2 个**对应 `compressedblocks:Nx_cobblestone`（箱内无其它物品）；
- **C** 原版级刷石机同样 40t → **恰好 2 个** `minecraft:cobblestone`；
- **D** 配方存在性：Carpet 假人执行 `recipe take/give` 读返回文本（未知配方报 `Unknown recipe`），或读 recipe JSON 校验产物 id/数量/原料；
- **E** 版本一致性：主/附 4 个 `build.gradle` 均为 0.4.0、附属 `fabric.mod.json` 依赖 `>=0.4.0 <0.5.0`、`neoforge.mods.toml` 依赖 `[0.4.0,0.5.0)`；
- **F** 附属资源：9 档 blockstate/items/模型（parent 链与贴图引用可解析）/战利品表/双语名/创造栏语言键/挖掘标签文件，另做破坏掉落断言。

`node ci/e2e-addon.js --files-only` 不连服务器，只跑 D-file / E / F，适合 PR 阶段快速自检。

## 七、兼容性

- Minecraft **1.21.11**、Java **21**；Fabric Loader + Fabric API，或 NeoForge（1.21.11 对应版本）。
- 除旧 2x/3x 刷石机外，其余 0.3.4 内容与存档不受影响（压缩方块/工具/护甲/食物/熔炉/漏斗/盆栽等行为不变）。
- 附属 mod 的压缩盆栽（`compressed_pot` / `compressed_hopper_pot`）逻辑与本版前一致，仅创造栏归属移动到「压缩拓展工具」。

## 八、已知限制

- 旧 2x/3x 刷石机**没有**自动替换或迁移脚本：方块消失是设计内行为，发布说明已按「升级前手动拆除」处理。
- 9 档刷石机只改产物等级，不改周期；如需更高吞吐请叠加漏斗/容器方案（本版不改传输速率）。
- 附属 mod 的配方/战利品表/标签均由生成器产出，手工修改会在下次 `gen_addon_assets.py` 运行时被覆盖。
