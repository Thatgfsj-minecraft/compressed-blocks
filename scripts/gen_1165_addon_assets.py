#!/usr/bin/env python3
"""附属 mod（compressedblockspot）1.16.5 资源生成：模型/方块状态/配方/战利品/语言/贴图。
与 1.21.11 的 gen_addon_assets.py 同逻辑，差异：1.16.5 格式（无 items/ 定义、recipes/loot_tables
复数、result={"item","count"}）、资源与主 mod 同 jar（输出到同一 resources 树）。
用法：python scripts/gen_1165_addon_assets.py
"""
import json
import os
from pathlib import Path
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "compressedblockspot"
MAIN_TEX = os.path.join(ROOT, "1.16.5", "forge", "src", "main", "resources",
                        "assets", "compressedblocks", "textures", "block")
RES = os.path.join(ROOT, "1.16.5", "forge", "src", "main", "resources")

POTS = [
    ("compressed_pot", "Compressed Pot", "压缩盆栽"),
    ("compressed_hopper_pot", "Compressed Hopper Pot", "压缩漏斗盆栽"),
]


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
    """分层盆：底板 12×12×1 + 实心腰 10×10×3 + 空心口沿 12×12×2（内孔 10×10）。"""
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
        el((2, 0, 2), (14, 1, 14), cull_down=True),
        el((3, 1, 3), (13, 4, 13)),
        el((2, 4, 2), (14, 6, 3)),
        el((2, 4, 13), (14, 6, 14)),
        el((2, 4, 3), (3, 6, 13)),
        el((13, 4, 3), (14, 6, 13)),
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
    assets = os.path.join(RES, "assets", NS)
    data = os.path.join(RES, "data", NS)
    texdir = os.path.join(assets, "textures", "block")
    os.makedirs(texdir, exist_ok=True)

    side = Image.open(os.path.join(MAIN_TEX, "pot_side.png")).convert("RGBA")
    top = Image.open(os.path.join(MAIN_TEX, "pot_top.png")).convert("RGBA")
    bottom = Image.open(os.path.join(MAIN_TEX, "pot_bottom.png")).convert("RGBA")
    iron_src = os.path.join(MAIN_TEX, "3x_cobblestone_generator_bottom.png")

    d_side = darker(side)
    d_top = darker(top)
    d_bottom = darker(bottom)
    d_side.save(os.path.join(texdir, "compressed_pot_side.png"))
    d_top.save(os.path.join(texdir, "compressed_pot_top.png"))
    d_bottom.save(os.path.join(texdir, "compressed_pot_bottom.png"))
    # 漏斗盆栽：底部铁色 + 最底 1px 黑线；底面整张铁
    iron = Image.open(iron_src).convert("RGBA") if os.path.exists(iron_src) else None
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
        darker(bottom, 0.7).save(os.path.join(texdir, "compressed_hopper_pot_bottom.png"))

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
        # 1.16.5 loot_tables：掉自身
        dump(f"{data}/loot_tables/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0,
                       "conditions": [{"condition": "minecraft:survives_explosion"}],
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}],
        })

    dump(f"{assets}/lang/en_us.json", {
        "block.compressedblockspot.compressed_pot": "Compressed Pot",
        "block.compressedblockspot.compressed_hopper_pot": "Compressed Hopper Pot",
    }, indent=2)
    dump(f"{assets}/lang/zh_cn.json", {
        "block.compressedblockspot.compressed_pot": "压缩盆栽",
        "block.compressedblockspot.compressed_hopper_pot": "压缩漏斗盆栽",
    }, indent=2)

    # 配方（1.16.5 格式）：压缩盆栽 = 5× 任意一重压缩方块（船形，主 mod pot_material 标签）；
    # 压缩漏斗盆栽 = 压缩盆栽 + 漏斗（无序）
    dump(f"{data}/recipes/compressed_pot.json", {
        "type": "minecraft:crafting_shaped",
        "pattern": ["A A", "AAA"],
        "key": {"A": {"tag": "compressedblocks:pot_material"}},
        "result": {"item": f"{NS}:compressed_pot", "count": 1},
    })
    dump(f"{data}/recipes/compressed_hopper_pot.json", {
        "type": "minecraft:crafting_shapeless",
        "ingredients": [{"item": f"{NS}:compressed_pot"}, {"item": "minecraft:hopper"}],
        "result": {"item": f"{NS}:compressed_hopper_pot", "count": 1},
    })
    # 挖掘工具标签（1.16.5 挖掘档位走 Java 属性，此处供其它 mod 查询）
    pot_blocks = [f"{NS}:{name}" for name, _, _ in POTS]
    for tag in ("needs_stone_tool",):
        dump(f"{data}/minecraft/tags/blocks/{tag}.json",
             {"replace": False, "values": pot_blocks})
    print(f"addon resources -> {RES}")


if __name__ == "__main__":
    main()
