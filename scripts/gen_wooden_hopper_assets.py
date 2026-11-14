#!/usr/bin/env python3
"""
生成木质漏斗体系（基础木漏斗 + 1~3 重压缩木漏斗）全套资源：
1. 贴图（使用原版 hopper 贴图叠加木质色调，1~3 重叠加压缩环与层级染色）；
2. 模型（block hopper、hopper_side，item 模型）；
3. Blockstates（5 转向映射）；
4. items 映射；
5. 战利品表（loot_table/blocks 自掉落）；
6. 配方（0阶木板V字+箱子，1~3重递进压缩与无损拆解）；
7. 标签（mineable/axe）；
8. 本地化（zh_cn, en_us）。
"""
import os
import sys
import json
import zipfile
import numpy as np
from PIL import Image

REPO_ROOT = "/home/thatgfsj/AIagent/minecraft/compressed-blocks"
sys.path.append(os.path.join(REPO_ROOT, "scripts"))
import gen_assets

JAR = "/home/thatgfsj/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2/minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar"

with zipfile.ZipFile(JAR) as z:
    top_vanilla = Image.open(z.open("assets/minecraft/textures/block/hopper_top.png")).convert("RGBA")
    outside_vanilla = Image.open(z.open("assets/minecraft/textures/block/hopper_outside.png")).convert("RGBA")
    inside_vanilla = Image.open(z.open("assets/minecraft/textures/block/hopper_inside.png")).convert("RGBA")
    item_vanilla = Image.open(z.open("assets/minecraft/textures/item/hopper.png")).convert("RGBA")

def wood_tint(img):
    arr = np.array(img, dtype=np.float32)
    r, g, b, a = arr[:,:,0], arr[:,:,1], arr[:,:,2], arr[:,:,3]
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    norm = np.clip((lum - 20) / (110 - 20), 0.0, 1.0)
    out_r = (1.0 - norm) * 75 + norm * 205
    out_g = (1.0 - norm) * 55 + norm * 168
    out_b = (1.0 - norm) * 28 + norm * 105
    out = np.stack([out_r, out_g, out_b, a], axis=-1).astype(np.uint8)
    return Image.fromarray(out)

top_wood_base = wood_tint(top_vanilla)
outside_wood_base = wood_tint(outside_vanilla)
inside_wood_base = wood_tint(inside_vanilla)
item_wood_base = wood_tint(item_vanilla)

