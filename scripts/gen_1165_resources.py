#!/usr/bin/env python3
"""压缩方块 mod 1.16.5 资源生成器（改造自 gen_resources.py）。

1.16.5 格式差异（相对 1.21.11）：
- 无 items/*.json 物品模型定义；BlockItem 模型 = models/item/<id>.json（parent 指向方块模型）
- 目录复数：data/<ns>/recipes、loot_tables、advancements；data/minecraft/tags/{items,blocks}
- 配方：result={"item":id,"count":n}；key 配料={"item":...}/{"tag":...}；无 category 字段
- 熔炼：result 为字符串 id
- 战利品表：无 random_sequence；match_tool 用 {"predicate":{"item":...}}
- 配方解锁 advancement 只挂 has_the_recipe 单 criterion（1.16.5 ItemPredicate 兼容）
- 树叶/树苗/甘蔗/作物模型用 Forge 的 "render_type" 字段声明透明层（免客户端代码）
- 树叶 loot 走简化版（剪刀/丝触掉自身 + 固定概率树苗/木棍），不做 1.16 时运曲线逐字节镜像
- 世界树特征不产 JSON（1.16.5 结构不同），由 Java 侧注册 configured feature
- 兼容金属锭标签走 #forge:ingots/<key>（1.16.5 Forge 标签约定），生成空标签供其它 mod 合并

命名规则与 1.21.11 一致（<prefix>_<mat> 等），材料集裁剪见 gen_1165_assets.py。
用法：python scripts/gen_1165_resources.py
"""
import json
import os
import shutil
from pathlib import Path

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "compressedblocks"
SUB = os.path.join(ROOT, "1.16.5", "forge")

LV = ["1x", "2x", "3x", "4x", "5x", "6x", "7x", "8x", "9x"]
LV_EN = ["Compressed", "Double Compressed", "Triple Compressed", "Quadruple Compressed",
         "Quintuple Compressed", "Sextuple Compressed", "Septuple Compressed",
         "Octuple Compressed", "Nonuple Compressed"]
LV_ZH = ["压缩", "二重压缩", "三重压缩", "四重压缩", "五重压缩",
         "六重压缩", "七重压缩", "八重压缩", "九重压缩"]

# 储存材料（key, en, zh）——与 gen_1165_assets.MATERIALS 一致
MATERIALS = [
    ("cobblestone", "Cobblestone", "圆石"),
    ("stone", "Stone", "石头"),
    ("oak_log", "Oak Log", "橡木原木"),
    ("spruce_log", "Spruce Log", "云杉原木"),
    ("birch_log", "Birch Log", "白桦原木"),
    ("jungle_log", "Jungle Log", "丛林原木"),
    ("acacia_log", "Acacia Log", "金合欢原木"),
    ("dark_oak_log", "Dark Oak Log", "深色橡木原木"),
    ("dirt", "Dirt", "泥土"),
    ("sand", "Sand", "沙子"),
    ("gravel", "Gravel", "沙砾"),
    ("netherrack", "Netherrack", "下界岩"),
    ("end_stone", "End Stone", "末地石"),
    ("obsidian", "Obsidian", "黑曜石"),
    ("coal_block", "Block of Coal", "煤炭块"),
    ("iron_block", "Block of Iron", "铁块"),
    ("lapis_block", "Lapis Lazuli Block", "青金石块"),
    ("gold_block", "Block of Gold", "金块"),
    ("redstone_block", "Block of Redstone", "红石块"),
    ("emerald_block", "Block of Emerald", "绿宝石块"),
    ("diamond_block", "Block of Diamond", "钻石块"),
    ("granite", "Granite", "花岗岩"),
    ("diorite", "Diorite", "闪长岩"),
    ("andesite", "Andesite", "安山岩"),
    ("sandstone", "Sandstone", "砂岩"),
    ("red_sandstone", "Red Sandstone", "红砂岩"),
    ("basalt", "Basalt", "玄武岩"),
    ("blackstone", "Blackstone", "黑石"),
    ("terracotta", "Terracotta", "陶瓦"),
    ("quartz_block", "Quartz Block", "石英块"),
    ("purpur_block", "Purpur Block", "紫珀块"),
    ("prismarine", "Prismarine", "海晶石"),
    ("glowstone", "Glowstone", "荧石"),
    ("clay", "Clay", "黏土块"),
    ("hay_block", "Hay Bale", "干草块"),
    ("bone_block", "Bone Block", "骨块"),
    ("snow", "Snow Block", "雪块"),
    ("blue_ice", "Blue Ice", "蓝冰"),
    ("gunpowder", "Block of Gunpowder", "火药块"),
]
MATERIAL_KEYS = {m[0] for m in MATERIALS}

WOODS = [
    ("oak", "Oak", "橡树"),
    ("spruce", "Spruce", "云杉"),
    ("birch", "Birch", "白桦"),
    ("jungle", "Jungle", "丛林"),
    ("acacia", "Acacia", "金合欢"),
    ("dark_oak", "Dark Oak", "深色橡树"),
]

CROPS = [
    ("wheat", "Wheat", "小麦", 8, True),
    ("carrot", "Carrot", "胡萝卜", 4, False),
    ("potato", "Potato", "马铃薯", 4, False),
    ("beetroot", "Beetroot", "甜菜根", 4, True),
]
CROP_MAX_LEVEL = 3

