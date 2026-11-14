#!/usr/bin/env python3
"""
生成一至三重压缩漏斗的全套资源：
1. 贴图（使用原版 hopper_outside / hopper_top / hopper_inside / item/hopper 叠加压缩特征环）；
2. 模型（block hopper、hopper_side，item 模型）；
3. Blockstates（5 转向映射）；
4. items 映射；
5. 配方（1 重 9 个原木合成，2/3 重递进升级与拆解）；
6. 标签（mineable/pickaxe, needs_iron_tool）；
7. 本地化（zh_cn, en_us）。
"""
import os
import sys
import json
import zipfile
from PIL import Image

REPO_ROOT = "/home/thatgfsj/AIagent/minecraft/compressed-blocks"
sys.path.append(os.path.join(REPO_ROOT, "scripts"))
import gen_assets

JAR = "/home/thatgfsj/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2/minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar"

with zipfile.ZipFile(JAR) as z:
    top_base = Image.open(z.open("assets/minecraft/textures/block/hopper_top.png")).convert("RGBA")
    outside_base = Image.open(z.open("assets/minecraft/textures/block/hopper_outside.png")).convert("RGBA")
    inside_base = Image.open(z.open("assets/minecraft/textures/block/hopper_inside.png")).convert("RGBA")
    item_base = Image.open(z.open("assets/minecraft/textures/item/hopper.png")).convert("RGBA")

TIERS = [
    (1, "1x", "Compressed Hopper", "一重压缩漏斗"),
    (2, "2x", "Double Compressed Hopper", "二重压缩漏斗"),
    (3, "3x", "Triple Compressed Hopper", "三重压缩漏斗"),
]

TARGET_DIRS = [
    os.path.join(REPO_ROOT, "1.21.11", "fabric", "src", "main", "resources"),
    os.path.join(REPO_ROOT, "1.21.11", "neoforge", "src", "main", "resources"),
]

def dump_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