TIERS = [
    (0, "wooden_hopper", "Wooden Hopper", "木质漏斗"),
    (1, "1x_compressed_wooden_hopper", "Compressed Wooden Hopper", "一重压缩木质漏斗"),
    (2, "2x_compressed_wooden_hopper", "Double Compressed Wooden Hopper", "二重压缩木质漏斗"),
    (3, "3x_compressed_wooden_hopper", "Triple Compressed Wooden Hopper", "三重压缩木质漏斗"),
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

    for tier, name, en_name, zh_name in TIERS:
        if tier == 0:
            t_top = top_wood_base.copy()
            t_outside = outside_wood_base.copy()
            t_inside = inside_wood_base.copy()
            t_item = item_wood_base.copy()
        else:
            t_top = gen_assets.gen_block_tex(top_wood_base.copy(), tier, gray=False)
            t_outside = gen_assets.gen_block_tex(outside_wood_base.copy(), tier, gray=False)
            t_inside = gen_assets.gen_block_tex(inside_wood_base.copy(), tier, gray=False)
            t_item = gen_assets.level_tint(item_wood_base.copy(), tier)

        t_top.save(os.path.join(tex_block_dir, f"{name}_top.png"))
        t_outside.save(os.path.join(tex_block_dir, f"{name}_outside.png"))
        t_inside.save(os.path.join(tex_block_dir, f"{name}_inside.png"))
        t_item.save(os.path.join(tex_item_dir, f"{name}.png"))

        # 2. 模型
        dump_json(os.path.join(assets_dir, "models", "block", f"{name}.json"), {
            "parent": "minecraft:block/hopper",
            "textures": {
                "particle": f"compressedblocks:block/{name}_outside",
                "top": f"compressedblocks:block/{name}_top",
                "side": f"compressedblocks:block/{name}_outside",
                "inside": f"compressedblocks:block/{name}_inside"
            }
        })
        dump_json(os.path.join(assets_dir, "models", "block", f"{name}_side.json"), {
            "parent": "minecraft:block/hopper_side",
            "textures": {
                "particle": f"compressedblocks:block/{name}_outside",
                "top": f"compressedblocks:block/{name}_top",
                "side": f"compressedblocks:block/{name}_outside",
                "inside": f"compressedblocks:block/{name}_inside"
            }
        })
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

        # 5. 战利品表（方块自掉落）
        dump_json(os.path.join(data_dir, "compressedblocks", "loot_table", "blocks", f"{name}.json"), {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1.0,
                    "bonus_rolls": 0.0,
                    "entries": [
                        {
                            "type": "minecraft:item",
                            "name": f"compressedblocks:{name}"
                        }
                    ],
                    "conditions": [
                        {
                            "condition": "minecraft:survives_explosion"
                        }
                    ]
                }
            ]
        })

    # 为铁质压缩漏斗补全自掉落战利品表（如果尚未存在）
    for p in ["1x", "2x", "3x"]:
        iron_name = f"{p}_compressed_hopper"
        iron_loot = os.path.join(data_dir, "compressedblocks", "loot_table", "blocks", f"{iron_name}.json")
        if not os.path.exists(iron_loot):
            dump_json(iron_loot, {
                "type": "minecraft:block",
                "pools": [
                    {
                        "rolls": 1.0,
                        "bonus_rolls": 0.0,
                        "entries": [
                            {
                                "type": "minecraft:item",
                                "name": f"compressedblocks:{iron_name}"
                            }
                        ],
                        "conditions": [
                            {
                                "condition": "minecraft:survives_explosion"
                            }
                        ]
                    }
                ]
            })

    # 6. 配方
    recipe_dir = os.path.join(data_dir, "compressedblocks", "recipe")

    # 0 阶木质漏斗：5 个木板（V字）+ 1 个木箱（居中）
    dump_json(os.path.join(recipe_dir, "wooden_hopper.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {
            "#": "#minecraft:planks",
            "C": "minecraft:chest"
        },
        "pattern": [
            "# #",
            "#C#",
            " # "
        ],
        "result": {
            "count": 1,
            "id": "compressedblocks:wooden_hopper"
        }
    })

    # 1 重压缩木质漏斗：9 个基础木质漏斗
    dump_json(os.path.join(recipe_dir, "1x_compressed_wooden_hopper.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {
            "#": "compressedblocks:wooden_hopper"
        },
        "pattern": [
            "###",
            "###",
            "###"
        ],
        "result": {
            "count": 1,
            "id": "compressedblocks:1x_compressed_wooden_hopper"
        }
    })
    dump_json(os.path.join(recipe_dir, "unpack_1x_compressed_wooden_hopper.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [
            "compressedblocks:1x_compressed_wooden_hopper"
        ],
        "result": {
            "count": 9,
            "id": "compressedblocks:wooden_hopper"
        }
    })

    # 2 重压缩木质漏斗：9 个 1 重压缩木质漏斗
    dump_json(os.path.join(recipe_dir, "2x_compressed_wooden_hopper.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {
            "#": "compressedblocks:1x_compressed_wooden_hopper"
        },
        "pattern": [
            "###",
            "###",
            "###"
        ],
        "result": {
            "count": 1,
            "id": "compressedblocks:2x_compressed_wooden_hopper"
        }
    })
    dump_json(os.path.join(recipe_dir, "unpack_2x_compressed_wooden_hopper.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [
            "compressedblocks:2x_compressed_wooden_hopper"
        ],
        "result": {
            "count": 9,
            "id": "compressedblocks:1x_compressed_wooden_hopper"
        }
    })

    # 3 重压缩木质漏斗：9 个 2 重压缩木质漏斗
    dump_json(os.path.join(recipe_dir, "3x_compressed_wooden_hopper.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {
            "#": "compressedblocks:2x_compressed_wooden_hopper"
        },
        "pattern": [
            "###",
            "###",
            "###"
        ],
        "result": {
            "count": 1,
            "id": "compressedblocks:3x_compressed_wooden_hopper"
        }
    })
    dump_json(os.path.join(recipe_dir, "unpack_3x_compressed_wooden_hopper.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [
            "compressedblocks:3x_compressed_wooden_hopper"
        ],
        "result": {
            "count": 9,
            "id": "compressedblocks:2x_compressed_wooden_hopper"
        }
    })

    # 7. 标签追加（minecraft:mineable/axe）
    axe_tag_path = os.path.join(data_dir, "minecraft", "tags", "block", "mineable", "axe.json")
    if os.path.exists(axe_tag_path):
        with open(axe_tag_path, "r", encoding="utf-8") as f:
            tag_data = json.load(f)
        vals = tag_data.get("values", [])
        for _, name, _, _ in TIERS:
            h_id = f"compressedblocks:{name}"
            if h_id not in vals:
                vals.append(h_id)
        tag_data["values"] = sorted(vals)
        dump_json(axe_tag_path, tag_data)

    # 8. 语言文件
    zh_path = os.path.join(assets_dir, "lang", "zh_cn.json")
    if os.path.exists(zh_path):
        with open(zh_path, "r", encoding="utf-8") as f:
            zh_data = json.load(f)
        for _, name, _, zh_name in TIERS:
            zh_data[f"block.compressedblocks.{name}"] = zh_name
            zh_data[f"container.compressedblocks.{name}"] = zh_name
        dump_json(zh_path, zh_data)

    en_path = os.path.join(assets_dir, "lang", "en_us.json")
    if os.path.exists(en_path):
        with open(en_path, "r", encoding="utf-8") as f:
            en_data = json.load(f)
        for _, name, en_name, _ in TIERS:
            en_data[f"block.compressedblocks.{name}"] = en_name
            en_data[f"container.compressedblocks.{name}"] = en_name
        dump_json(en_path, en_data)

print("Generated all wooden hopper assets, models, blockstates, loot tables, recipes, tags and lang successfully!")