FOODS = [
    ("bread", "Bread", "面包"), ("beef", "Raw Beef", "生牛肉"),
    ("cooked_beef", "Steak", "牛排"), ("porkchop", "Raw Porkchop", "生猪排"),
    ("cooked_porkchop", "Cooked Porkchop", "熟猪排"), ("mutton", "Raw Mutton", "羊肉"),
    ("cooked_mutton", "Cooked Mutton", "熟羊肉"), ("chicken", "Raw Chicken", "生鸡肉"),
    ("cooked_chicken", "Cooked Chicken", "熟鸡肉"), ("rabbit", "Raw Rabbit", "生兔肉"),
    ("cooked_rabbit", "Cooked Rabbit", "熟兔肉"), ("cod", "Raw Cod", "生鳕鱼"),
    ("cooked_cod", "Cooked Cod", "熟鳕鱼"), ("salmon", "Raw Salmon", "生鲑鱼"),
    ("cooked_salmon", "Cooked Salmon", "熟鲑鱼"), ("melon", "Watermelon", "西瓜"),
    ("rotten_flesh", "Rotten Flesh", "腐肉"), ("baked_potato", "Baked Potato", "烤土豆"),
]
COOKED_FROM = [("potato", "baked_potato"), ("beef", "cooked_beef"),
               ("porkchop", "cooked_porkchop"), ("mutton", "cooked_mutton"),
               ("chicken", "cooked_chicken"), ("rabbit", "cooked_rabbit"),
               ("cod", "cooked_cod"), ("salmon", "cooked_salmon")]
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
TOOL_LINES = [("cobblestone", "Cobblestone", "圆石"), ("wood", "Wooden", "木")]
AGE_TO_STAGE = {0: 0, 1: 0, 2: 1, 3: 1, 4: 2, 5: 2, 6: 3, 7: 3}
EXTRA_SEEDS = ["pumpkin_seeds", "melon_seeds"]

# 0.3.2 压缩金属包（key, en, zh, 挖掘档, 发光）
COMPAT_METALS = [
    ("tin", "Tin", "锡", "stone", 0), ("lead", "Lead", "铅", "stone", 0),
    ("zinc", "Zinc", "锌", "stone", 0), ("plastic", "Plastic", "塑料", "stone", 0),
    ("silver", "Silver", "银", "iron", 0), ("nickel", "Nickel", "镍", "iron", 0),
    ("bronze", "Bronze", "青铜", "iron", 0), ("brass", "Brass", "黄铜", "iron", 0),
    ("electrum", "Electrum", "琥珀金", "iron", 0), ("invar", "Invar", "殷钢", "iron", 0),
    ("constantan", "Constantan", "康铜", "iron", 0), ("steel", "Steel", "钢", "iron", 0),
    ("manasteel", "Manasteel", "源质钢", "iron", 0), ("uranium", "Uranium", "铀", "diamond", 0),
    ("osmium", "Osmium", "锇", "diamond", 0), ("signalum", "Signalum", "信素", "diamond", 0),
    ("enderium", "Enderium", "末影锭", "diamond", 0),
    ("refined_obsidian", "Refined Obsidian", "精炼黑曜石", "diamond", 0),
    ("refined_glowstone", "Refined Glowstone", "精炼萤石", "diamond", 15),
    ("lumium", "Lumium", "流明", "diamond", 12), ("terrasteel", "Terrasteel", "泰拉钢", "diamond", 0),
    ("elementium", "Elementium", "元素钢", "diamond", 0),
]


def dump(path, obj, indent=None):
    text = json.dumps(obj, ensure_ascii=False, separators=(",", ":"), indent=indent) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def dump_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def storage_names():
    return [f"{p}_{m[0]}" for m in MATERIALS for p in LV]


def leaves_names():
    return [f"{p}_{w[0]}_leaves" for w in WOODS for p in LV]


def sapling_names():
    return [f"{p}_{w[0]}_sapling" for w in WOODS for p in LV]


def farmland_names():
    return [f"{p}_farmland" for p in LV]


def crop_block_names():
    return [f"{p}_{c[0]}_plant" for c in CROPS for p in LV[:CROP_MAX_LEVEL]]


def crop_item_id(crop_key, p):
    if crop_key in ("wheat", "beetroot"):
        return f"{p}_{crop_key}_seeds"
    return f"{p}_{crop_key}"


def tool_names():
    return [f"{p}_{m[0]}_{t}" for m in TOOL_LINES for p in LV for t in TOOLS]


def armor_names():
    return [f"{p}_{line}_{piece}" for line in ("stone", "wood") for p in LV for piece in ARMOR_PIECES]


def food_names():
    return [f"{p}_{f[0]}" for f in FOODS for p in LV[:FOOD_MAX_LEVEL]]


def crop_produce_names():
    return [f"{p}_wheat" for p in LV[:CROP_MAX_LEVEL]] + [f"{p}_beetroot" for p in LV[:CROP_MAX_LEVEL]]


def stick_names():
    return [f"{p}_stick" for p in LV]


def extra_seed_names():
    return [f"{p}_{k}" for k in EXTRA_SEEDS for p in LV[:CROP_MAX_LEVEL]]


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
    return mat[2] if mat[0] == "bone_block" else (mat[2][:-1] if mat[2].endswith("块") else mat[2])


def cane_names():
    out = [f"{p}_cane" for p in LV]
    for mat in MATERIALS:
        key = cane_key(mat[0])
        out += [f"{p}_{key}_cane" for p in LV]
    return out


def pot_names():
    return ["pot", "hopper_pot"]


GENERATOR_TIERS = ["cobblestone_generator", "2x_cobblestone_generator", "3x_cobblestone_generator"]


