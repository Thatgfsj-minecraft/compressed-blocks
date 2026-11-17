#!/usr/bin/env python3
"""附属 mod（compressedblockspot）资源生成：模型/方块状态/物品定义/配方/战利品/语言/贴图。

内容：
- 1~9 重压缩刷石机：贴图（复用主 mod gen_assets.generator_textures）+ blockstate/模型/物品定义/
  战利品表/配方/拆解配方/配方解锁进度；
- 压缩盆栽 + 压缩漏斗盆栽：沿用原有资源（贴图从主 mod 盆栽贴图加深，漏斗盆栽侧面加铁色与黑线）；
- 挖掘标签写到正确路径 data/minecraft/tags/block/mineable/{pickaxe,axe}.json；
- 创造栏语言：5 栏改造（删除 pots 栏，新增 addon_blocks / addon_tools）。

用法：python scripts/gen_addon_assets.py
幂等：同一棵仓库上连跑两次，git status --short 必须为空。
"""
import json
import os
import shutil
from pathlib import Path

from PIL import Image

import asset_io  # 同目录：按像素幂等写 PNG
import gen_assets  # 同目录：复用主 mod 刷石机贴图生成

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "compressedblockspot"
MAIN_NS = "compressedblocks"
MAIN = os.path.join(ROOT, "1.21.11", "fabric", "src", "main", "resources", "assets", "compressedblocks")
RES = "src/main/resources"

SUBS = [os.path.join(ROOT, "1.21.11", "addon", "fabric"),
        os.path.join(ROOT, "1.21.11", "addon", "neoforge")]

# 1~9 重压缩刷石机：tier = 产物压缩等级（1x..9x 压缩圆石）
GEN_TIERS = range(1, 10)
ZH_DIGITS = {1: "一", 2: "二", 3: "三", 4: "四", 5: "五", 6: "六", 7: "七", 8: "八", 9: "九"}

# 压缩盆栽（普通/漏斗）
POTS = [
    ("compressed_pot", "Pot", "压缩盆栽"),
    ("compressed_hopper_pot", "Compressed Hopper Pot", "压缩漏斗盆栽"),
]


def generator_name(tier):
    return f"{tier}x_cobblestone_generator"


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


def darker(img, f=0.62):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (int(r * f), int(g * f), int(b * f), a)
    return out


def advancement(recipe_id, ingredient):
    """配方解锁进度：与主 mod 结构一致（has_the_recipe + has_ingredient）。"""
    return {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                               "conditions": {"recipe": recipe_id}},
            "has_ingredient": {"trigger": "minecraft:inventory_changed",
                               "conditions": {"items": [{"items": ingredient}]}},
        },
        "requirements": [["has_ingredient", "has_the_recipe"]],
        "rewards": {"recipes": [recipe_id]},
    }


