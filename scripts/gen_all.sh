#!/usr/bin/env bash
# 压缩方块一键资源生成流水线。
#
# 顺序敏感，四步缺一不可：
#   1. gen_assets.py               贴图（assets/compressedblocks/textures/**）
#   2. gen_resources.py            方块/物品模型、blockstate、配方、战利品表、标签、语言、装备
#   3. gen_hopper_assets.py        1~3 重压缩铁漏斗的资产/配方/标签/lang 条目
#   4. gen_wooden_hopper_assets.py 木质漏斗与 1~3 重压缩木质漏斗（含战利品表）
#
# 第 2 步会清空它自己拥有的生成目录，第 3/4 步再把漏斗条目追加回去，
# 所以不能跳步、也不能只跑前两步。
#
# 用法：./scripts/gen_all.sh                  # 在仓库任意位置执行均可
#       ./scripts/gen_all.sh --target 1.21.11 # 额外参数透传给 gen_resources.py
# 幂等：跑完 git status --short 必须为空。
set -euo pipefail

cd "$(dirname "$0")/.."

PYTHON="${PYTHON:-python3}"
# 不生成 __pycache__：否则 git status 会多出未跟踪目录，幂等检查失真
export PYTHONDONTWRITEBYTECODE=1

echo "==> [1/4] gen_assets.py：贴图"
"$PYTHON" scripts/gen_assets.py

echo "==> [2/4] gen_resources.py：模型/blockstate/配方/战利品表/标签/语言"
"$PYTHON" scripts/gen_resources.py "$@"

echo "==> [3/4] gen_hopper_assets.py：压缩铁漏斗"
"$PYTHON" scripts/gen_hopper_assets.py

echo "==> [4/4] gen_wooden_hopper_assets.py：木质漏斗"
"$PYTHON" scripts/gen_wooden_hopper_assets.py

echo "==> 完成。生成器幂等性检查：git status --short 应为空"
git status --short