for target in TARGET_DIRS:
    assets_dir = os.path.join(target, "assets", "compressedblocks")
    data_dir = os.path.join(target, "data")

    # 1. 贴图
    tex_block_dir = os.path.join(assets_dir, "textures", "block")
    tex_item_dir = os.path.join(assets_dir, "textures", "item")
    os.makedirs(tex_block_dir, exist_ok=True)
    os.makedirs(tex_item_dir, exist_ok=True)

    for tier, prefix, en_name, zh_name in TIERS:
        name = f"{prefix}_compressed_hopper"
        # 贴图
        t_top = gen_assets.gen_block_tex(top_base.copy(), tier, gray=False)
        t_outside = gen_assets.gen_block_tex(outside_base.copy(), tier, gray=False)
        t_inside = gen_assets.gen_block_tex(inside_base.copy(), tier, gray=False)
        t_item = gen_assets.level_tint(item_base.copy(), tier)

        t_top.save(os.path.join(tex_block_dir, f"{name}_top.png"))
        t_outside.save(os.path.join(tex_block_dir, f"{name}_outside.png"))
        t_inside.save(os.path.join(tex_block_dir, f"{name}_inside.png"))
        t_item.save(os.path.join(tex_item_dir, f"{name}.png"))

        # 2. 模型
        # 方块向下模型（指向模组自包含模板）
        dump_json(os.path.join(assets_dir, "models", "block", f"{name}.json"), {
            "parent": "compressedblocks:block/template_hopper",
            "textures": {
                "particle": f"compressedblocks:block/{name}_outside",
                "top": f"compressedblocks:block/{name}_top",
                "side": f"compressedblocks:block/{name}_outside",
                "inside": f"compressedblocks:block/{name}_inside"
            }
        })
        # 方块侧向模型（指向模组自包含模板）
        dump_json(os.path.join(assets_dir, "models", "block", f"{name}_side.json"), {
            "parent": "compressedblocks:block/template_hopper_side",
            "textures": {
                "particle": f"compressedblocks:block/{name}_outside",
                "top": f"compressedblocks:block/{name}_top",
                "side": f"compressedblocks:block/{name}_outside",
                "inside": f"compressedblocks:block/{name}_inside"
            }
        })
        # 物品模型（2D 手持物品贴图）
        dump_json(os.path.join(assets_dir, "models", "item", f"{name}.json"), {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": f"compressedblocks:item/{name}"
            }
        })

        # 3. Blockstate
        dump_json(os.path.join(assets_dir, "blockstates", f"{name}.json"), {
            "variants": {
                "facing=down": {
                    "model": f"compressedblocks:block/{name}"
                },
                "facing=east": {
                    "model": f"compressedblocks:block/{name}_side",
                    "y": 90
                },
                "facing=north": {
                    "model": f"compressedblocks:block/{name}_side"
                },
                "facing=south": {
                    "model": f"compressedblocks:block/{name}_side",
                    "y": 180
                },
                "facing=west": {
                    "model": f"compressedblocks:block/{name}_side",
                    "y": 270
                }
            }
        })

        # 4. 1.21.11 items/ 映射
        dump_json(os.path.join(assets_dir, "items", f"{name}.json"), {
            "model": {
                "type": "minecraft:model",
                "model": f"compressedblocks:item/{name}"
            }
        })

    # 5. 配方
    recipe_dir = os.path.join(data_dir, "compressedblocks", "recipe")
    # 1 重压缩漏斗：9 个原版漏斗合成
    dump_json(os.path.join(recipe_dir, "1x_compressed_hopper.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {
            "#": "minecraft:hopper"
        },
        "pattern": [
            "###",
            "###",
            "###"
        ],
        "result": {
            "count": 1,
            "id": "compressedblocks:1x_compressed_hopper"
        }
    })
    dump_json(os.path.join(recipe_dir, "unpack_1x_compressed_hopper.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [
            "compressedblocks:1x_compressed_hopper"
        ],
        "result": {
            "count": 9,
            "id": "minecraft:hopper"
        }
    })
    # 2 重压缩漏斗
    dump_json(os.path.join(recipe_dir, "2x_compressed_hopper.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {
            "#": "compressedblocks:1x_compressed_hopper"
        },
        "pattern": [
            "###",
            "###",
            "###"
        ],
        "result": {
            "count": 1,
            "id": "compressedblocks:2x_compressed_hopper"
        }
    })
    dump_json(os.path.join(recipe_dir, "unpack_2x_compressed_hopper.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [
            "compressedblocks:2x_compressed_hopper"
        ],
        "result": {
            "count": 9,
            "id": "compressedblocks:1x_compressed_hopper"
        }
    })
    # 3 重压缩漏斗
    dump_json(os.path.join(recipe_dir, "3x_compressed_hopper.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {
            "#": "compressedblocks:2x_compressed_hopper"
        },
        "pattern": [
            "###",
            "###",
            "###"
        ],
        "result": {
            "count": 1,
            "id": "compressedblocks:3x_compressed_hopper"
        }
    })
    dump_json(os.path.join(recipe_dir, "unpack_3x_compressed_hopper.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [
            "compressedblocks:3x_compressed_hopper"
        ],
        "result": {
            "count": 9,
            "id": "compressedblocks:2x_compressed_hopper"
        }
    })

    # 6. 标签追加
    pickaxe_tag_path = os.path.join(data_dir, "minecraft", "tags", "block", "mineable", "pickaxe.json")
    if os.path.exists(pickaxe_tag_path):
        with open(pickaxe_tag_path, "r", encoding="utf-8") as f:
            tag_data = json.load(f)
        vals = tag_data.get("values", [])
        for _, p, _, _ in TIERS:
            h_id = f"compressedblocks:{p}_compressed_hopper"
            if h_id not in vals:
                vals.append(h_id)
        tag_data["values"] = sorted(vals)
        dump_json(pickaxe_tag_path, tag_data)

    iron_tool_tag_path = os.path.join(data_dir, "minecraft", "tags", "block", "needs_iron_tool.json")
    if os.path.exists(iron_tool_tag_path):
        with open(iron_tool_tag_path, "r", encoding="utf-8") as f:
            tag_data = json.load(f)
        vals = tag_data.get("values", [])
        for _, p, _, _ in TIERS:
            h_id = f"compressedblocks:{p}_compressed_hopper"
            if h_id not in vals:
                vals.append(h_id)
        tag_data["values"] = sorted(vals)
        dump_json(iron_tool_tag_path, tag_data)

    # 7. 语言文件
    zh_path = os.path.join(assets_dir, "lang", "zh_cn.json")
    if os.path.exists(zh_path):
        with open(zh_path, "r", encoding="utf-8") as f:
            zh_data = json.load(f)
        for _, p, _, zh_name in TIERS:
            zh_data[f"block.compressedblocks.{p}_compressed_hopper"] = zh_name
            zh_data[f"container.compressedblocks.{p}_compressed_hopper"] = zh_name
        dump_json(zh_path, zh_data)

    en_path = os.path.join(assets_dir, "lang", "en_us.json")
    if os.path.exists(en_path):
        with open(en_path, "r", encoding="utf-8") as f:
            en_data = json.load(f)
        for _, p, en_name, _ in TIERS:
            en_data[f"block.compressedblocks.{p}_compressed_hopper"] = en_name
            en_data[f"container.compressedblocks.{p}_compressed_hopper"] = en_name
        dump_json(en_path, en_data)

print("Generated all hopper assets, models, blockstates, recipes, tags and lang successfully!")
