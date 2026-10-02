#!/usr/bin/env python3
"""附属 mod（compressedblockspot）资源生成：模型/方块状态/物品定义/配方/战利品/语言/贴图。
贴图从主 mod 生成的盆栽贴图加深（×0.62），漏斗盆栽侧面底部加黑色横线。
用法：python scripts/gen_addon_assets.py
"""
import json
import os
from pathlib import Path
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "compressedblockspot"
MAIN = os.path.join(ROOT, "1.21.11", "fabric", "src", "main", "resources", "assets", "compressedblocks")

SUBS = [os.path.join(ROOT, "1.21.11", "addon", "fabric"),
        os.path.join(ROOT, "1.21.11", "addon", "neoforge")]


def dump(path, obj, indent=None):
    text = json.dumps(obj, ensure_ascii=False, separators=(",", ":"), indent=indent) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def pot_display():
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                  "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                  "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                                 "scale": [0.4, 0.4, 0.4]},
    }


POT_ELEMENTS = [
    {"from": [2, 0, 2], "to": [14, 6, 3],
     "faces": {"down": {"texture": "#bottom", "cullface": "down"},
               "north": {"texture": "#side", "cullface": "north"},
               "south": {"texture": "#side"}, "west": {"texture": "#side"},
               "east": {"texture": "#side"}, "up": {"texture": "#top"}}},
    {"from": [2, 0, 13], "to": [14, 6, 14],
     "faces": {"down": {"texture": "#bottom", "cullface": "down"},
               "north": {"texture": "#side"}, "south": {"texture": "#side", "cullface": "south"},
               "west": {"texture": "#side"}, "east": {"texture": "#side"}, "up": {"texture": "#top"}}},
    {"from": [2, 0, 3], "to": [3, 6, 13],
     "faces": {"down": {"texture": "#bottom", "cullface": "down"},
               "north": {"texture": "#side"}, "south": {"texture": "#side"},
               "west": {"texture": "#side", "cullface": "west"},
               "east": {"texture": "#side"}, "up": {"texture": "#top"}}},
    {"from": [13, 0, 3], "to": [14, 6, 13],
     "faces": {"down": {"texture": "#bottom", "cullface": "down"},
               "north": {"texture": "#side"}, "south": {"texture": "#side"},
               "west": {"texture": "#side"},
               "east": {"texture": "#side", "cullface": "east"}, "up": {"texture": "#top"}}},
]

POTS = [
    ("compressed_pot", "Pot", "压缩盆栽"),
    ("compressed_hopper_pot", "Compressed Hopper Pot", "压缩漏斗盆栽"),
]


def darker(img, f=0.62):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (int(r * f), int(g * f), int(b * f), a)
    return out


def main():
    main_tex = os.path.join(MAIN, "textures", "block")
    side = Image.open(os.path.join(main_tex, "pot_side.png")).convert("RGBA")
    top = Image.open(os.path.join(main_tex, "pot_top.png")).convert("RGBA")
    bottom = Image.open(os.path.join(main_tex, "pot_bottom.png")).convert("RGBA")

    for sub in SUBS:
        assets = os.path.join(sub, "src", "main", "resources", "assets", NS)
        data = os.path.join(sub, "src", "main", "resources", "data", NS)
        texdir = os.path.join(assets, "textures", "block")
        os.makedirs(texdir, exist_ok=True)

        d_side = darker(side)
        d_top = darker(top)
        d_bottom = darker(bottom)
        d_side.save(os.path.join(texdir, "compressed_pot_side.png"))
        d_top.save(os.path.join(texdir, "compressed_pot_top.png"))
        d_bottom.save(os.path.join(texdir, "compressed_pot_bottom.png"))
        hop = d_side.copy()
        pxh = hop.load()
        for y in range(13, 16):
            for x in range(16):
                pxh[x, y] = (16, 16, 16, 255)
        hop.save(os.path.join(texdir, "compressed_hopper_pot_side.png"))

        for name, en, zh in POTS:
            hopper = name == "compressed_hopper_pot"
            side_tex = f"{NS}:block/compressed_hopper_pot_side" if hopper else f"{NS}:block/compressed_pot_side"
            dump(f"{assets}/blockstates/{name}.json",
                 {"variants": {"": {"model": f"{NS}:block/{name}"}}})
            dump(f"{assets}/models/block/{name}.json", {
                "gui_light": "side",
                "display": pot_display(),
                "textures": {"particle": side_tex, "side": side_tex,
                             "top": f"{NS}:block/compressed_pot_top",
                             "bottom": f"{NS}:block/compressed_pot_bottom"},
                "elements": POT_ELEMENTS,
            })
            dump(f"{assets}/models/item/{name}.json",
                 {"parent": f"{NS}:block/{name}"})
            dump(f"{assets}/items/{name}.json",
                 {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
            dump(f"{data}/loot_table/blocks/{name}.json", {
                "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{name}",
                "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                           "conditions": [{"condition": "minecraft:survives_explosion"}],
                           "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}],
            })

        dump(f"{assets}/lang/en_us.json", {
            "itemGroup.compressedblockspot.pots": "Compressed Pots",
            "block.compressedblockspot.compressed_pot": "Compressed Pot",
            "block.compressedblockspot.compressed_hopper_pot": "Compressed Hopper Pot",
        }, indent=2)
        dump(f"{assets}/lang/zh_cn.json", {
            "itemGroup.compressedblockspot.pots": "压缩盆栽",
            "block.compressedblockspot.compressed_pot": "压缩盆栽",
            "block.compressedblockspot.compressed_hopper_pot": "压缩漏斗盆栽",
        }, indent=2)

        # 配方（保持原版设计不变）：压缩盆栽 = 5× 任意一重压缩方块（船形，主 mod pot_material 标签）；
        # 漏斗盆栽 = 压缩盆栽 + 漏斗（无序）
        dump(f"{data}/recipe/compressed_pot.json", {
            "type": "minecraft:crafting_shaped", "category": "building",
            "pattern": ["A A", "AAA"],
            "key": {"A": "#compressedblocks:pot_material"},
            "result": {"count": 1, "id": f"{NS}:compressed_pot"},
        })
        dump(f"{data}/recipe/compressed_hopper_pot.json", {
            "type": "minecraft:crafting_shapeless", "category": "building",
            "ingredients": [f"{NS}:compressed_pot", "minecraft:hopper"],
            "result": {"count": 1, "id": f"{NS}:compressed_hopper_pot"},
        })
        print(f"addon resources -> {sub}")


if __name__ == "__main__":
    main()