def compat_names():
    return [f"{p}_{m[0]}_block" for m in COMPAT_METALS for p in LV]


def block_names():
    return (storage_names() + leaves_names() + sapling_names() + farmland_names()
            + crop_block_names() + cane_names() + pot_names() + GENERATOR_TIERS
            + compat_names() + ["compressed_chest", "compressed_shulker_box"])


def item_names():
    return (storage_names() + leaves_names() + sapling_names()
            + [crop_item_id(c, p) for c in CROPS for p in LV[:CROP_MAX_LEVEL]]
            + cane_names() + pot_names() + GENERATOR_TIERS + tool_names() + armor_names()
            + food_names() + crop_produce_names() + stick_names() + extra_seed_names()
            + compat_names() + ["compressed_chest", "compressed_shulker_box"])


# ---------------------------------------------------------------- assets

def gen_assets(res):
    # 储存方块/树叶/金属：cube_all + blockstate + BlockItem 模型
    leaf_set = set(leaves_names())
    for name in storage_names() + leaves_names() + compat_names():
        dump(f"{res}/blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}}
        if name in leaf_set:
            # 1.16.5：Forge 扩展 render_type 字段声明透明渲染层（免客户端代码）
            model["render_type"] = "minecraft:cutout_mipped"
        dump(f"{res}/models/block/{name}.json", model)
        dump(f"{res}/models/item/{name}.json", {"parent": f"{NS}:block/{name}"})
    # 树苗：cross；物品独立 generated 模型
    for name in sapling_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"stage=0": {"model": f"{NS}:block/{name}"},
                           "stage=1": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
              "textures": {"cross": f"{NS}:block/{name}"}})
        dump(f"{res}/models/item/{name}.json",
             {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/{name}"}})
    # 压缩甘蔗：cross
    for name in cane_names():
        dump(f"{res}/blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
              "textures": {"cross": f"{NS}:block/{name}"}})
        dump(f"{res}/models/item/{name}.json",
             {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/{name}"}})
    # 压缩盆栽：分层盆模型（与 1.21.11 相同的 elements）
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

    def pot_element(frm, to, cull_down=False):
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

    pot_elements = [
        pot_element([2, 0, 2], [14, 1, 14], cull_down=True),
        pot_element([3, 1, 3], [13, 4, 13]),
        pot_element([2, 4, 2], [14, 6, 3]),
        pot_element([2, 4, 13], [14, 6, 14]),
        pot_element([2, 4, 3], [3, 6, 13]),
        pot_element([13, 4, 3], [14, 6, 13]),
    ]
    for name in pot_names():
        hopper = name == "hopper_pot"
        side_tex = f"{NS}:block/pot_hopper_side" if hopper else f"{NS}:block/pot_side"
        bottom_tex = f"{NS}:block/pot_hopper_bottom" if hopper else f"{NS}:block/pot_bottom"
        model = {"gui_light": "side", "display": pot_display,
                 "textures": {"particle": side_tex, "side": side_tex,
                              "top": f"{NS}:block/pot_top", "bottom": bottom_tex},
                 "elements": pot_elements}
        dump(f"{res}/blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json", model)
        dump(f"{res}/models/item/{name}.json", {"parent": f"{NS}:block/{name}"})
    # 刷石机：cube 三面
    for name in GENERATOR_TIERS:
        dump(f"{res}/blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cube",
              "textures": {"up": f"{NS}:block/{name}_top", "down": f"{NS}:block/{name}_bottom",
                           "north": f"{NS}:block/{name}", "south": f"{NS}:block/{name}",
                           "west": f"{NS}:block/{name}", "east": f"{NS}:block/{name}",
                           "particle": f"{NS}:block/{name}"}})
        dump(f"{res}/models/item/{name}.json", {"parent": f"{NS}:block/{name}"})
    # 压缩箱子：facing blockstate；方块模型只留 particle；物品用正面贴图平面图标
    dump(f"{res}/models/block/compressed_chest.json",
         {"render_type": "minecraft:cutout",
          "textures": {"particle": f"{NS}:block/compressed_chest_side"}})
    dump(f"{res}/blockstates/compressed_chest.json",
         {"variants": {"facing=north": {"model": f"{NS}:block/compressed_chest"},
                       "facing=east": {"model": f"{NS}:block/compressed_chest", "y": 90},
                       "facing=south": {"model": f"{NS}:block/compressed_chest", "y": 180},
                       "facing=west": {"model": f"{NS}:block/compressed_chest", "y": 270}}})
    dump(f"{res}/models/item/compressed_chest.json",
         {"parent": "minecraft:item/generated",
          "textures": {"layer0": f"{NS}:block/compressed_chest_front"}})
    # 压缩潜影盒：同上（物品用侧面贴图平面图标）
    dump(f"{res}/models/block/compressed_shulker_box.json",
         {"render_type": "minecraft:cutout",
          "textures": {"particle": f"{NS}:block/compressed_shulker_box_side"}})
    dump(f"{res}/blockstates/compressed_shulker_box.json",
         {"variants": {"": {"model": f"{NS}:block/compressed_shulker_box"}}})
    dump(f"{res}/models/item/compressed_shulker_box.json",
         {"parent": "minecraft:item/generated",
          "textures": {"layer0": f"{NS}:block/compressed_shulker_box_side"}})
    # 耕地：15/16 模板，无物品
    for name in farmland_names():
        p = name.split("_", 1)[0]
        dump(f"{res}/blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/template_farmland",
              "textures": {"dirt": f"{NS}:block/{p}_dirt", "top": f"{NS}:block/{name}"}})
    # 作物：age 0..7 → 阶段 cross
    for crop, _, _, stages, _ in CROPS:
        for p in LV[:CROP_MAX_LEVEL]:
            variants = {}
            for age in range(8):
                stage = age if stages == 8 else AGE_TO_STAGE[age]
                variants[f"age={age}"] = {"model": f"{NS}:block/{p}_{crop}_stage{stage}"}
            dump(f"{res}/blockstates/{p}_{crop}_plant.json", {"variants": variants})
            for n in range(stages):
                dump(f"{res}/models/block/{p}_{crop}_stage{n}.json",
                     {"parent": "minecraft:block/crop", "render_type": "minecraft:cutout",
                      "textures": {"crop": f"{NS}:block/{p}_{crop}_stage{n}"}})
            item = crop_item_id(crop, p)
            dump(f"{res}/models/item/{item}.json",
                 {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})
    # 纯物品模型（工具 handheld，其余 generated）
    simple = (tool_names() + armor_names() + food_names() + crop_produce_names()
              + stick_names() + extra_seed_names())
    tool_set = set(tool_names())
    for name in simple:
        parent = "minecraft:item/handheld" if name in tool_set else "minecraft:item/generated"
        dump(f"{res}/models/item/{name}.json",
             {"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}})


# ---------------------------------------------------------------- lang

def gen_lang(res):
    en, zh = {}, {}
    EN_TOOLS = {"pickaxe": "Pickaxe", "axe": "Axe", "shovel": "Shovel", "hoe": "Hoe", "sword": "Sword"}
    tool_set = set(tool_names())
    tool_mats = {m[0]: m for m in TOOL_LINES}
    for name in tool_names():
        lv = next(l for l in LV if name.startswith(l + "_"))
        i = LV.index(lv)
        rest = name[len(lv) + 1:]
        mat_key, tool_id = rest.rsplit("_", 1)
        mat = tool_mats[mat_key]
        en[f"item.{NS}.{name}"] = f"{LV_EN[i]} {mat[1]} {EN_TOOLS[tool_id]}"
        zh[f"item.{NS}.{name}"] = f"{LV_ZH[i]}{mat[2]}{TOOLS[tool_id][1]}"
    for name in storage_names():
        lv = next(l for l in LV if name.startswith(l + "_"))
        i = LV.index(lv)
        mat = next(m for m in MATERIALS if m[0] == name[len(lv) + 1:])
        en[f"block.{NS}.{name}"] = f"{LV_EN[i]} {mat[1]}"
        zh[f"block.{NS}.{name}"] = f"{LV_ZH[i]}{mat[2]}"
    for name in leaves_names():
        lv = next(l for l in LV if name.startswith(l + "_"))
        i = LV.index(lv)
        wood = next(w for w in WOODS if name[len(lv) + 1:].startswith(w[0] + "_"))
        en[f"block.{NS}.{name}"] = f"{LV_EN[i]} {wood[1]} Leaves"
        zh[f"block.{NS}.{name}"] = f"{LV_ZH[i]}{wood[2]}树叶"
    for name in sapling_names():
        lv = next(l for l in LV if name.startswith(l + "_"))
        i = LV.index(lv)
        wood = next(w for w in WOODS if name[len(lv) + 1:].startswith(w[0] + "_"))
        en[f"block.{NS}.{name}"] = f"{LV_EN[i]} {wood[1]} Sapling"
        zh[f"block.{NS}.{name}"] = f"{LV_ZH[i]}{wood[2]}树苗"
    for name in farmland_names():
        lv = next(l for l in LV if name.startswith(l + "_"))
        en[f"block.{NS}.{name}"] = f"{LV_EN[LV.index(lv)]} Farmland"
        zh[f"block.{NS}.{name}"] = f"{LV_ZH[LV.index(lv)]}耕地"
    cane_mat_of_key = {cane_key(m[0]): m for m in MATERIALS}
    for name in cane_names():
        lv = next(l for l in LV if name.startswith(l + "_"))
        i = LV.index(lv)
        rest = name[len(lv) + 1:]
        if rest == "cane":
            en[f"block.{NS}.{name}"] = f"{LV_EN[i]} Sugar Cane"
            zh[f"block.{NS}.{name}"] = f"{LV_ZH[i]}甘蔗"
        else:
            mat = cane_mat_of_key[rest[:-5]]
            en[f"block.{NS}.{name}"] = f"{LV_EN[i]} {cane_en(mat[1])} Cane"
            zh[f"block.{NS}.{name}"] = f"{LV_ZH[i]}{cane_zh(mat)}甘蔗"
    en["block." + NS + ".pot"] = "Pot"
    zh["block." + NS + ".pot"] = "盆栽"
    en["block." + NS + ".hopper_pot"] = "Hopper Pot"
    zh["block." + NS + ".hopper_pot"] = "漏斗盆栽"
    generator_lang = {"cobblestone_generator": ("Cobblestone Generator", "刷石机"),
                      "2x_cobblestone_generator": ("Double Compressed Cobblestone Generator", "二重压缩刷石机"),
                      "3x_cobblestone_generator": ("Triple Compressed Cobblestone Generator", "三重压缩刷石机")}
    for name in GENERATOR_TIERS:
        en["block." + NS + "." + name] = generator_lang[name][0]
        zh["block." + NS + "." + name] = generator_lang[name][1]
    en["block." + NS + ".compressed_chest"] = "Compressed Chest"
    zh["block." + NS + ".compressed_chest"] = "压缩箱子"
    en["block." + NS + ".compressed_shulker_box"] = "Compressed Shulker Box"
    zh["block." + NS + ".compressed_shulker_box"] = "压缩潜影盒"
    for key, mat_en, mat_zh, _, _ in COMPAT_METALS:
        for i, lv in enumerate(LV):
            en[f"block.{NS}.{lv}_{key}_block"] = f"{LV_EN[i]} Compressed {mat_en} Block"
            zh[f"block.{NS}.{lv}_{key}_block"] = f"{LV_ZH[i]}压缩{mat_zh}块"
    crop_en = {c[0]: c[1] for c in CROPS}
    crop_zh = {c[0]: c[2] for c in CROPS}
    for name in crop_block_names():
        p, crop, _ = name.split("_", 2)
        i = LV.index(p)
        en[f"block.{NS}.{name}"] = f"{LV_EN[i]} {crop_en[crop]} Plant"
        zh[f"block.{NS}.{name}"] = f"{LV_ZH[i]}{crop_zh[crop]}植株"
    for crop, en_name, zh_name, _, has_seed in CROPS:
        for p in LV[:CROP_MAX_LEVEL]:
            i = LV.index(p)
            item = crop_item_id(crop, p)
            if has_seed:
                en[f"item.{NS}.{item}"] = f"{LV_EN[i]} Compressed {en_name} Seeds"
                zh[f"item.{NS}.{item}"] = f"{LV_ZH[i]}{zh_name}种子"
            else:
                en[f"item.{NS}.{item}"] = f"{LV_EN[i]} Compressed {en_name}"
                zh[f"item.{NS}.{item}"] = f"{LV_ZH[i]}{zh_name}"
    for p in LV[:CROP_MAX_LEVEL]:
        i = LV.index(p)
        en[f"item.{NS}.{p}_wheat"] = f"{LV_EN[i]} Wheat"
        zh[f"item.{NS}.{p}_wheat"] = f"{LV_ZH[i]}小麦"
        en[f"item.{NS}.{p}_beetroot"] = f"{LV_EN[i]} Beetroot"
        zh[f"item.{NS}.{p}_beetroot"] = f"{LV_ZH[i]}甜菜根"
    food_en = {f[0]: f[1] for f in FOODS}
    food_zh = {f[0]: f[2] for f in FOODS}
    for name in food_names():
        p, food = name.split("_", 1)
        i = LV.index(p)
        en[f"item.{NS}.{name}"] = f"{LV_EN[i]} {food_en[food]}"
        zh[f"item.{NS}.{name}"] = f"{LV_ZH[i]}{food_zh[food]}"
    for name in armor_names():
        p, line, piece = name.split("_", 2)
        i = LV.index(p)
        line_en = "Stone" if line == "stone" else "Wooden"
        line_zh = "石头" if line == "stone" else "木质"
        en[f"item.{NS}.{name}"] = f"{LV_EN[i]} {line_en} {ARMOR_EN[piece]}"
        zh[f"item.{NS}.{name}"] = f"{LV_ZH[i]}{line_zh}{ARMOR_ZH[piece]}"
    for name in stick_names():
        lv = next(l for l in LV if name.startswith(l + "_"))
        i = LV.index(lv)
        en[f"item.{NS}.{name}"] = f"{LV_EN[i]} Stick"
        zh[f"item.{NS}.{name}"] = f"{LV_ZH[i]}木棍"
    extra_seed_zh = {"pumpkin_seeds": "南瓜种子", "melon_seeds": "西瓜种子"}
    extra_seed_en = {"pumpkin_seeds": "Pumpkin Seeds", "melon_seeds": "Melon Seeds"}
    for name in extra_seed_names():
        lv = next(l for l in LV if name.startswith(l + "_"))
        i = LV.index(lv)
        key = name[len(lv) + 1:]
        en[f"item.{NS}.{name}"] = f"{LV_EN[i]} {extra_seed_en[key]}"
        zh[f"item.{NS}.{name}"] = f"{LV_ZH[i]}{extra_seed_zh[key]}"
    en["itemGroup." + NS + ".blocks"] = "Compressed Blocks"
    zh["itemGroup." + NS + ".blocks"] = "压缩方块"
    en["itemGroup." + NS + ".tools"] = "Compressed Tools"
    zh["itemGroup." + NS + ".tools"] = "压缩工具"
    en["itemGroup." + NS + ".food"] = "Compressed Food"
    zh["itemGroup." + NS + ".food"] = "压缩食物"
    en["container." + NS + ".compressed_chest"] = "Compressed Chest"
    zh["container." + NS + ".compressed_chest"] = "压缩箱子"
    en["container." + NS + ".compressed_shulker_box"] = "Compressed Shulker Box"
    zh["container." + NS + ".compressed_shulker_box"] = "压缩潜影盒"
    dump(f"{res}/lang/en_us.json", dict(sorted(en.items())), indent=2)
    dump(f"{res}/lang/zh_cn.json", dict(sorted(zh.items())), indent=2)


# ---------------------------------------------------------------- data（1.16.5：recipes/loot_tables/advancements 复数）

def gen_data(data):
    def unlock(path):
        dump(f"{data}/{NS}/advancements/recipes/{path}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                                            "conditions": {"recipe": f"{NS}:{path}"}}},
            "requirements": [["has_the_recipe"]],
            "rewards": {"recipes": [f"{NS}:{path}"]},
        })

    def shaped(path, pattern, key, result, count=1):
        dump(f"{data}/{NS}/recipes/{path}.json", {
            "type": "minecraft:crafting_shaped", "pattern": pattern, "key": key,
            "result": {"item": result, "count": count},
        })
        unlock(path)

    def shapeless(path, ingredients, result, count=1):
        dump(f"{data}/{NS}/recipes/{path}.json", {
            "type": "minecraft:crafting_shapeless", "ingredients": ingredients,
            "result": {"item": result, "count": count},
        })
        unlock(path)

    def compress(cur, prev, count=9):
        shaped(cur.split(":", 1)[1], ["PPP", "PPP", "PPP"], {"P": prev}, cur)

    def unpack(cur, prev, count=9):
        shapeless("unpack_" + cur.split(":", 1)[1], [cur], prev, count)

    # ---- 储存方块与木棍：9↔1
    for mat, _, _ in MATERIALS + [("stick", "", "")]:
        for i, p in enumerate(LV):
            cur = f"{NS}:{p}_{mat}"
            prev = f"minecraft:{mat}" if i == 0 else f"{NS}:{LV[i - 1]}_{mat}"
            compress(cur, prev)
            unpack(cur, prev)
    # ---- 树苗/树叶
    for wood, _, _ in WOODS:
        for kind in ("sapling", "leaves"):
            for i, p in enumerate(LV):
                cur = f"{NS}:{p}_{wood}_{kind}"
                prev = f"minecraft:{wood}_{kind}" if i == 0 else f"{NS}:{LV[i - 1]}_{wood}_{kind}"
                compress(cur, prev)
                unpack(cur, prev)
    # ---- 作物 3 级
    for i, p in enumerate(LV[:CROP_MAX_LEVEL]):
        cur = f"{NS}:{p}_wheat"
        if i == 0:
            unpack(cur, "minecraft:wheat")
        elif i == 1:
            compress(cur, "minecraft:hay_block")
            unpack(cur, f"{NS}:{LV[i - 1]}_wheat")
        else:
            compress(cur, f"{NS}:{LV[i - 1]}_wheat")
            unpack(cur, f"{NS}:{LV[i - 1]}_wheat")
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        cur = f"{NS}:{p}_melon"
        if i == 0:
            unpack(cur, "minecraft:melon_slice")
        elif i == 1:
            compress(cur, "minecraft:melon")
            shaped(f"{p}_melon_from_compressed", ["PPP", "PPP", "PPP"], {"P": f"{NS}:1x_melon"}, cur)
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
    for crop in ("wheat", "beetroot"):
        for i, p in enumerate(LV[:CROP_MAX_LEVEL]):
            cur = f"{NS}:{p}_{crop}_seeds"
            prev = f"minecraft:{crop}_seeds" if i == 0 else f"{NS}:{LV[i - 1]}_{crop}_seeds"
            compress(cur, prev)
            unpack(cur, prev)
    for seed_id in EXTRA_SEEDS:
        for i, p in enumerate(LV[:CROP_MAX_LEVEL]):
            cur = f"{NS}:{p}_{seed_id}"
            prev = f"minecraft:{seed_id}" if i == 0 else f"{NS}:{LV[i - 1]}_{seed_id}"
            compress(cur, prev)
            unpack(cur, prev)
    # ---- 压缩甘蔗
    for i, p in enumerate(LV):
        cur = f"{NS}:{p}_cane"
        if i == 0:
            compress(cur, "minecraft:sugar_cane")
            unpack(cur, "minecraft:sugar_cane")
        else:
            compress(cur, f"{NS}:{LV[i - 1]}_cane")
            unpack(cur, f"{NS}:{LV[i - 1]}_cane")
    for mat in MATERIALS:
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
    # ---- 盆栽与漏斗盆栽
    dump(f"{data}/{NS}/tags/items/pot_material.json", {"replace": False, "values": [
        f"{NS}:1x_{m[0]}" for m in MATERIALS]})
    dump(f"{data}/minecraft/tags/items/pot_stones.json", {"replace": False, "values": [
        "minecraft:cobblestone", "minecraft:stone", "minecraft:granite", "minecraft:diorite",
        "minecraft:andesite", "minecraft:blackstone", "minecraft:red_sandstone", "minecraft:sandstone"]})
    shaped("pot", ["A A", "AAA"], {"A": {"tag": "minecraft:pot_stones"}}, f"{NS}:pot")
    shapeless("hopper_pot", [f"{NS}:pot", "minecraft:hopper"], f"{NS}:hopper_pot")
    # ---- 刷石机
    shaped("cobblestone_generator",
           ["RIR", "BCL", "OHO"],
           {"R": "minecraft:redstone", "I": "minecraft:iron_ingot",
            "B": "minecraft:water_bucket", "C": "minecraft:cobblestone",
            "L": "minecraft:lava_bucket", "O": "minecraft:obsidian",
            "H": "minecraft:hopper"},
           f"{NS}:cobblestone_generator")
    for i, name in enumerate(GENERATOR_TIERS):
        if i == 0:
            continue
        compress(f"{NS}:{name}", f"{NS}:{GENERATOR_TIERS[i - 1]}")
        unpack(f"{NS}:{name}", f"{NS}:{GENERATOR_TIERS[i - 1]}")
    # ---- 压缩箱子/潜影盒：9→1（不做解包）
    shaped("compressed_chest", ["AAA", "AAA", "AAA"], {"A": "minecraft:chest"},
           f"{NS}:compressed_chest")
    shaped("compressed_shulker_box", ["AAA", "AAA", "AAA"], {"A": "minecraft:shulker_box"},
           f"{NS}:compressed_shulker_box")
    # ---- 压缩金属包：#forge:ingots/<key>（空标签供其它 mod 合并）→ 1x；9↔1 升降级
    for key, _, _, _, _ in COMPAT_METALS:
        for i, p in enumerate(LV):
            cur = f"{NS}:{p}_{key}_block"
            if i == 0:
                shaped(f"{p}_{key}_block", ["PPP", "PPP", "PPP"],
                       {"P": {"tag": f"forge:ingots/{key}"}}, cur)
            else:
                compress(cur, f"{NS}:{LV[i - 1]}_{key}_block")
                unpack(cur, f"{NS}:{LV[i - 1]}_{key}_block")
        dump(f"{data}/forge/tags/items/ingots/{key}.json", {"replace": False, "values": []})
    # ---- 食物
    shaped("1x_bread", ["WWW"], {"W": f"{NS}:1x_wheat"}, f"{NS}:1x_bread")
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        cur = f"{NS}:{p}_bread"
        if i > 0:
            compress(cur, f"{NS}:{LV[i - 1]}_bread")
        unpack(cur, "minecraft:bread" if i == 0 else f"{NS}:{LV[i - 1]}_bread")
    for food in ("beef", "rotten_flesh"):
        for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
            cur = f"{NS}:{p}_{food}"
            prev = f"minecraft:{food}" if i == 0 else f"{NS}:{LV[i - 1]}_{food}"
            compress(cur, prev)
            unpack(cur, prev)
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        for raw, cooked in COOKED_FROM:
            for kind, rtype, seconds in (
                    ("smelting", "minecraft:smelting", min(9 ** i, 300)),
                    ("smoking", "minecraft:smoking", min(9 ** i / 2, 150))):
                path = f"{p}_{cooked}_{kind}"
                dump(f"{data}/{NS}/recipes/{path}.json", {
                    "type": rtype, "ingredient": {"item": f"{NS}:{p}_{raw}"},
                    "result": f"{NS}:{p}_{cooked}",
                    "experience": 0.35 * 9 ** i, "cookingtime": int(seconds * 20),
                })
                unlock(path)
    # ---- 工具（材料位=同重数标签，棍位=同重数压缩木棍）
    for mat, _, _ in TOOL_LINES:
        for p in LV:
            key_x = f"#{NS}:{mat}_tool_{p}"
            stick_id = f"{NS}:{p}_stick"
            for tool, (pattern, _) in TOOLS.items():
                name = f"{p}_{mat}_{tool}"
                shaped(name, pattern, {"X": {"tag": f"{NS}:{mat}_tool_{p}"}, "S": stick_id},
                       f"{NS}:{name}")
    # ---- 盔甲
    for p in LV:
        for piece, pattern in ARMOR_PATTERNS.items():
            shaped(f"{p}_stone_{piece}", pattern, {"X": {"tag": f"{NS}:stone_armor_{p}"}},
                   f"{NS}:{p}_stone_{piece}")
            shaped(f"{p}_wood_{piece}", pattern, {"X": {"tag": f"{NS}:wood_tool_{p}"}},
                   f"{NS}:{p}_wood_{piece}")
    # ---- 压缩原木 → 压缩木棍
    for p in LV:
        shaped(f"{p}_stick_from_log", ["L", "L"], {"L": {"tag": f"{NS}:wood_tool_{p}"}},
               f"{NS}:{p}_stick", count=16)
    # ---- 战利品表（loot_tables 复数，无 random_sequence）
    def self_drop(name):
        return {"type": "minecraft:block",
                "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                           "conditions": [{"condition": "minecraft:survives_explosion"}]}]}

    for name in storage_names() + pot_names() + GENERATOR_TIERS + compat_names() + cane_names():
        dump(f"{data}/{NS}/loot_tables/blocks/{name}.json", self_drop(name))
    dump(f"{data}/{NS}/loot_tables/blocks/compressed_chest.json", self_drop("compressed_chest"))
    dump(f"{data}/{NS}/loot_tables/blocks/compressed_shulker_box.json",
         self_drop("compressed_shulker_box"))
    for name in farmland_names():
        p = name.split("_", 1)[0]
        dump(f"{data}/{NS}/loot_tables/blocks/{name}.json", self_drop(f"{p}_dirt"))
    # 树叶（简化版）：剪刀/丝触掉自身 + 5% 树苗 + 2% 木棍
    for name in leaves_names():
        p = name.split("_", 1)[0]
        wood = name.split("_", 1)[1][: -len("_leaves")]
        dump(f"{data}/{NS}/loot_tables/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [
                {"rolls": 1.0,
                 "entries": [{"type": "minecraft:alternatives", "children": [
                     {"type": "minecraft:item", "name": f"{NS}:{name}",
                      "conditions": [{"condition": "minecraft:match_tool",
                                      "predicate": {"item": "minecraft:shears"}}]},
                     {"type": "minecraft:item", "name": f"{NS}:{name}",
                      "conditions": [{"condition": "minecraft:match_tool",
                                      "predicate": {"enchantments": ["minecraft:silk_touch"]}}]},
                 ]}]},
                {"rolls": 1.0,
                 "conditions": [{"condition": "minecraft:survives_explosion"}],
                 "entries": [{"type": "minecraft:item", "name": f"{NS}:{p}_{wood}_sapling",
                              "conditions": [{"condition": "minecraft:random_chance",
                                              "chance": 0.05}]}]},
                {"rolls": 1.0,
                 "conditions": [{"condition": "minecraft:survives_explosion"}],
                 "entries": [{"type": "minecraft:item", "name": "minecraft:stick",
                              "conditions": [{"condition": "minecraft:random_chance",
                                              "chance": 0.02}]}]},
            ]})
    # 作物：成熟产物 + 种子
    for crop, _, _, _, has_seed in CROPS:
        for p in LV[:CROP_MAX_LEVEL]:
            block = f"{NS}:{p}_{crop}_plant"
            mature = [{"condition": "minecraft:block_state_property", "block": block,
                       "properties": {"age": "7"}}]
            produce = (f"{NS}:{crop_item_id(crop, p)}" if crop in ("carrot", "potato")
                       else f"{NS}:{p}_{crop}")
            seed_item = f"{NS}:{crop_item_id(crop, p)}"
            pools = [{"rolls": 1.0,
                      "entries": [{"type": "minecraft:item", "name": produce, "functions": [
                          {"function": "minecraft:apply_bonus",
                           "enchantment": "minecraft:fortune",
                           "formula": "minecraft:binomial_with_bonus_count",
                           "parameters": {"extra": 3, "probability": 0.5714286}}],
                          "conditions": mature}]}]
            if not has_seed or crop in ("wheat", "beetroot"):
                pools.append({"rolls": 1.0, "entries": [
                    {"type": "minecraft:item", "name": seed_item,
                     "functions": [{"function": "minecraft:apply_bonus",
                                    "enchantment": "minecraft:fortune",
                                    "formula": "minecraft:binomial_with_bonus_count",
                                    "parameters": {"extra": 3, "probability": 0.5714286}}]}]})
            dump(f"{data}/{NS}/loot_tables/blocks/{p}_{crop}_plant.json",
                 {"type": "minecraft:block", "pools": pools})
    # ---- 方块/物品标签（1.16.5：tags/blocks、tags/items 复数；挖掘档位走 Java 属性，标签供其它 mod 查询）
    stone_tool, iron_tool, diamond_tool = [], [], []
    for name in storage_names():
        lv = int(name.split("_", 1)[0][:-1])
        (stone_tool if lv <= 2 else iron_tool if lv <= 4 else diamond_tool).append(f"{NS}:{name}")
    for m in COMPAT_METALS:
        target = {"stone": stone_tool, "iron": iron_tool, "diamond": diamond_tool}[m[3]]
        target += [f"{NS}:{n}" for n in compat_names() if n.endswith(f"_{m[0]}_block")]
    logs = [f"{NS}:{p}_{w[0]}_log" for w in WOODS for p in LV]
    pickaxe = ([f"{NS}:{n}" for n in storage_names() + pot_names() + GENERATOR_TIERS
                + compat_names()] + [f"{NS}:compressed_shulker_box"])
    for tag, values in [
            ("blocks/needs_stone_tool", stone_tool), ("blocks/needs_iron_tool", iron_tool),
            ("blocks/needs_diamond_tool", diamond_tool),
            ("blocks/logs", logs), ("blocks/leaves", [f"{NS}:{n}" for n in leaves_names()]),
            ("blocks/shulker_boxes", [f"{NS}:compressed_shulker_box"]),
            ("items/logs", logs),
            ("items/shulker_boxes", [f"{NS}:compressed_shulker_box"])]:
        dump(f"{data}/minecraft/tags/{tag}.json", {"replace": False, "values": sorted(values)})
    # 工具/盔甲修复与合成材料标签（mod 命名空间）
    for mat, _, _ in TOOL_LINES:
        for p in LV:
            if mat == "cobblestone":
                values = [f"{NS}:{p}_cobblestone"]
            else:
                values = sorted(f"{NS}:{p}_{w[0]}_log" for w in WOODS)
            dump(f"{data}/{NS}/tags/items/{mat}_tool_{p}.json",
                 {"replace": False, "values": values})
    for p in LV:
        dump(f"{data}/{NS}/tags/items/stone_armor_{p}.json",
             {"replace": False, "values": [f"{NS}:{p}_stone", f"{NS}:{p}_cobblestone"]})
        dump(f"{data}/{NS}/tags/items/wood_tool_{p}.json",
             {"replace": False, "values": sorted(f"{NS}:{p}_{w[0]}_log" for w in WOODS)})


def main():
    res = os.path.join(SUB, "src", "main", "resources", "assets", NS)
    data = os.path.join(SUB, "src", "main", "resources", "data")
    # 生成目录归本脚本所有：先清空（textures/ 归 gen_1165_assets.py 所有，跳过）
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
    blocks = block_names()
    items = item_names()
    assert len(storage_names()) == 351
    assert len(cane_names()) == 360
    assert len(compat_names()) == 198
    # 方块：351 储存 + 54 叶 + 54 苗 + 9 耕地 + 12 作物 + 360 甘蔗 + 2 盆栽 + 3 刷石机 + 198 金属 + 2 容器
    assert len(blocks) == 1045, len(blocks)
    assert len(items) == 1273, len(items)
    assert len(tool_names()) == 90 and len(armor_names()) == 72 and len(food_names()) == 54
    print(f"blocks={len(blocks)} items={len(items)} "
          f"(tools={len(tool_names())} armor={len(armor_names())} food={len(food_names())} "
          f"leaves={len(leaves_names())} saplings={len(sapling_names())} canes={len(cane_names())})")
    print(f"generated into {SUB}/src/main/resources")


if __name__ == "__main__":
    main()