def write_generators(assets, data):
    """9 档压缩刷石机：贴图、blockstate、方块/物品模型、items、战利品表、配方与进度。"""
    texdir = os.path.join(assets, "textures", "block")
    for tier in GEN_TIERS:
        name = generator_name(tier)
        # 贴图：与主 mod 同压缩等级的刷石机视觉一致（1x = 原版级刷石机的环，属预期）
        side, top, bottom = gen_assets.generator_textures(tier)
        asset_io.save_png(side, os.path.join(texdir, name + ".png"))
        asset_io.save_png(top, os.path.join(texdir, name + "_top.png"))
        asset_io.save_png(bottom, os.path.join(texdir, name + "_bottom.png"))

        dump(os.path.join(assets, "blockstates", name + ".json"),
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(os.path.join(assets, "models", "block", name + ".json"), {
            "parent": "minecraft:block/cube",
            "textures": {
                "up": f"{NS}:block/{name}_top",
                "down": f"{NS}:block/{name}_bottom",
                "north": f"{NS}:block/{name}",
                "south": f"{NS}:block/{name}",
                "west": f"{NS}:block/{name}",
                "east": f"{NS}:block/{name}",
                "particle": f"{NS}:block/{name}",
            },
        })
        dump(os.path.join(assets, "models", "item", name + ".json"),
             {"parent": f"{NS}:block/{name}"})
        dump(os.path.join(assets, "items", name + ".json"),
             {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})

        dump(os.path.join(data, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{name}",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                       "conditions": [{"condition": "minecraft:survives_explosion"}],
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}],
        })

        # 合成：1x = 3×3 主 mod 刷石机；nx = 3×3 的 (n-1)x；拆解：1 个 nx → 9 个 (n-1)x
        if tier == 1:
            ingredient = f"{MAIN_NS}:cobblestone_generator"
            unpack_result = f"{MAIN_NS}:cobblestone_generator"
        else:
            ingredient = f"{NS}:{generator_name(tier - 1)}"
            unpack_result = f"{NS}:{generator_name(tier - 1)}"

        recipe_id = f"{NS}:{name}"
        dump(os.path.join(data, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "building",
            "pattern": ["PPP", "PPP", "PPP"],
            "key": {"P": ingredient},
            "result": {"count": 1, "id": f"{NS}:{name}"},
        })
        dump(os.path.join(data, "advancement", "recipes", name + ".json"),
             advancement(recipe_id, ingredient))

        unpack_id = f"{NS}:unpack_{name}"
        dump(os.path.join(data, "recipe", f"unpack_{name}.json"), {
            "type": "minecraft:crafting_shapeless", "category": "building",
            "ingredients": [f"{NS}:{name}"],
            "result": {"count": 9, "id": unpack_result},
        })
        dump(os.path.join(data, "advancement", "recipes", f"unpack_{name}.json"),
             advancement(unpack_id, f"{NS}:{name}"))


