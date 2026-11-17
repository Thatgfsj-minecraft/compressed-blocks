#!/usr/bin/env python3
"""压缩方块 mod 资源生成器：blockstate/模型/物品模型定义/配方/战利品表/标签/语言文件/装备资产。
输出必须与仓库内容逐字节一致：重跑 scripts/gen_all.sh（本脚本只是其中的第 2 步）后 git status 应保持为空。

清理边界（合作式所有权）：本脚本只清理**自己产出**的内容——
  * assets/compressedblocks 下除 textures/ 与本脚本保留白名单（手写模板 + 漏斗资产）之外的条目；
  * data/ 下本脚本拥有的命名空间（compressedblocks / minecraft / c / ftbultimine）。
loader 专属命名空间（如 neoforge/data_maps 的燃料数据图）与其它模组命名空间一律不碰。

命名规则（与 CompressedBlocks.java 严格一致）：
  储存方块 <prefix>_<mat>            43 材料含 24 新建材
  树叶     <prefix>_<wood>_leaves
  树苗     <prefix>_<wood>_sapling
  耕地     <prefix>_farmland         无物品
  作物     <prefix>_<crop>_plant     3 级封顶；BlockItem 挂作物物品 id
  食物     <prefix>_<food>           3 级封顶（bread/beef/melon）
  盔甲     <prefix>_stone_<piece>
用法：python scripts/gen_resources.py [--target 1.21.11|1.21.1]
"""

import argparse
import os

from res_assets import gen_assets
from res_common import (
    NS, ROOT, armor_names, cane_names, clean_owned_assets, clean_owned_data,
    compat_names, crop_block_names, crop_produce_names, extra_seed_names,
    farmland_names, food_names, furnace_names, generator_names, leaves_names,
    pot_names, sapling_names, stick_names, storage_names, tool_names,
)
from res_data import gen_data
from res_lang import gen_lang


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default="1.21.11", choices=["1.21.11"],
                    help="1.21.1 冻结在 0.2.0，不再生成")
    args = ap.parse_args()
    subs = [os.path.join(ROOT, args.target, loader) for loader in ("fabric", "neoforge")]

    blocks = (storage_names() + leaves_names() + sapling_names() + farmland_names()
              + crop_block_names() + cane_names() + pot_names() + generator_names() + furnace_names()
              + compat_names() + ["compressed_chest", "compressed_shulker_box"])
    items = (storage_names() + leaves_names() + sapling_names() + crop_block_names() + cane_names()
             + pot_names() + generator_names() + furnace_names() + tool_names() + armor_names() + food_names()
             + crop_produce_names() + stick_names() + extra_seed_names() + compat_names()
             + ["compressed_chest", "compressed_shulker_box"])
    assert len(storage_names()) == 459, len(storage_names())
    assert len(tool_names()) == 90, len(tool_names())
    assert len(blocks) == 1319, len(blocks)
    assert len(items) == 1556, len(items)
    print(f"blocks={len(blocks)} items={len(items)} (tools={len(tool_names())} armor={len(armor_names())} "
          f"saplings={len(sapling_names())} leaves={len(leaves_names())} food={len(food_names())} "
          f"canes={len(cane_names())})")
    for sub in subs:
        if not os.path.exists(os.path.join(sub, "build.gradle")):
            print(f"skip {sub} (no build.gradle)")
            continue
        res = f"{sub}/src/main/resources/assets/{NS}"
        data = f"{sub}/src/main/resources/data"
        # loader 专属 data map（燃料图）不归本脚本所有，清理前后都必须在
        fuel_map = os.path.join(data, "neoforge", "data_maps", "item", "furnace_fuels.json")
        had_fuel_map = os.path.exists(fuel_map)
        # 只清本脚本自己的产物：资产走保留白名单，data 走命名空间清单。
        # 以前的 shutil.rmtree(data) 会把 neoforge/ 燃料图与漏斗资产一起删掉。
        if os.environ.get("NO_CLEAN") != "1":
            if os.path.isdir(res):
                clean_owned_assets(res)
            clean_owned_data(data)
        gen_assets(res)
        gen_lang(res)
        gen_data(data)
        if had_fuel_map and not os.path.exists(fuel_map):
            raise SystemExit(f"ERROR: 生成过程误删了 loader 专属燃料数据图：{fuel_map}")
        if not os.path.isdir(f"{res}/textures"):
            raise SystemExit(f"ERROR: {res}/textures 不存在——先跑 gen_assets.py 再跑本脚本")
        print(f"generated resources into {sub}")


if __name__ == "__main__":
    main()
