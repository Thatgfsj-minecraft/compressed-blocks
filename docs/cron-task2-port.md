# 定时任务书：多版本移植（分仓=仓库内多版本分支）

> 已排期：2026-10-05 02:00 一次性（automation-15603c77，复用槽位，nextRunAt 已指向该时刻）。
> 备用：若当晚未触发，开一个**新对话**发送：
> 「阅读 docs/cron-task2-port.md，按其中"定时任务配置"创建一次性定时任务（今天凌晨2点）」

## 定时任务配置

- **触发时间**：凌晨 2 点（一次性，不重复）
- **标题**：凌晨2点分仓+多版本移植：每版本一代理（最多2个并行）
- **任务提示词**（创建时把下面整段作为 prompt）：

---

这是压缩方块 mod 的多版本移植任务（一次性）。仓库：O:\clawwork\chuansongmen\compressed-blocks（分支 main = 1.21.11 正式线，绝不改动）。策略（用户定稿）：所有移植版本都在本仓库内的长期分支上进行，分支命名 port/<版本>，如 port/26.x、port/1.16.5、port/1.12.2、port/1.7.10。

第一步：git status 必须干净（不干净则中止并报告）。检查 backlog 文件 _port-backlog.md：不存在则在仓库根创建（用 Write 工具），内容为版本路线图与状态表：port/26.x（新版本线，优先；以实际可查的最新 MC 版本号为准）、port/1.16.5、port/1.12.2、port/1.7.10，每项标注状态（未开始/进行中-进度笔记/已完成）。

第二步：从 backlog 取最多 2 个"未开始或进行中"的版本，每个版本派 1 个 general-purpose 子代理（用 Agent 工具，最多同时 2 个）。并发要求：每个子代理必须用独立 git worktree 工作以免分支冲突——git worktree add O:/clawwork/chuansongmen/cb-port-<版本> port/<版本>（分支不存在则先 git branch port/<版本> main 再 worktree add；分支已存在且 worktree 已建则直接复用）。每个子代理的任务书：
- 在自己的 worktree 目录里的 port/<版本> 分支上工作
- 先通读 main 的 1.21.11/ 目录结构与 scripts/gen_assets.py、scripts/gen_resources.py，理解 mod 构成：43 材料×9 重压缩储存方块、树叶/树苗/耕地/作物/甘蔗、盆栽+漏斗盆栽、刷石机×3、压缩箱子/潜影盒（243 格滚动容器）、压缩工具/盔甲/食物、22 兼容金属、附属 mod compressedblockspot（压缩盆栽+压缩漏斗盆栽）
- 按该版本生态搭脚手架：26.x 用当时最新 Fabric + Fabric API；1.16.5 用 Forge 36.x；1.12.2 用 Forge 14.23.x；1.7.10 用 Forge 10.x。JDK 注意：本机只装了 jdk21（C:/Program Files/Amazon Corretto/jdk21.0.10_7），旧版本需要的 Java 8 等用 gradle toolchain 或记录阻塞点到 backlog，不要卡死
- 移植顺序（能做多少做多少，不求一次完成）：①核心 43×9 储存方块（注册/贴图/模型/配方）→ ②树叶树苗甘蔗 → ③工具盔甲食物 → ④箱子潜影盒滚动容器 → ⑤盆栽家族与刷石机 → ⑥附属 mod。贴图/模型/配方优先改造 scripts 生成器复用（旧版本资源格式差异自行适配）
- 收尾：该版本子项目 gradle build 至少把已移植部分编译通过（GRADLE_USER_HOME=O:/clawwork/chuansongmen/.gradle-home），git add -A 提交到 port/<版本> 分支；push 该分支：git -c http.proxy= push -q origin port/<版本>，失败改用 git -c http.proxy=http://127.0.0.1:7897 push -q origin port/<版本>
- 在 worktree 里更新 _port-backlog.md 的该版本状态与进度笔记并提交到该分支
- 写任何源码一律用 Write/Edit 工具（Bash 写源码会被安全钩子拒绝）；绝不 taskkill 全部 java；不部署任何东西到 H:\Minecraft

第三步：你（主控）汇总两个子代理的产出，在 main 上只提交 _port-backlog.md 的路线图/状态镜像（代码一律不进 main），push 同上代理规则。若 backlog 中所有版本都已完成，直接回复"backlog 已完成"并结束。不要新建或删除定时任务。最终回复用中文总结本轮移植成果。

---

## 备注

- 仓库内多版本分支策略（用户定稿）：main = 1.21.11 正式线不动；每版本一条 port/<版本> 长期分支；子代理用 git worktree 并发。
- 若以后想重启"每夜"模式：把触发时间改为每天 02:00（cron: `0 2 * * *`，recurring）即可。
