#!/usr/bin/env python3
"""压缩方块 mod 资源生成器：blockstate/模型/物品模型定义/配方/战利品表/标签/语言文件/装备资产。
输出必须与仓库内容逐字节一致：重跑本脚本后 git status 应保持为空。
生成前会清空各子项目的 assets/compressedblocks 与 data（生成目录整体归本脚本所有）。

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
import json
import os
import shutil
from pathlib import Path

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "compressedblocks"
VANILLA_DATA = os.path.join(ROOT, "_asset-src", "vanilla-data")
LEGACY = False  # 1.21.1 冻结在 0.2.0，不再生成

LEVELS = [
    ("1x", "Compressed", "压缩"),
    ("2x", "Double Compressed", "二重压缩"),
    ("3x", "Triple Compressed", "三重压缩"),
    ("4x", "Quadruple Compressed", "四重压缩"),
    ("5x", "Quintuple Compressed", "五重压缩"),
    ("6x", "Sextuple Compressed", "六重压缩"),
    ("7x", "Septuple Compressed", "七重压缩"),
    ("8x", "Octuple Compressed", "八重压缩"),
    ("9x", "Nonuple Compressed", "九重压缩"),
]
LV = [l[0] for l in LEVELS]

# 储存方块材料（key, en, zh）
MATERIALS = [
    ("cobblestone", "Cobblestone", "圆石"),
    ("stone", "Stone", "石头"),
    ("cobbled_deepslate", "Cobbled Deepslate", "深板岩圆石"),
    ("deepslate", "Deepslate", "深板岩"),
    ("oak_log", "Oak Log", "橡木原木"),
    ("spruce_log", "Spruce Log", "云杉原木"),
    ("birch_log", "Birch Log", "白桦原木"),
    ("jungle_log", "Jungle Log", "丛林原木"),
    ("acacia_log", "Acacia Log", "金合欢原木"),
    ("dark_oak_log", "Dark Oak Log", "深色橡木原木"),
    ("mangrove_log", "Mangrove Log", "红树原木"),
    ("cherry_log", "Cherry Log", "樱花原木"),
    ("pale_oak_log", "Pale Oak Log", "苍白橡木原木"),
    ("dirt", "Dirt", "泥土"),
    ("sand", "Sand", "沙子"),
    ("gravel", "Gravel", "沙砾"),
    ("netherrack", "Netherrack", "下界岩"),
    ("end_stone", "End Stone", "末地石"),
    ("obsidian", "Obsidian", "黑曜石"),
    # 原版矿物块（储存用，无工具线）：压缩矿物甘蔗的核心方块
    ("coal_block", "Block of Coal", "煤炭块"),
    ("copper_block", "Block of Copper", "铜块"),
    ("iron_block", "Block of Iron", "铁块"),
    ("lapis_block", "Lapis Lazuli Block", "青金石块"),
    ("gold_block", "Block of Gold", "金块"),
    ("redstone_block", "Block of Redstone", "红石块"),
    ("emerald_block", "Block of Emerald", "绿宝石块"),
    ("diamond_block", "Block of Diamond", "钻石块"),
]

# 工具只有两条线：压缩圆石工具（圆石+深板岩圆石混用）、压缩木质工具（9 原木混用）
TOOL_LINES = [
    ("cobblestone", "Cobblestone", "圆石"),
    ("wood", "Wooden", "木"),
]

# 0.3.0 新建材（key, en, zh, mineable, needs: None|stone|iron）
BUILD_MATERIALS = [
    ("granite", "Granite", "花岗岩", "pickaxe", "stone"),
    ("diorite", "Diorite", "闪长岩", "pickaxe", "stone"),
    ("andesite", "Andesite", "安山岩", "pickaxe", "stone"),
    ("calcite", "Calcite", "方解石", "pickaxe", "stone"),
    ("tuff", "Tuff", "凝灰岩", "pickaxe", "stone"),
    ("sandstone", "Sandstone", "砂岩", "pickaxe", None),
    ("red_sandstone", "Red Sandstone", "红砂岩", "pickaxe", None),
    ("basalt", "Basalt", "玄武岩", "pickaxe", "stone"),
    ("blackstone", "Blackstone", "黑石", "pickaxe", None),
    ("dripstone_block", "Dripstone Block", "钟乳石", "pickaxe", None),
    ("terracotta", "Terracotta", "陶瓦", "pickaxe", None),
    ("quartz_block", "Quartz Block", "石英块", "pickaxe", None),
    ("purpur_block", "Purpur Block", "紫珀块", "pickaxe", None),
    ("prismarine", "Prismarine", "海晶石", "pickaxe", None),
    ("amethyst_block", "Amethyst Block", "紫水晶块", "pickaxe", "iron"),
    ("glowstone", "Glowstone", "荧石", "pickaxe", None),
    ("clay", "Clay", "黏土块", "shovel", None),
    ("hay_block", "Hay Bale", "干草块", "hoe", None),
    ("bone_block", "Bone Block", "骨块", "pickaxe", None),
    ("moss_block", "Moss Block", "苔藓块", "hoe", None),
    ("snow", "Snow Block", "雪块", "shovel", None),
    ("blue_ice", "Blue Ice", "蓝冰", "pickaxe", None),
    ("mud", "Mud", "泥巴", "shovel", None),
    # 原版无方块形态：物品 sprite 合成方块底图（压缩火药块，甘蔗/储存链用）
    ("gunpowder", "Block of Gunpowder", "火药块", "shovel", None),
]

WOODS = [
    ("oak", "Oak", "橡树"),
    ("spruce", "Spruce", "云杉"),
    ("birch", "Birch", "白桦"),
    ("jungle", "Jungle", "丛林"),
    ("acacia", "Acacia", "金合欢"),
    ("dark_oak", "Dark Oak", "深色橡树"),
    ("mangrove", "Mangrove", "红树"),
    ("cherry", "Cherry", "樱花"),
    ("pale_oak", "Pale Oak", "苍白橡树"),
]

# 作物（key, en, zh, 阶段数, 有种子）。种子/产物物品 id 规则见 crop_item/crop_block
CROPS = [
    ("wheat", "Wheat", "小麦", 8, True),
    ("carrot", "Carrot", "胡萝卜", 4, False),
    ("potato", "Potato", "马铃薯", 4, False),
    ("beetroot", "Beetroot", "甜菜根", 4, True),
]
CROP_MAX_LEVEL = 3

FOODS = [
    ("bread", "Bread", "面包"),
    ("beef", "Beef", "牛肉"),
    ("melon", "Watermelon", "西瓜"),
    ("rotten_flesh", "Rotten Flesh", "腐肉"),
]
FOOD_MAX_LEVEL = 3

ARMOR_PIECES = ["helmet", "chestplate", "leggings", "boots"]
ARMOR_PATTERNS = {
    "helmet": ["XXX", "X X"],
    "chestplate": ["X X", "XXX", "XXX"],
    "leggings": ["XXX", "X X", "X X"],
    "boots": ["X X", "X X"],
}
ARMOR_ZH = {"helmet": "头盔", "chestplate": "胸甲", "leggings": "护腿", "boots": "靴子"}
ARMOR_EN = {"helmet": "Helmet", "chestplate": "Chestplate", "leggings": "Leggings", "boots": "Boots"}

TOOLS = {
    "pickaxe": (["XXX", " S ", " S "], "镐"),
    "axe": (["XX", "XS", " S"], "斧"),
    "shovel": (["X", "S", "S"], "锹"),
    "hoe": (["XX", " S", " S"], "锄"),
    "sword": (["X", "X", "S"], "剑"),
}
# 作物 age→阶段贴图（小麦 8 段直映；其余 4 段两 age 一段）
AGE_TO_STAGE = {0: 0, 1: 0, 2: 1, 3: 1, 4: 2, 5: 2, 6: 3, 7: 3}


def dump(path, obj, indent=None):
    text = json.dumps(obj, ensure_ascii=False, separators=(",", ":"), indent=indent) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def dump_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def storage_materials():
    return [(m[0], m[1], m[2]) for m in MATERIALS] + [(m[0], m[1], m[2]) for m in BUILD_MATERIALS]


def storage_names():
    return [f"{p}_{m[0]}" for m in storage_materials() for p in LV]


def leaves_names():
    return [f"{p}_{w[0]}_leaves" for w in WOODS for p in LV]


def sapling_names():
    return [f"{p}_{w[0]}_sapling" for w in WOODS for p in LV]


def farmland_names():
    return [f"{p}_farmland" for p in LV]


def crop_block_names():
    return [f"{p}_{c[0]}_plant" for c in CROPS for p in LV[:CROP_MAX_LEVEL]]


def crop_item_id(crop_key, p):
    """作物方块的 BlockItem id（种子的种，胡萝卜/土豆产物即种子）。"""
    if crop_key == "wheat":
        return f"{p}_wheat_seeds"
    if crop_key == "beetroot":
        return f"{p}_beetroot_seeds"
    return f"{p}_{crop_key}"


def tool_names():
    return [f"{p}_{m[0]}_{t}" for m in TOOL_LINES for p in LV for t in TOOLS]


def armor_names():
    return [f"{p}_{line}_{piece}" for line in ("stone", "wood") for p in LV for piece in ARMOR_PIECES]


def food_names():
    return [f"{p}_{f[0]}" for f in FOODS for p in LV[:FOOD_MAX_LEVEL]]


def crop_produce_names():
    """非方块物品的作物产物：压缩小麦、压缩甜菜根（胡萝卜/土豆产物=BlockItem）。"""
    return [f"{p}_wheat" for p in LV[:CROP_MAX_LEVEL]] + [f"{p}_beetroot" for p in LV[:CROP_MAX_LEVEL]]


def stick_names():
    return [f"{p}_stick" for p in LV]


# 更多压缩种子（原版其余种子 × 3 级）：盆栽通用兼容可直接种植（#minecraft:crops）
EXTRA_SEEDS = ["pumpkin_seeds", "melon_seeds", "torchflower_seeds", "pitcher_pod"]


def extra_seed_names():
    return [f"{p}_{k}" for k in EXTRA_SEEDS for p in LV[:CROP_MAX_LEVEL]]


# 压缩甘蔗全材料化：纯甘蔗（1x_cane）+ 每种储存材料一条甘蔗线。
# id：材料 key 去掉 "_block" 后缀 + "_cane"（coal_block → coal_cane，全部唯一）。
# 显示名：去掉 "Block of " 前缀 / " Block" / " Bale" 后缀（骨块保留，用户命名 骨块甘蔗）。


def cane_key(mat_key):
    return mat_key[:-6] if mat_key.endswith("_block") else mat_key


def cane_en(mat_en):
    if mat_en.startswith("Block of "):
        return mat_en[len("Block of "):]
    if mat_en.endswith(" Block"):
        return mat_en[: -len(" Block")]
    if mat_en.endswith(" Bale"):
        return mat_en[: -len(" Bale")]
    return mat_en


def cane_zh(mat):
    if mat[0] == "bone_block":
        return mat[2]
    return mat[2][:-1] if mat[2].endswith("块") else mat[2]


def cane_names():
    out = [f"{p}_cane" for p in LV]
    for mat in storage_materials():
        key = cane_key(mat[0])
        out += [f"{p}_{key}_cane" for p in LV]
    return out


def pot_names():
    return ["pot", "hopper_pot"]


# ---------------------------------------------------------------- assets

def gen_assets(res):
    # 储存方块/树叶：cube_all + blockstate + 物品模型定义
    leaf_set = set(leaves_names())
    for name in storage_names() + leaves_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        if name in leaf_set:
            # 树叶带透明像素：必须声明 cutout_mipped（1.21.4+ 无 render_type 默认 SOLID 不透明）
            dump(f"{res}/models/block/{name}.json",
                 {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                  "textures": {"all": f"{NS}:block/{name}"}})
        else:
            dump(f"{res}/models/block/{name}.json",
                 {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    # 树苗：cross，stage 0/1 同模型；透明模型必须声明 render_type（1.21.4+ 默认 SOLID 不透明）
    for name in sapling_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"stage=0": {"model": f"{NS}:block/{name}"},
                           "stage=1": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
              "textures": {"cross": f"{NS}:block/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    # 压缩甘蔗：cross 单模型（age 属性不换图）
    for name in cane_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
              "textures": {"cross": f"{NS}:block/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    # 压缩盆栽（普通/漏斗）：12×12 底面、6px 高、1px 薄壁，
    # display 挂原版 block/block 的 GUI 等距视角（物品图标 3D 显示），土壤/作物由 BER 绘制
    pot_display = {
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
    for name in pot_names():
        hopper = name == "hopper_pot"
        side = f"{NS}:block/{'hopper_pot_side' if hopper else 'pot_side'}"
        model = {
            "gui_light": "side",
            "display": pot_display,
            "textures": {"particle": side, "side": side,
                         "top": f"{NS}:block/pot_top", "bottom": f"{NS}:block/pot_bottom"},
            "elements": [
                {"from": [2, 0, 2], "to": [14, 6, 3],
                 "faces": {"down": {"texture": "#bottom", "cullface": "down"},
                           "north": {"texture": "#side", "cullface": "north"},
                           "south": {"texture": "#side"}, "west": {"texture": "#side"},
                           "east": {"texture": "#side"}, "up": {"texture": "#top"}}},
                {"from": [2, 0, 13], "to": [14, 6, 14],
                 "faces": {"down": {"texture": "#bottom", "cullface": "down"},
                           "north": {"texture": "#side"}, "south": {"texture": "#side", "cullface": "south"},
                           "west": {"texture": "#side"}, "east": {"texture": "#side"},
                           "up": {"texture": "#top"}}},
                {"from": [2, 0, 3], "to": [3, 6, 13],
                 "faces": {"down": {"texture": "#bottom", "cullface": "down"},
                           "north": {"texture": "#side"}, "south": {"texture": "#side"},
                           "west": {"texture": "#side", "cullface": "west"},
                           "east": {"texture": "#side"}, "up": {"texture": "#top"}}},
                {"from": [13, 0, 3], "to": [14, 6, 13],
                 "faces": {"down": {"texture": "#bottom", "cullface": "down"},
                           "north": {"texture": "#side"}, "south": {"texture": "#side"},
                           "west": {"texture": "#side"},
                           "east": {"texture": "#side", "cullface": "east"},
                           "up": {"texture": "#top"}}},
            ],
        }
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json", model)
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    # 耕地：15/16 模板，无物品
    for name in farmland_names():
        p = name.split("_", 1)[0]
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/template_farmland",
              "textures": {"dirt": f"{NS}:block/{p}_dirt", "top": f"{NS}:block/{name}"}})
    # 作物：age 0..7 → 阶段 cross
    for crop, _, _, stages, _ in CROPS:
        for p in LV[:CROP_MAX_LEVEL]:
            block = f"{p}_{crop}_plant"
            variants = {}
            for age in range(8):
                stage = age if stages == 8 else AGE_TO_STAGE[age]
                variants[f"age={age}"] = {"model": f"{NS}:block/{p}_{crop}_stage{stage}"}
            dump(f"{res}/blockstates/{block}.json", {"variants": variants})
            for n in range(stages):
                dump(f"{res}/models/block/{p}_{crop}_stage{n}.json",
                     {"parent": "minecraft:block/crop", "render_type": "minecraft:cutout",
                      "textures": {"crop": f"{NS}:block/{p}_{crop}_stage{n}"}})
            # 作物 BlockItem：物品模型
            dump(f"{res}/items/{crop_item_id(crop, p)}.json",
                 {"model": {"type": "minecraft:model", "model": f"{NS}:item/{crop_item_id(crop, p)}"}})
            dump(f"{res}/models/item/{crop_item_id(crop, p)}.json",
                 {"parent": "minecraft:item/generated",
                  "textures": {"layer0": f"{NS}:item/{crop_item_id(crop, p)}"}})
    # 纯物品模型
    simple = (tool_names() + armor_names() + food_names() + crop_produce_names() + stick_names()
              + extra_seed_names())
    for name in simple:
        parent = "minecraft:item/handheld" if name in set(tool_names()) else "minecraft:item/generated"
        dump(f"{res}/models/item/{name}.json",
             {"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    # 装备资产（1.21.2+ 盔甲贴图走 equipment 系统）
    for p in LV:
        dump(f"{res}/equipment/stone_{p}.json",
             {"layers": {"humanoid": [{"texture": f"{NS}:stone_{p}"}],
                         "humanoid_leggings": [{"texture": f"{NS}:stone_{p}"}]}})
        dump(f"{res}/equipment/wood_{p}.json",
             {"layers": {"humanoid": [{"texture": f"{NS}:wood_{p}"}],
                         "humanoid_leggings": [{"texture": f"{NS}:wood_{p}"}]}})
    icon_src = os.path.join(ROOT, "_asset-src", "icon.png")
    shutil.copyfile(icon_src, f"{res}/icon.png")


# ---------------------------------------------------------------- lang

def gen_lang(res):
    en, zh = {}, {}
    EN_TOOLS = {"pickaxe": "Pickaxe", "axe": "Axe", "shovel": "Shovel", "hoe": "Hoe", "sword": "Sword"}
    tool_set = set(tool_names())
    tool_mats = {m[0]: m for m in TOOL_LINES}
    for name in tool_names():
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        rest = name[len(lv[0]) + 1:]
        mat_key, tool_id = rest.rsplit("_", 1)
        mat = tool_mats[mat_key]
        en[f"item.{NS}.{name}"] = f"{lv[1]} {mat[1]} {EN_TOOLS[tool_id]}"
        zh[f"item.{NS}.{name}"] = f"{lv[2]}{mat[2]}{TOOLS[tool_id][1]}"
    storage = storage_materials()
    for name in storage_names():
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        mat = next(m for m in storage if m[0] == name[len(lv[0]) + 1:])
        en[f"block.{NS}.{name}"] = f"{lv[1]} {mat[1]}"
        zh[f"block.{NS}.{name}"] = f"{lv[2]}{mat[2]}"
    for name in leaves_names():
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        wood = next(w for w in WOODS if name[len(lv[0]) + 1:].startswith(w[0] + "_"))
        en[f"block.{NS}.{name}"] = f"{lv[1]} {wood[1]} Leaves"
        zh[f"block.{NS}.{name}"] = f"{lv[2]}{wood[2]}树叶"
    for name in sapling_names():
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        wood = next(w for w in WOODS if name[len(lv[0]) + 1:].startswith(w[0] + "_"))
        en[f"block.{NS}.{name}"] = f"{lv[1]} {wood[1]} Sapling"
        zh[f"block.{NS}.{name}"] = f"{lv[2]}{wood[2]}树苗"
    for name in farmland_names():
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        en[f"block.{NS}.{name}"] = f"{lv[1]} Farmland"
        zh[f"block.{NS}.{name}"] = f"{lv[2]}耕地"
    storage_mats = storage_materials()
    cane_mat_of_key = {cane_key(m[0]): m for m in storage_mats}
    for name in cane_names():
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        rest = name[len(lv[0]) + 1:]
        if rest == "cane":
            en[f"block.{NS}.{name}"] = f"{lv[1]} Sugar Cane"
            zh[f"block.{NS}.{name}"] = f"{lv[2]}甘蔗"
        else:
            mat = cane_mat_of_key[rest[:-5]]
            # 重数前缀已含「压缩」：不要重复拼接
            en[f"block.{NS}.{name}"] = f"{lv[1]} {cane_en(mat[1])} Cane"
            zh[f"block.{NS}.{name}"] = f"{lv[2]}{cane_zh(mat)}甘蔗"
    en["block." + NS + ".pot"] = "Compressed Pot"
    zh["block." + NS + ".pot"] = "压缩盆栽"
    en["block." + NS + ".hopper_pot"] = "Compressed Hopper Pot"
    zh["block." + NS + ".hopper_pot"] = "压缩漏斗盆栽"
    crop_en = {c[0]: c[1] for c in CROPS}
    crop_zh = {c[0]: c[2] for c in CROPS}
    for name in crop_block_names():
        p, crop, _ = name.split("_", 2)
        lv = next(l for l in LEVELS if l[0] == p)
        en[f"block.{NS}.{name}"] = f"{lv[1]} {crop_en[crop]} Plant"
        zh[f"block.{NS}.{name}"] = f"{lv[2]}{crop_zh[crop]}植株"
    for crop, en_name, zh_name, _, has_seed in CROPS:
        for p in LV[:CROP_MAX_LEVEL]:
            lv = next(l for l in LEVELS if l[0] == p)
            item = crop_item_id(crop, p)
            if has_seed:
                en[f"item.{NS}.{item}"] = f"{lv[1]} Compressed {en_name} Seeds"
                zh[f"item.{NS}.{item}"] = f"{lv[2]}{zh_name}种子"
            else:
                en[f"item.{NS}.{item}"] = f"{lv[1]} Compressed {en_name}"
                zh[f"item.{NS}.{item}"] = f"{lv[2]}{zh_name}"
    for p in LV[:CROP_MAX_LEVEL]:
        lv = next(l for l in LEVELS if l[0] == p)
        en[f"item.{NS}.{p}_wheat"] = f"{lv[1]} Wheat"
        zh[f"item.{NS}.{p}_wheat"] = f"{lv[2]}小麦"
        en[f"item.{NS}.{p}_beetroot"] = f"{lv[1]} Beetroot"
        zh[f"item.{NS}.{p}_beetroot"] = f"{lv[2]}甜菜根"
    food_en = {f[0]: f[1] for f in FOODS}
    food_zh = {f[0]: f[2] for f in FOODS}
    for name in food_names():
        p, food = name.split("_", 1)
        lv = next(l for l in LEVELS if l[0] == p)
        en[f"item.{NS}.{name}"] = f"{lv[1]} {food_en[food]}"
        zh[f"item.{NS}.{name}"] = f"{lv[2]}{food_zh[food]}"
    for name in armor_names():
        p, _, piece = name.split("_", 2)
        lv = next(l for l in LEVELS if l[0] == p)
        en[f"item.{NS}.{name}"] = f"{lv[1]} Stone {ARMOR_EN[piece]}"
        zh[f"item.{NS}.{name}"] = f"{lv[2]}石头{ARMOR_ZH[piece]}"
    for name in armor_names():
        if "_wood_" in name:
            p = name.split("_", 1)[0]
            piece = name.split("_wood_")[1]
            lv = next(l for l in LEVELS if l[0] == p)
            en[f"item.{NS}.{name}"] = f"{lv[1]} Wooden {ARMOR_EN[piece]}"
            zh[f"item.{NS}.{name}"] = f"{lv[2]}木质{ARMOR_ZH[piece]}"
    for name in stick_names():
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        en[f"item.{NS}.{name}"] = f"{lv[1]} Stick"
        zh[f"item.{NS}.{name}"] = f"{lv[2]}木棍"
    extra_seed_zh = {"pumpkin_seeds": "南瓜种子", "melon_seeds": "西瓜种子",
                     "torchflower_seeds": "火把花种子", "pitcher_pod": "瓶子草荚果"}
    extra_seed_en = {"pumpkin_seeds": "Pumpkin Seeds", "melon_seeds": "Melon Seeds",
                     "torchflower_seeds": "Torchflower Seeds", "pitcher_pod": "Pitcher Pod"}
    for name in extra_seed_names():
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        key = name[len(lv[0]) + 1:]
        en[f"item.{NS}.{name}"] = f"{lv[1]} {extra_seed_en[key]}"
        zh[f"item.{NS}.{name}"] = f"{lv[2]}{extra_seed_zh[key]}"
    en["itemGroup.compressedblocks.blocks"] = "Compressed Blocks"
    zh["itemGroup.compressedblocks.blocks"] = "压缩方块"
    en["itemGroup.compressedblocks.tools"] = "Compressed Tools"
    zh["itemGroup.compressedblocks.tools"] = "压缩工具"
    en["itemGroup.compressedblocks.food"] = "Compressed Food"
    zh["itemGroup.compressedblocks.food"] = "压缩食物"
    en["itemGroup.compressedblocks.pots"] = "Compressed Pots"
    zh["itemGroup.compressedblocks.pots"] = "压缩盆栽"
    dump(f"{res}/lang/en_us.json", dict(sorted(en.items())), indent=2)
    dump(f"{res}/lang/zh_cn.json", dict(sorted(zh.items())), indent=2)


# ---------------------------------------------------------------- data

def gen_data(data):
    def unlock_advancement(path, result, ingredients):
        """配方解锁进度：没有它配方书永不显示（1.21 配方需 advancement rewards）。"""
        first = ingredients[0]
        dump(f"{data}/{NS}/advancement/recipes/{path}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                                   "conditions": {"recipe": f"{NS}:{path}"}},
                "has_ingredient": {"trigger": "minecraft:inventory_changed",
                                   "conditions": {"items": [{"items": first}]}},
            },
            "requirements": [["has_ingredient", "has_the_recipe"]],
            "rewards": {"recipes": [f"{NS}:{path}"]},
        })

    def shaped(path, pattern, key, result, count=1, category="building"):
        dump(f"{data}/{NS}/recipe/{path}.json", {
            "type": "minecraft:crafting_shaped", "category": category,
            "pattern": pattern, "key": key,
            "result": {"count": count, "id": result},
        })
        unlock_advancement(path, result, list(dict.fromkeys(
            ing for ing in key.values() if isinstance(ing, str))))

    def shapeless(path, ingredients, result, count=1, category="building"):
        dump(f"{data}/{NS}/recipe/{path}.json", {
            "type": "minecraft:crafting_shapeless", "category": category,
            "ingredients": ingredients,
            "result": {"count": count, "id": result},
        })
        unlock_advancement(path, result, list(dict.fromkeys(
            ing for ing in ingredients if isinstance(ing, str))))

    def compress(cur, prev, count=9):
        """3×3 压缩（prev 可为物品或配料列表）。"""
        shaped(cur.split(":", 1)[1], ["PPP", "PPP", "PPP"], {"P": prev}, cur)

    def unpack(cur, prev, count=9):
        shapeless("unpack_" + cur.split(":", 1)[1], [cur], prev, count)

    # ---- 储存方块与木棍：9↔1 压缩/解压链
    for mat, _, _ in storage_materials() + [("stick", "", "")]:
        for i, p in enumerate(LV):
            cur = f"{NS}:{p}_{mat}"
            prev = f"minecraft:{mat}" if i == 0 else f"{NS}:{LV[i - 1]}_{mat}"
            compress(cur, prev)
            unpack(cur, prev)
    # ---- 树苗/树叶：9×原版 → 1x，逐级
    for wood, _, _ in WOODS:
        for kind, vanilla in (("sapling", f"minecraft:{wood}_sapling"
                               if wood != "mangrove" else "minecraft:mangrove_propagule"),
                              ("leaves", f"minecraft:{wood}_leaves")):
            for i, p in enumerate(LV):
                cur = f"{NS}:{p}_{wood}_{kind}"
                prev = vanilla if i == 0 else f"{NS}:{LV[i - 1]}_{wood}_{kind}"
                compress(cur, prev)
                unpack(cur, prev)
    # ---- 作物（3 级）：小麦/西瓜类走原版中间块（干草块/西瓜块），其余 9↔1
    for i, p in enumerate(LV[:CROP_MAX_LEVEL]):
        cur = f"{NS}:{p}_wheat"
        if i == 0:
            unpack(cur, "minecraft:wheat")           # 1x 分解 → 9 小麦；不可用小麦合成
        elif i == 1:
            compress(cur, "minecraft:hay_block")      # 9 干草块 → 2x（原版 9 麦=干草块）
            unpack(cur, f"{NS}:{LV[i - 1]}_wheat")
        else:
            compress(cur, f"{NS}:{LV[i - 1]}_wheat")
            unpack(cur, f"{NS}:{LV[i - 1]}_wheat")
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        cur = f"{NS}:{p}_melon"
        if i == 0:
            # 不可从西瓜片合成：否则与原版“9 西瓜片→西瓜块”同形抢占，导致西瓜块无法合成。
            # 链条：西瓜片→(原版)西瓜块→二重西瓜→分解→一重西瓜→分解→西瓜片
            unpack(cur, "minecraft:melon_slice")
        elif i == 1:
            compress(cur, "minecraft:melon")          # 9 西瓜块 → 2x（原版 9 片=西瓜块）
            shaped(f"{p}_melon_from_compressed", ["PPP", "PPP", "PPP"],
                   {"P": f"{NS}:1x_melon"}, cur)      # 9×1x → 2x
            unpack(cur, f"{NS}:{LV[i - 1]}_melon")
        else:
            compress(cur, f"{NS}:{LV[i - 1]}_melon")
            unpack(cur, f"{NS}:{LV[i - 1]}_melon")
    for crop in ("carrot", "potato", "beetroot"):
        for i, p in enumerate(LV[:CROP_MAX_LEVEL]):
            cur = f"{NS}:{p}_{crop}"
            prev = f"minecraft:{crop}" if i == 0 else f"{NS}:{LV[i - 1]}_{crop}"
            compress(cur, prev)
            unpack(cur, prev)
    # 压缩种子
    for crop in ("wheat", "beetroot"):
        for i, p in enumerate(LV[:CROP_MAX_LEVEL]):
            cur = f"{NS}:{p}_{crop}_seeds"
            prev = f"minecraft:{crop}_seeds" if i == 0 else f"{NS}:{LV[i - 1]}_{crop}_seeds"
            compress(cur, prev)
            unpack(cur, prev)
    # ---- 更多压缩种子（南瓜/西瓜/火把花/瓶子草荚果 × 3 级）：9↔1
    for seed_id in EXTRA_SEEDS:
        for i, p in enumerate(LV[:CROP_MAX_LEVEL]):
            cur = f"{NS}:{p}_{seed_id}"
            prev = f"minecraft:{seed_id}" if i == 0 else f"{NS}:{LV[i - 1]}_{seed_id}"
            compress(cur, prev)
            unpack(cur, prev)
    # ---- 压缩甘蔗：8 甘蔗 + 核心方块居中 → 1x；9× 升级；1x 可拆回核心方块（矿物甘蔗拆出压缩钻石块）
    # 纯压缩甘蔗：9 甘蔗 ↔ 1x，逐级 9↔1（唯一可用甘蔗合成且可逆分解的甘蔗）
    for i, p in enumerate(LV):
        cur = f"{NS}:{p}_cane"
        if i == 0:
            compress(cur, "minecraft:sugar_cane")
            unpack(cur, "minecraft:sugar_cane")
        else:
            compress(cur, f"{NS}:{LV[i - 1]}_cane")
            unpack(cur, f"{NS}:{LV[i - 1]}_cane")
    # 材料甘蔗（每种储存材料一条线）：1x = 8× 一重压缩甘蔗 + 1× 一重压缩方块居中；
    # 高级 = 9× 低级甘蔗；分解返还同级压缩方块（用户设计：升级链可逆回方块一级）
    for mat in storage_materials():
        key = cane_key(mat[0])
        for i, p in enumerate(LV):
            cur = f"{NS}:{p}_{key}_cane"
            if i == 0:
                shaped(f"{p}_{key}_cane", ["CCC", "CBC", "CCC"],
                       {"C": f"{NS}:1x_cane", "B": f"{NS}:1x_{mat[0]}"}, cur)
                unpack(cur, f"{NS}:1x_{mat[0]}", count=1)
            else:
                compress(cur, f"{NS}:{LV[i - 1]}_{key}_cane")
                unpack(cur, f"{NS}:{p}_{mat[0]}", count=1)
    # ---- 压缩盆栽：5× 任意一重压缩方块（船形）；盆栽+漏斗=漏斗盆栽
    dump(f"{data}/{NS}/tags/item/pot_material.json",
         {"replace": False, "values": sorted(f"{NS}:1x_{m[0]}" for m in storage_materials())})
    shaped("pot", ["A A", "AAA"], {"A": f"#{NS}:pot_material"}, f"{NS}:pot")
    shapeless("hopper_pot", [f"{NS}:pot", "minecraft:hopper"], f"{NS}:hopper_pot",
              category="misc")
    # ---- 食物：面包=3 压缩小麦（原版 3 麦→面包），牛肉=9 肉，西瓜见上
    shaped("1x_bread", ["WWW"], {"W": f"{NS}:1x_wheat"}, f"{NS}:1x_bread", category="misc")
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        cur = f"{NS}:{p}_bread"
        if i > 0:
            compress(cur, f"{NS}:{LV[i - 1]}_bread")
        unpack(cur, "minecraft:bread" if i == 0 else f"{NS}:{LV[i - 1]}_bread")
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        cur = f"{NS}:{p}_beef"
        prev = "minecraft:beef" if i == 0 else f"{NS}:{LV[i - 1]}_beef"
        compress(cur, prev)
        unpack(cur, prev)
    # 压缩腐肉：无饥饿副作用（Java 侧 FoodProperties 不带任何效果），数值 ×9^层
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        cur = f"{NS}:{p}_rotten_flesh"
        prev = "minecraft:rotten_flesh" if i == 0 else f"{NS}:{LV[i - 1]}_rotten_flesh"
        compress(cur, prev)
        unpack(cur, prev)
    # ---- 工具两条线：材料位=同重数混用标签（圆石线=圆石+深板岩圆石，木线=9 原木），棍位=同重数压缩木棍
    for mat, _, _ in TOOL_LINES:
        for p in LV:
            # 1.21.2+ 配方材料只有字符串形式：物品=id，标签=#id（{"tag":...} 已废除，解析会失败）
            key_x = f"#{NS}:{mat}_tool_{p}"
            stick_id = f"{NS}:{p}_stick"
            for tool, (pattern, _) in TOOLS.items():
                name = f"{p}_{mat}_{tool}"
                shaped(name, pattern, {"X": key_x, "S": stick_id}, f"{NS}:{name}", category="equipment")
    # ---- 盔甲：原版盔甲配方 × 同重数压缩石头
    for p in LV:
        stone = f"{NS}:{p}_stone"
        for piece, pattern in ARMOR_PATTERNS.items():
            shaped(f"{p}_stone_{piece}", pattern, {"X": f"#{NS}:stone_armor_{p}"},
                   f"{NS}:{p}_stone_{piece}", category="equipment")
            shaped(f"{p}_wood_{piece}", pattern, {"X": f"#{NS}:wood_tool_{p}"},
                   f"{NS}:{p}_wood_{piece}", category="equipment")
    # ---- 战利品表
    for name in storage_names():
        dump(f"{data}/{NS}/loot_table/blocks/{name}.json", self_drop(name))
    for name in leaves_names():
        gen_leaves_loot(data, name)
    for name in farmland_names():
        p = name.split("_", 1)[0]
        dump(f"{data}/{NS}/loot_table/blocks/{name}.json", self_drop(f"{p}_dirt"))
    for crop, _, _, _, has_seed in CROPS:
        for p in LV[:CROP_MAX_LEVEL]:
            gen_crop_loot(data, crop, p, has_seed)
    # 压缩甘蔗：掉落自身
    for name in cane_names():
        dump(f"{data}/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # 压缩盆栽：掉落自身（盆内土壤/作物由方块实体 onRemove 补发）
    for name in pot_names():
        dump(f"{data}/{NS}/loot_table/blocks/{name}.json", self_drop(name))
    # ---- 方块标签（并入原版命名空间）
    # 所有压缩方块统一镐子采掘；挖掘等级：L1-2 石镐、L3-4 铁镐、L5+ 钻镐封顶
    pickaxe = [f"{NS}:{n}" for n in storage_names()] + [f"{NS}:{n}" for n in pot_names()]
    stone_tool = [f"{NS}:{n}" for n in storage_names() if int(n.split("_", 1)[0][:-1]) <= 2]
    iron_tool = [f"{NS}:{n}" for n in storage_names() if 3 <= int(n.split("_", 1)[0][:-1]) <= 4]
    diamond_tool = [f"{NS}:{n}" for n in storage_names() if int(n.split("_", 1)[0][:-1]) >= 5]
    logs = [f"{NS}:{p}_{w[0]}_log" for w in WOODS for p in LV]
    for tag, values in [("mineable/pickaxe", pickaxe),
                        ("needs_stone_tool", stone_tool), ("needs_iron_tool", iron_tool),
                        ("needs_diamond_tool", diamond_tool),
                        ("logs", logs), ("logs_that_burn", logs),
                        ("leaves", [f"{NS}:{n}" for n in leaves_names()]),
                        ("saplings", [f"{NS}:{n}" for n in sapling_names()]),
                        ("crops", [f"{NS}:{n}" for n in crop_block_names()])]:
        dump(f"{data}/minecraft/tags/block/{tag}.json", {"replace": False, "values": sorted(values)})
    # 基础工具/盔甲标签覆盖：进原版 #pickaxes…/#enchantable/*，附魔修复自动生效
    tool_tag_values = {
        "pickaxes": [f"{NS}:{n}" for n in tool_names() if n.endswith("_pickaxe")],
        "swords": [f"{NS}:{n}" for n in tool_names() if n.endswith("_sword")],
        "axes": [f"{NS}:{n}" for n in tool_names() if n.endswith("_axe")],
        "shovels": [f"{NS}:{n}" for n in tool_names() if n.endswith("_shovel")],
        "hoes": [f"{NS}:{n}" for n in tool_names() if n.endswith("_hoe")],
    }
    for tag, values in tool_tag_values.items():
        dump(f"{data}/minecraft/tags/item/{tag}.json", {"replace": False, "values": sorted(values)})
    armor = [f"{NS}:{n}" for n in armor_names()]
    for tag in ("enchantable/armor", "enchantable/durability"):
        dump(f"{data}/minecraft/tags/item/{tag}.json", {"replace": False, "values": sorted(armor)})
    # 工具修复材料标签：同重数的混用组；护甲=同重数压缩石头
    for mat, _, _ in TOOL_LINES:
        for p in LV:
            if mat == "cobblestone":
                values = [f"{NS}:{p}_cobblestone", f"{NS}:{p}_cobbled_deepslate"]
            else:
                values = sorted(f"{NS}:{p}_{w[0]}_log" for w in WOODS)
            dump(f"{data}/{NS}/tags/item/repair_{mat}_tool_{p}.json",
                 {"replace": False, "values": values})
    for p in LV:
        dump(f"{data}/{NS}/tags/item/repair_stone_armor_{p}.json",
             {"replace": False, "values": [f"{NS}:{p}_stone"]})
        dump(f"{data}/{NS}/tags/item/repair_wood_armor_{p}.json",
             {"replace": False, "values": sorted(f"{NS}:{p}_{w[0]}_log" for w in WOODS)})
    # 工具材料混用标签（对应原版 #stone_tool_materials 的做法）；名字必须与配方引用的 {mat}_tool_{p} 一致
    for p in LV:
        dump(f"{data}/{NS}/tags/item/cobblestone_tool_{p}.json",
             {"replace": False, "values": [f"{NS}:{p}_cobblestone", f"{NS}:{p}_cobbled_deepslate"]})
        dump(f"{data}/{NS}/tags/item/wood_tool_{p}.json",
             {"replace": False, "values": sorted(f"{NS}:{p}_{w[0]}_log" for w in WOODS)})
        # 盔甲合成材料：压缩石头系（石头+深板岩）或压缩原石系（圆石+深板岩圆石）
        dump(f"{data}/{NS}/tags/item/stone_armor_{p}.json",
             {"replace": False, "values": [f"{NS}:{p}_stone", f"{NS}:{p}_deepslate",
                                           f"{NS}:{p}_cobblestone", f"{NS}:{p}_cobbled_deepslate"]})
    # ---- 世界树特征：9 树种 × 9 级，形状与原版一致
    n = gen_tree_features(data)
    assert n == 126, n


PICKAXE_MATS = {m[0] for m in MATERIALS if m[0] in
                ("cobblestone", "stone", "cobbled_deepslate", "deepslate", "netherrack", "end_stone", "obsidian")} | \
               {m[0] for m in BUILD_MATERIALS if m[3] == "pickaxe"}
SHOVEL_MATS = {"dirt", "sand", "gravel", "clay", "snow", "mud"}


def _mat_of(block_name):
    rest = block_name.split("_", 1)[1]
    if rest.endswith("_leaves"):
        return "leaves"
    for m in storage_materials():
        if rest == m[0]:
            return m[0]
    return rest


def self_drop(name):
    return {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{name}",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                   "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}],
    }


def gen_leaves_loot(data, name):
    """原版树叶战利品表逐字节镜像：本叶（剪刀/精准）+ 树苗 5%+时运 + 木棍 2%+时运（橡木另加苹果）。"""
    p = name.split("_", 1)[0]
    wood = name.split("_", 1)[1][: -len("_leaves")]
    src = os.path.join(VANILLA_DATA, "loot_table", "blocks", f"{wood}_leaves.json")
    text = Path(src).read_text(encoding="utf-8")
    vanilla_sapling = f"minecraft:{wood}_sapling" if wood != "mangrove" else "minecraft:mangrove_propagule"
    text = text.replace(f'"minecraft:{wood}_leaves"', f'"{NS}:{name}"')
    text = text.replace(f'"{vanilla_sapling}"', f'"{NS}:{name.replace("_leaves", "_sapling")}"')
    text = text.replace(f'"minecraft:blocks/{wood}_leaves"', f'"{NS}:blocks/{name}"')
    dump_text(f"{data}/{NS}/loot_table/blocks/{name}.json", text)


# 树特征模板：特征 id → 原版模板名（14 个树种特征 × 9 级）
TREE_FEATURES = {
    "oak": [("oak", "oak"), ("fancy_oak", "fancy_oak")],
    "birch": [("birch", "birch")],
    "spruce": [("spruce", "spruce"), ("mega_spruce", "mega_spruce"), ("mega_pine", "mega_pine")],
    "jungle": [("jungle", "jungle_tree_no_vine"), ("mega_jungle", "mega_jungle_tree")],
    "acacia": [("acacia", "acacia")],
    "dark_oak": [("dark_oak", "dark_oak")],
    "cherry": [("cherry", "cherry")],
    "mangrove": [("mangrove", "mangrove"), ("tall_mangrove", "tall_mangrove")],
    "pale_oak": [("pale_oak", "pale_oak_bonemeal")],
}


def gen_tree_features(data):
    """从原版 configured_feature 模板批量生成压缩树特征：只替换原木/树叶 id，形状 1:1，
    藤蔓/红树气根/灰化土等附属方块保持原版。树苗生长（TreeGrower）按 id 引用。"""
    total = 0
    for wood, _, _ in WOODS:
        for feature, template in TREE_FEATURES[wood]:
            src = os.path.join(VANILLA_DATA, "worldgen", "configured_feature", f"{template}.json")
            text = Path(src).read_text(encoding="utf-8")
            for p in LV:
                out = text.replace(f'"minecraft:{wood}_log"', f'"{NS}:{p}_{wood}_log"')
                out = out.replace(f'"minecraft:{wood}_leaves"', f'"{NS}:{p}_{wood}_leaves"')
                # 长树会把脚下换成 dirt/podzol（dirt_provider）：必须一并换成同重数压缩泥土
                out = out.replace('"minecraft:dirt"', f'"{NS}:{p}_dirt"')
                out = out.replace('"minecraft:podzol"', f'"{NS}:{p}_dirt"')
                dump_text(f"{data}/{NS}/worldgen/configured_feature/{feature}_{p}.json", out)
                total += 1
    return total


def gen_crop_loot(data, crop, p, has_seed):
    """原版作物战利品表镜像：成熟产物 + 种子（含时运）。胡萝卜/土豆产物即种子。"""
    block_key = {"wheat": "wheat", "carrot": "carrots", "potato": "potatoes", "beetroot": "beetroots"}[crop]
    src = os.path.join(VANILLA_DATA, "loot_table", "blocks", f"{block_key}.json")
    text = Path(src).read_text(encoding="utf-8")
    our_block = f"{NS}:{p}_{crop}_plant"
    produce = f"{NS}:{crop_item_id(crop, p)}" if crop in ("carrot", "potato") else f"{NS}:{p}_{crop}"
    text = text.replace(f'"block": "minecraft:{block_key}"', f'"block": "{our_block}"')
    # 先换种子再换产物（beetroot 是 beetroot 的前缀）
    if crop == "wheat":
        text = text.replace('"name": "minecraft:wheat_seeds"', f'"name": "{NS}:{p}_wheat_seeds"')
    if crop == "beetroot":
        text = text.replace('"name": "minecraft:beetroot_seeds"', f'"name": "{NS}:{p}_beetroot_seeds"')
    text = text.replace(f'"name": "minecraft:{crop}"', f'"name": "{produce}"')
    text = text.replace(f'"minecraft:blocks/{block_key}"', f'"{NS}:blocks/{p}_{crop}_plant"')
    dump_text(f"{data}/{NS}/loot_table/blocks/{p}_{crop}_plant.json", text)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default="1.21.11", choices=["1.21.11"],
                    help="1.21.1 冻结在 0.2.0，不再生成")
    args = ap.parse_args()
    subs = [os.path.join(ROOT, args.target, loader) for loader in ("fabric", "neoforge")]

    blocks = (storage_names() + leaves_names() + sapling_names() + farmland_names()
              + crop_block_names() + cane_names() + pot_names())
    items = (storage_names() + leaves_names() + sapling_names() + crop_block_names() + cane_names()
             + pot_names() + tool_names() + armor_names() + food_names() + crop_produce_names()
             + stick_names() + extra_seed_names())
    assert len(storage_names()) == 459, len(storage_names())
    assert len(tool_names()) == 90, len(tool_names())
    assert len(blocks) == 1112, len(blocks)
    assert len(items) == 1304, len(items)
    print(f"blocks={len(blocks)} items={len(items)} (tools={len(tool_names())} armor={len(armor_names())} "
          f"saplings={len(sapling_names())} leaves={len(leaves_names())} food={len(food_names())} "
          f"canes={len(cane_names())})")
    for sub in subs:
        if not os.path.exists(os.path.join(sub, "build.gradle")):
            print(f"skip {sub} (no build.gradle)")
            continue
        res = f"{sub}/src/main/resources/assets/{NS}"
        data = f"{sub}/src/main/resources/data"
        # 生成目录整体归本脚本所有：先清空再生成，避免 0.2.0 遗留文件。
        # textures/ 例外——归 gen_assets.py 所有，只清本脚本拥有的条目。
        if os.path.isdir(res):
            for entry in os.listdir(res):
                if entry == "textures":
                    continue
                full = os.path.join(res, entry)
                shutil.rmtree(full, ignore_errors=True) if os.path.isdir(full) else os.remove(full)
        shutil.rmtree(data, ignore_errors=True)
        gen_assets(res)
        gen_lang(res)
        gen_data(data)
        if not os.path.isdir(f"{res}/textures"):
            raise SystemExit(f"ERROR: {res}/textures 不存在——先跑 gen_assets.py 再跑本脚本")
        print(f"generated resources into {sub}")


if __name__ == "__main__":
    main()
