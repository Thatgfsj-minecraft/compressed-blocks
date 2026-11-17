#!/usr/bin/env python3
"""资源生成共享层：常量、名称 helper、JSON 写出工具、生成目录所有权清理。

原 gen_resources.py 第 1~437 行的可复用部分，被薄入口 gen_resources.py 与
res_assets.py / res_lang.py / res_data.py 共同导入；拆分不改变任何生成结果。
"""

import json
import os
from pathlib import Path

import hopper_tiers  # 同目录：漏斗分级定义，lang/标签与两个漏斗生成器共用

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NS = "compressedblocks"
VANILLA_DATA = os.path.join(ROOT, "_asset-src", "vanilla-data")
LEGACY = False  # 1.21.1 冻结在 0.2.0，不再生成

# ---------------------------------------------------------------- 清理边界
# 本脚本拥有的 data 命名空间：只有这些会在生成前被清空重建。
# neoforge/（loader 专属 data map：燃料图）以及其它模组命名空间不在其列，永不删除。
OWNED_DATA_NAMESPACES = (NS, "minecraft", "c", "ftbultimine")

# 生成目录里**不属于**本脚本的资产，清理时必须原样保留：
#   * template_hopper / template_hopper_side —— 手写自包含模型模板（本脚本不产出）；
#   * 1~3 重压缩铁漏斗、木质漏斗与 1~3 重压缩木质漏斗 —— 由 gen_hopper_assets.py /
#     gen_wooden_hopper_assets.py 生成 blockstates/items/models 全套。
# 少列一个名字，单独跑本脚本就会把它们的文件当"遗留文件"删掉；新增漏斗品牌时记得同步这里。
PRESERVED_ASSET_STEMS = frozenset(
    # 手写自包含模型模板（本脚本不产出，两个漏斗生成器引用它当 parent）
    {"template_hopper", "template_hopper_side"}
    # 漏斗全套资产（blockstates/items/models）：分级定义见 scripts/hopper_tiers.py
    | {name for _tier, name, _en, _zh in hopper_tiers.ALL_HOPPERS}
)

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
    ("beef", "Raw Beef", "生牛肉"),
    ("cooked_beef", "Steak", "牛排"),
    ("porkchop", "Raw Porkchop", "生猪排"),
    ("cooked_porkchop", "Cooked Porkchop", "熟猪排"),
    ("mutton", "Raw Mutton", "羊肉"),
    ("cooked_mutton", "Cooked Mutton", "熟羊肉"),
    ("chicken", "Raw Chicken", "生鸡肉"),
    ("cooked_chicken", "Cooked Chicken", "熟鸡肉"),
    ("rabbit", "Raw Rabbit", "生兔肉"),
    ("cooked_rabbit", "Cooked Rabbit", "熟兔肉"),
    ("cod", "Raw Cod", "生鳕鱼"),
    ("cooked_cod", "Cooked Cod", "熟鳕鱼"),
    ("salmon", "Raw Salmon", "生鲑鱼"),
    ("cooked_salmon", "Cooked Salmon", "熟鲑鱼"),
    ("melon", "Watermelon", "西瓜"),
    ("rotten_flesh", "Rotten Flesh", "腐肉"),
    ("baked_potato", "Baked Potato", "烤土豆"),
    ("apple", "Apple", "苹果"),
]
# 烤制：生的 → 熟的（烤土豆=土豆）
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
# 作物 age→阶段贴图（小麦 8 段直映；其余 4 段两 age 一段）
AGE_TO_STAGE = {0: 0, 1: 0, 2: 1, 3: 1, 4: 2, 5: 2, 6: 3, 7: 3}


def dump(path, obj, indent=None):
    text = json.dumps(obj, ensure_ascii=False, separators=(",", ":"), indent=indent) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def dump_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def dump_pretty(path, obj):
    """缩进 2 的标准 JSON（与仓库里手写/其它生成器的 pretty 文件逐字节一致）。"""
    text = json.dumps(obj, ensure_ascii=False, indent=2) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def dump_hopper_tag(path, obj):
    """漏斗生成器会重写这几张标签，且它们是最后写入者：indent=2、末尾不补换行。
    本脚本也按同一格式写，否则单独跑本脚本就会产生纯格式 diff。"""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(json.dumps(obj, ensure_ascii=False, indent=2),
                          encoding="utf-8", newline="\n")


def is_preserved_asset(rel_path):
    """rel_path 相对 assets/compressedblocks；_side 变体归一化到主干名再比对。"""
    stem = os.path.basename(rel_path)
    if stem.endswith(".json"):
        stem = stem[:-len(".json")]
    if stem.endswith("_side"):
        stem = stem[:-len("_side")]
    return stem in PRESERVED_ASSET_STEMS