def write_pots(assets, data):
    """压缩盆栽/漏斗盆栽：贴图与模型（沿用 0.3.x 外观，贴图从主 mod 盆栽贴图加深 0.62）。"""
    main_tex = os.path.join(MAIN, "textures", "block")
    side = Image.open(os.path.join(main_tex, "pot_side.png")).convert("RGBA")
    top = Image.open(os.path.join(main_tex, "pot_top.png")).convert("RGBA")
    bottom = Image.open(os.path.join(main_tex, "pot_bottom.png")).convert("RGBA")
    texdir = os.path.join(assets, "textures", "block")

    d_side = darker(side)
    d_top = darker(top)
    d_bottom = darker(bottom)
    asset_io.save_png(d_side, os.path.join(texdir, "compressed_pot_side.png"))
    asset_io.save_png(d_top, os.path.join(texdir, "compressed_pot_top.png"))
    asset_io.save_png(d_bottom, os.path.join(texdir, "compressed_pot_bottom.png"))
    # 漏斗盆栽：底部（贴图行 0-4 = 底板+腰身）铁色、最底 1px 黑线（漏斗特征）；底面整张铁
    iron_src = os.path.join(main_tex, "cobblestone_generator_bottom.png")
    iron = Image.open(iron_src).convert("RGBA") if os.path.exists(iron_src) else None
    hop = d_side.copy()
    pxh = hop.load()
    if iron is not None:
        hop.paste(iron.crop((0, 0, 16, 4)), (0, 0))
    for x in range(16):
        pxh[x, 0] = (10, 10, 10, 255)
    asset_io.save_png(hop, os.path.join(texdir, "compressed_hopper_pot_side.png"))
    if iron is not None:
        asset_io.save_png(iron, os.path.join(texdir, "compressed_hopper_pot_bottom.png"))
    else:
        asset_io.save_png(gen_assets.darken(bottom, 0.7),
                          os.path.join(texdir, "compressed_hopper_pot_bottom.png"))

    for name, _en, _zh in POTS:
        hopper = name == "compressed_hopper_pot"
        side_tex = f"{NS}:block/compressed_hopper_pot_side" if hopper else f"{NS}:block/compressed_pot_side"
        bottom_tex = (f"{NS}:block/compressed_hopper_pot_bottom" if hopper
                      else f"{NS}:block/compressed_pot_bottom")
        dump(os.path.join(assets, "blockstates", name + ".json"),
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(os.path.join(assets, "models", "block", name + ".json"), {
            "gui_light": "side",
            "display": pot_display(),
            "textures": {"particle": side_tex, "side": side_tex,
                         "top": f"{NS}:block/compressed_pot_top",
                         "bottom": bottom_tex},
            "elements": pot_elements(),
        })
        dump(os.path.join(assets, "models", "item", name + ".json"),
             {"parent": f"{NS}:block/{name}"})
        dump(os.path.join(assets, "items", name + ".json"),
             {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        dump(os.path.join(data, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{name}",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                       "conditions": [{"condition": "minecraft:survives_explosion"}],
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}],
        })

    # 配方（保持原版设计不变）：压缩盆栽 = 5× 任意一重压缩方块（船形，主 mod pot_material 标签）；
    # 漏斗盆栽 = 压缩盆栽 + 漏斗（无序）
    dump(os.path.join(data, "recipe", "compressed_pot.json"), {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["A A", "AAA"],
        "key": {"A": f"#{MAIN_NS}:pot_material"},
        "result": {"count": 1, "id": f"{NS}:compressed_pot"},
    })
    dump(os.path.join(data, "recipe", "compressed_hopper_pot.json"), {
        "type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": [f"{NS}:compressed_pot", "minecraft:hopper"],
        "result": {"count": 1, "id": f"{NS}:compressed_hopper_pot"},
    })


def write_tags(resources):
    """挖掘标签：9 台刷石机 + 2 盆栽可被镐采掘；2 盆栽也可被斧采掘。

    注意 namespace 是 minecraft（不是 compressedblockspot），路径必须是
    data/minecraft/tags/block/mineable/<tool>.json —— 历史错误路径一并清理。
    """
    generator_blocks = [f"{NS}:{generator_name(tier)}" for tier in GEN_TIERS]
    pot_blocks = [f"{NS}:{name}" for name, _en, _zh in POTS]
    tags = {
        "pickaxe": generator_blocks + pot_blocks,
        "axe": pot_blocks,
    }
    for tool, values in tags.items():
        dump(os.path.join(resources, "data", "minecraft", "tags", "block", "mineable", tool + ".json"),
             {"replace": False, "values": values})

    # 清理历史错误位置（data/compressedblockspot/minecraft/tags/... 不会被游戏读取）
    stale = os.path.join(resources, "data", NS, "minecraft")
    if os.path.isdir(stale):
        shutil.rmtree(stale)


def write_lang(assets):
    en = {
        "itemGroup.compressedblockspot.addon_blocks": "Addon Blocks",
        "itemGroup.compressedblockspot.addon_tools": "Addon Tools",
    }
    zh = {
        "itemGroup.compressedblockspot.addon_blocks": "压缩拓展方块",
        "itemGroup.compressedblockspot.addon_tools": "压缩拓展工具",
    }
    for name, en_name, zh_name in POTS:
        en[f"block.{NS}.{name}"] = en_name
        zh[f"block.{NS}.{name}"] = zh_name
    for tier in GEN_TIERS:
        key = f"block.{NS}.{generator_name(tier)}"
        en[key] = f"{tier}x Compressed Cobblestone Generator"
        zh[key] = f"{ZH_DIGITS[tier]}重压缩刷石机"

    dump(os.path.join(assets, "lang", "en_us.json"), en, indent=2)
    dump(os.path.join(assets, "lang", "zh_cn.json"), zh, indent=2)


def main():
    for sub in SUBS:
        resources = os.path.join(sub, RES)
        assets = os.path.join(resources, "assets", NS)
        data = os.path.join(resources, "data", NS)
        write_pots(assets, data)
        write_generators(assets, data)
        write_tags(resources)
        write_lang(assets)
        print(f"addon resources -> {sub}")


if __name__ == "__main__":
    main()
