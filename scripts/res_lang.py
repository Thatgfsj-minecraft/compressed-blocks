#!/usr/bin/env python3
"""语言域：en_us / zh_cn 语言文件（gen_lang）。"""

import json

import hopper_tiers

from res_common import (
    ARMOR_EN, ARMOR_ZH, COMPAT_METALS, CROP_MAX_LEVEL, CROPS, FOODS, LEVELS, LV,
    NS, TOOL_LINES, TOOLS, WOODS, armor_names, cane_en, cane_key, cane_names,
    cane_zh, crop_block_names, crop_item_id, dump_text, extra_seed_names,
    farmland_names, food_names, furnace_names, generator_names, leaves_names,
    sapling_names, stick_names, storage_materials, storage_names, tool_names,
)

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
    en["block." + NS + ".pot"] = "Pot"
    zh["block." + NS + ".pot"] = "盆栽"
    en["block." + NS + ".hopper_pot"] = "Hopper Pot"
    zh["block." + NS + ".hopper_pot"] = "漏斗盆栽"
    generator_lang = {"cobblestone_generator": ("Cobblestone Generator", "刷石机")}
    for name in generator_names():
        en_name, zh_name = generator_lang[name]
        en["block." + NS + "." + name] = en_name
        zh["block." + NS + "." + name] = zh_name
    furnace_lang = {"1x_compressed_furnace": ("Compressed Furnace", "一重压缩熔炉"),
                    "2x_compressed_furnace": ("Double Compressed Furnace", "二重压缩熔炉"),
                    "3x_compressed_furnace": ("Triple Compressed Furnace", "三重压缩熔炉"),
                    "4x_compressed_furnace": ("Quadruple Compressed Furnace", "四重压缩熔炉")}
    for name in furnace_names():
        en_name, zh_name = furnace_lang[name]
        en["block." + NS + "." + name] = en_name
        zh["block." + NS + "." + name] = zh_name
        en["container." + NS + "." + name] = en_name
        zh["container." + NS + "." + name] = zh_name
    en["block." + NS + ".compressed_chest"] = "Compressed Chest"
    zh["block." + NS + ".compressed_chest"] = "压缩箱子"
    en["block." + NS + ".compressed_shulker_box"] = "Compressed Shulker Box"
    zh["block." + NS + ".compressed_shulker_box"] = "压缩潜影盒"
    for key, mat_en, mat_zh, _, _ in COMPAT_METALS:
        for lv in LEVELS:
            en[f"block.{NS}.{lv[0]}_{key}_block"] = f"{lv[1]} Compressed {mat_en} Block"
            zh[f"block.{NS}.{lv[0]}_{key}_block"] = f"{lv[2]}压缩{mat_zh}块"
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
    # 漏斗条目：与两个漏斗生成器共用同一份分级定义，追加在排序内容之后
    # （漏斗生成器也是这个顺序、这个位置追加的），末尾不补换行（它们是最后写入者）。
    for lang_name, table in (("en_us", en), ("zh_cn", zh)):
        data = dict(sorted(table.items()))
        for _tier, name, en_name, zh_name in hopper_tiers.ALL_HOPPERS:
            label = en_name if lang_name == "en_us" else zh_name
            data[f"block.{NS}.{name}"] = label
            data[f"container.{NS}.{name}"] = label
        dump_text(f"{res}/lang/{lang_name}.json",
                  json.dumps(data, ensure_ascii=False, indent=2))
