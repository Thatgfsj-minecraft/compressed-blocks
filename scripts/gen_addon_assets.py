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


def pot_elements():
    """分层盆：底板 12×12×1 + 实心腰 10×10×3 + 空心口沿 12×12×2（内孔 10×10）。
    所有面按方块坐标取 UV，贴图行与方块高度对齐（贴图行 0-4 为漏斗铁底留位）。"""
    def el(frm, to, cull_down=False):
        x0, y0, z0 = frm
        x1, y1, z1 = to
        faces = {
            "north": {"texture": "#side", "uv": [x0, y0, x1, y1]},
            "south": {"texture": "#side", "uv": [x0, y0, x1, y1]},
            "west": {"texture": "#side", "uv": [z0, y0, z1, y1]},
            "east": {"texture": "#side", "uv": [z0, y0, z1, y1]},
            "up": {"texture": "#top", "uv": [x0, z0, x1, z1]},
            "down": {"texture": "#bottom", "uv": [x0, z0, x1, z1]},
        }
        if cull_down:
            faces["down"]["cullface"] = "down"
        return {"from": list(frm), "to": list(to), "faces": faces}

    return [
        el((2, 0, 2), (14, 1, 14), cull_down=True),  # 底板
        el((3, 1, 3), (13, 4, 13)),                  # 腰身（实心）
        el((2, 4, 2), (14, 6, 3)),                   # 口沿北
        el((2, 4, 13), (14, 6, 14)),                 # 口沿南
        el((2, 4, 3), (3, 6, 13)),                   # 口沿西
        el((13, 4, 3), (14, 6, 13)),                 # 口沿东
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
        # 漏斗盆栽：底部（贴图行 0-4 = 底板+腰身）铁色、最底 1px 黑线（漏斗特征）；底面整张铁
        iron = None
        iron_src = os.path.join(main_tex, "cobblestone_generator_bottom.png")
        if os.path.exists(iron_src):
            iron = Image.open(iron_src).convert("RGBA")
        hop = d_side.copy()
        pxh = hop.load()
        if iron is not None:
            hop.paste(iron.crop((0, 0, 16, 4)), (0, 0))
        for x in range(16):
            pxh[x, 0] = (10, 10, 10, 255)
        hop.save(os.path.join(texdir, "compressed_hopper_pot_side.png"))
        if iron is not None:
            iron.save(os.path.join(texdir, "compressed_hopper_pot_bottom.png"))
        else:
            darken(bottom, 0.7).save(os.path.join(texdir, "compressed_hopper_pot_bottom.png"))

        for name, en, zh in POTS:
            hopper = name == "compressed_hopper_pot"
            side_tex = f"{NS}:block/compressed_hopper_pot_side" if hopper else f"{NS}:block/compressed_pot_side"
            bottom_tex = (f"{NS}:block/compressed_hopper_pot_bottom" if hopper
                          else f"{NS}:block/compressed_pot_bottom")
            dump(f"{assets}/blockstates/{name}.json",
                 {"variants": {"": {"model": f"{NS}:block/{name}"}}})
            dump(f"{assets}/models/block/{name}.json", {
                "gui_light": "side",
                "display": pot_display(),
                "textures": {"particle": side_tex, "side": side_tex,
                             "top": f"{NS}:block/compressed_pot_top",
                             "bottom": bottom_tex},
                "elements": pot_elements(),
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