def is_preserved_data(rel_path):
    """data/ 下的保留判定：漏斗的配方/战利品表归两个漏斗生成器所有（unpack_ 前缀是拆解配方）。"""
    stem = os.path.basename(rel_path)
    if stem.endswith(".json"):
        stem = stem[:-len(".json")]
    if stem.startswith("unpack_"):
        stem = stem[len("unpack_"):]
    if stem.endswith("_side"):
        stem = stem[:-len("_side")]
    return stem in PRESERVED_ASSET_STEMS


def clean_owned_data(data):
    """清空本脚本拥有的 data 命名空间，但保留两个漏斗生成器的配方/战利品表。
    loader 专属命名空间（neoforge/）根本不在 OWNED_DATA_NAMESPACES 里，永不触碰。"""
    for ns in OWNED_DATA_NAMESPACES:
        root = os.path.join(data, ns)
        if not os.path.isdir(root):
            continue
        for dirpath, _dirnames, filenames in os.walk(root, topdown=False):
            for name in filenames:
                path = os.path.join(dirpath, name)
                if is_preserved_data(os.path.relpath(path, data)):
                    continue
                os.remove(path)
            if dirpath != root and not os.listdir(dirpath):
                os.rmdir(dirpath)
        if not os.listdir(root):
            os.rmdir(root)


def clean_owned_assets(res):
    """清空本脚本拥有的资产目录：textures/ 归 gen_assets.py 整目录跳过，
    PRESERVED_ASSET_STEMS 里的文件（手写模板 + 漏斗资产）保留。"""
    for entry in sorted(os.listdir(res)):
        if entry == "textures":
            continue
        full = os.path.join(res, entry)
        if not os.path.isdir(full):
            os.remove(full)  # icon.png：本脚本重新写出
            continue
        for dirpath, _dirnames, filenames in os.walk(full, topdown=False):
            for name in filenames:
                path = os.path.join(dirpath, name)
                if is_preserved_asset(os.path.relpath(path, res)):
                    continue
                os.remove(path)
            if dirpath != full and not os.listdir(dirpath):
                os.rmdir(dirpath)
        if not os.listdir(full):
            os.rmdir(full)


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
    # 主 mod：原版色盆栽 + 漏斗盆栽（自动收割）；压缩盆栽/压缩漏斗盆栽在附属 mod
    return ["pot", "hopper_pot"]


# 刷石机（主 mod 只保留原版级；1~9 重压缩刷石机在附属 mod compressedblockspot）
GENERATOR_TIERS = ["cobblestone_generator"]


def generator_names():
    return GENERATOR_TIERS


# 压缩熔炉（4 个压缩等级：3x/9x/27x/81x 速度与阶梯权限）
FURNACE_TIERS = ["1x_compressed_furnace", "2x_compressed_furnace", "3x_compressed_furnace", "4x_compressed_furnace"]


def furnace_names():
    return FURNACE_TIERS


# 0.3.2 压缩金属包（科技/魔法 mod 经典材质预置；合成走 #c:*_ingots 通用标签实现跨 mod 适配）
# (key, en, zh, 挖掘等级: stone|iron|diamond, 发光 0-15)
COMPAT_METALS = [
    ("tin", "Tin", "锡", "stone", 0),
    ("lead", "Lead", "铅", "stone", 0),
    ("zinc", "Zinc", "锌", "stone", 0),
    ("plastic", "Plastic", "塑料", "stone", 0),
    ("silver", "Silver", "银", "iron", 0),
    ("nickel", "Nickel", "镍", "iron", 0),
    ("bronze", "Bronze", "青铜", "iron", 0),
    ("brass", "Brass", "黄铜", "iron", 0),
    ("electrum", "Electrum", "琥珀金", "iron", 0),
    ("invar", "Invar", "殷钢", "iron", 0),
    ("constantan", "Constantan", "康铜", "iron", 0),
    ("steel", "Steel", "钢", "iron", 0),
    ("manasteel", "Manasteel", "源质钢", "iron", 0),
    ("uranium", "Uranium", "铀", "diamond", 0),
    ("osmium", "Osmium", "锇", "diamond", 0),
    ("signalum", "Signalum", "信素", "diamond", 0),
    ("enderium", "Enderium", "末影锭", "diamond", 0),
    ("refined_obsidian", "Refined Obsidian", "精炼黑曜石", "diamond", 0),
    ("refined_glowstone", "Refined Glowstone", "精炼萤石", "diamond", 15),
    ("lumium", "Lumium", "流明", "diamond", 12),
    ("terrasteel", "Terrasteel", "泰拉钢", "diamond", 0),
    ("elementium", "Elementium", "元素钢", "diamond", 0),
]


def compat_names():
    return [f"{p}_{m[0]}_block" for m in COMPAT_METALS for p in LV]
