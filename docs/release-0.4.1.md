# 0.4.1 发布说明

> 主 mod `compressedblocks` **0.4.1** + 附属 mod `compressedblockspot` **0.4.1** 同步发布。
> **两个 jar 必须成对更新**：版本不同会因依赖区间不满足被 loader 拒绝加载。

关联 Issue：#86（创造栏再分配）。上一版变更见 [0.4.0 发布说明](release-0.4.0.md)。

## 一、本次变更：创造栏再分配（**无破坏性变更**）

10 件「超模机器」从主 mod 的「压缩工具」栏迁到附属 mod 的「压缩拓展工具」栏（`compressedblockspot:addon_tools`），仅改变**创造栏可见位置**：

| 机器 | 搬迁件数 | id |
| --- | --- | --- |
| 压缩熔炉 | 4 | `compressedblocks:1x/2x/3x/4x_compressed_furnace` |
| 压缩铁漏斗 | 3 | `compressedblocks:1x/2x/3x_compressed_hopper` |
| 压缩木质漏斗 | 3 | `compressedblocks:1x/2x/3x_compressed_wooden_hopper` |

- **仍留在主 mod「压缩工具」栏**：基础木质漏斗 `compressedblocks:wooden_hopper`（未压缩、非超模），以及压缩工具/护甲、压缩箱子/压缩潜影盒等既有内容。
- 搬迁后附属 `addon_tools` 共 **21 件**：超模机器 10（熔炉 4 + 铁漏斗 3 + 木漏斗 3）+ 压缩盆栽 2（`compressed_pot` / `compressed_hopper_pot`）+ 1~9 重压缩刷石机 9。

### 为什么是「无破坏性变更」

- **id、配方、战利品表、挖掘标签、方块实体、燃料/烧炼数据全部不变**；已放置的机器、容器里的物品、配方书解锁、自动化电路、存档数据都不受影响。
- 变化的只是「玩家在创造模式物品栏哪一栏看到它们」。生存模式获取路径（合成/拆解）完全不变。
- **主 mod 单装（不装附属）时**：这 10 件仍可正常合成、放置、使用、破坏掉落，只是在创造栏不再显示（与压缩甘蔗/树苗、压缩盆栽的现状一致——它们本来也只由附属栏展示）。

## 二、版本与依赖

- 主 mod 与附属 mod 均为 **0.4.1**，必须成对发布/更新。
- 附属依赖主 mod：Fabric `>=0.4.1 <0.5.0`、NeoForge `[0.4.1,0.5.0)`。
- 0.4.0 与 0.4.1 混搭（如主 0.4.0 + 附 0.4.1）会被 loader 依赖检查拒绝；请两个 jar 一起替换。

## 三、升级步骤

1. 关闭游戏/服务器，把主 mod 与附属 mod **两个 jar 一起**替换为 0.4.1（删除旧 jar）。
2. 无需任何存档操作：机器、配方、物品与自动化都不变；创造栏位置变化只是展示层。
3. 若只单装主 mod（不装附属）：直接换 0.4.1 主 mod 即可；10 件机器仍可正常使用，只是不在创造栏显示。
4. 0.4.0 的破坏性变更（旧 2x/3x 压缩刷石机移除）不适用于本次升级——若你还在 0.3.x，请先阅读 [0.4.0 发布说明](release-0.4.0.md)。

## 四、构建顺序（与 0.4.0 相同，未变化）

附属 mod 通过 `compileOnly` 引用**主 mod 构建产物**，必须先构建主 mod、再构建附属：

```bash
# 先主 mod
cd 1.21.11/fabric   && ./gradlew build
cd 1.21.11/neoforge && ./gradlew build

# 再附属 mod
cd 1.21.11/addon/fabric   && ./gradlew build
cd 1.21.11/addon/neoforge && ./gradlew build
```

- 附属各端 `gradle.properties` 的 `main_mod_version` 已同步为 `0.4.1`；主 mod 版本变化时要一起改。

## 五、验证 / E2E

- 附属 E2E：`node ci/e2e-addon.js`（默认 `127.0.0.1:25576`，`RCON_PORT` 覆盖；服务端 mods 需同时放主 mod 0.4.1 + 附属 0.4.1 + Fabric API，建议加 Carpet 以启用在线配方断言）。
  - 用例 E 已同步断言 0.4.1：主/附 4 个 `build.gradle` = 0.4.1，附属依赖 `>=0.4.1 <0.5.0` / `[0.4.1,0.5.0)`；
  - 用例 A~F（9 档刷石机方块/产物/配方/附属资源/破坏掉落）不受本次改动影响，继续作为回归门。
- 不连服快速自检：`node ci/e2e-addon.js --files-only`。
- 主 mod E2E：`node ci/e2e.js`（内容不受本次改动影响）。

## 六、兼容性

- Minecraft **1.21.11**、Java **21**；Fabric Loader + Fabric API，或 NeoForge。
- 存档安全：本版无任何 id/数据删除或重命名，0.4.0 存档可直接升级。
- 创造栏结构仍为 5 栏（主 3 + 附属 2），栏 id 与顺序不变：`blocks` / `tools` / `food` / `compressedblockspot:addon_blocks` / `compressedblockspot:addon_tools`。
