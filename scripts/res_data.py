#!/usr/bin/env python3
"""数据域：配方、战利品表、标签、世界树特征（gen_data 及其 helper）。"""

import os
import re
from pathlib import Path

import hopper_tiers

from res_common import (
    ARMOR_PATTERNS, BUILD_MATERIALS, COMPAT_METALS, COOKED_FROM,
    CROP_MAX_LEVEL, CROPS, EXTRA_SEEDS, FOOD_MAX_LEVEL, LV, MATERIALS, NS,
    TOOL_LINES, TOOLS, VANILLA_DATA, WOODS, armor_names, cane_key, cane_names,
    compat_names, crop_block_names, crop_item_id, dump, dump_hopper_tag,
    dump_pretty, dump_text, farmland_names, furnace_names, generator_names,
    leaves_names, pot_names, sapling_names, storage_materials, storage_names,
    tool_names,
)

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
    # ---- 压缩盆栽：石头类（原石/石头/花岗岩……）船形合成；只能种原版作物/甘蔗/树木（Java 侧限定）
    # pot_material：只含一重压缩方块（附属压缩盆栽配方）。原版石头走 pot_stones 合成主 mod 盆栽，
    # 两个配方不再在原石输入上撞车（否则原石船形会被附属压缩盆栽抢走）
    dump(f"{data}/{NS}/tags/item/pot_material.json", {"replace": False, "values": [
        f"{NS}:1x_{m[0]}" for m in storage_materials()]})
    dump(f"{data}/minecraft/tags/item/pot_stones.json", {"replace": False, "values": [
        "minecraft:cobblestone", "minecraft:stone", "minecraft:granite", "minecraft:diorite",
        "minecraft:andesite", "minecraft:cobbled_deepslate", "minecraft:deepslate",
        "minecraft:blackstone", "minecraft:tuff", "minecraft:calcite", "minecraft:dripstone_block",
    ]})
    shaped("pot", ["A A", "AAA"], {"A": "#minecraft:pot_stones"}, f"{NS}:pot")
    # 漏斗盆栽 = 盆栽 + 漏斗（无序；成熟自动收割入下方容器）
    shapeless("hopper_pot", [f"{NS}:pot", "minecraft:hopper"], f"{NS}:hopper_pot")
    # 木漏斗版：造价低，允许用木质漏斗直接合成漏斗盆栽（与铁漏斗配方并存）
    shapeless("hopper_pot_from_wooden_hopper", [f"{NS}:pot", f"{NS}:wooden_hopper"],
              f"{NS}:hopper_pot")
    # ---- 刷石机：红石/铁锭/红石 + 水桶/原石/岩浆桶 + 黑曜石/漏斗/黑曜石；机器可 9↔1 压缩（最多三重）
    shaped("cobblestone_generator",
           ["RIR", "BCL", "OHO"],
           {"R": "minecraft:redstone", "I": "minecraft:iron_ingot",
            "B": "minecraft:water_bucket", "C": "minecraft:cobblestone",
            "L": "minecraft:lava_bucket", "O": "minecraft:obsidian",
            "H": "minecraft:hopper"},
           f"{NS}:cobblestone_generator")
    # 木漏斗版：木质漏斗替代铁漏斗（两条配方并存，与漏斗盆栽同款惯例）
    shaped("cobblestone_generator_from_wooden_hopper",
           ["RIR", "BCL", "OHO"],
           {"R": "minecraft:redstone", "I": "minecraft:iron_ingot",
            "B": "minecraft:water_bucket", "C": "minecraft:cobblestone",
            "L": "minecraft:lava_bucket", "O": "minecraft:obsidian",
            "H": f"{NS}:wooden_hopper"},
           f"{NS}:cobblestone_generator")
    for i, name in enumerate(generator_names()):
        if i == 0:
            continue
        compress(f"{NS}:{name}", f"{NS}:{generator_names()[i - 1]}")
        unpack(f"{NS}:{name}", f"{NS}:{generator_names()[i - 1]}")
    # ---- 压缩熔炉：1重：8×1x圆石围绕1原版熔炉，或9×原版熔炉；2~4重同理围绕上一级熔炉，或9×上一级熔炉
    for i, name in enumerate(furnace_names()):
        tier = i + 1
        prev_furnace = "minecraft:furnace" if i == 0 else f"{NS}:{furnace_names()[i - 1]}"
        cobble = f"{NS}:{tier}x_cobblestone"
        shaped(f"{name}_surround", ["CCC", "CFC", "CCC"], {"C": cobble, "F": prev_furnace},
               f"{NS}:{name}", category="misc")
        compress(f"{NS}:{name}", prev_furnace)
        unpack(f"{NS}:{name}", prev_furnace)
    # ---- 压缩箱子/压缩潜影盒：9→1 压缩（刻意不做 1→9 解包：带内容解包会丢数据）
    shaped("compressed_chest", ["AAA", "AAA", "AAA"], {"A": "minecraft:chest"},
           f"{NS}:compressed_chest")
    shaped("compressed_shulker_box", ["AAA", "AAA", "AAA"], {"A": "minecraft:shulker_box"},
           f"{NS}:compressed_shulker_box")
    # ---- 压缩金属包：9× 对应金属锭 → 压缩块；锭走 #c:*_ingots 通用标签实现跨 mod 适配
    for key, _, _, _, _ in COMPAT_METALS:
        for i, p in enumerate(LV):
            cur = f"{NS}:{p}_{key}_block"
            if i == 0:
                shaped(f"{p}_{key}_block", ["PPP", "PPP", "PPP"],
                       {"P": f"#c:{key}_ingots"}, cur)
            else:
                compress(cur, f"{NS}:{LV[i - 1]}_{key}_block")
                unpack(cur, f"{NS}:{LV[i - 1]}_{key}_block")
    # 注册进通用 storage_blocks 标签（父标签 + 子标签），供其它 mod 反向识别；
    # 同时生成空锭标签（replace:false），装有对应金属 mod 时自动合并，配方即刻可用
    dump(f"{data}/c/tags/item/storage_blocks.json", {"replace": False, "values": [
        f"#c:storage_blocks/{key}" for key, _, _, _, _ in COMPAT_METALS
    ] + ["#c:storage_blocks/compressedblocks"]})
    # 子标签实体（夜间审批 P3 修复：父标签引用了它却从未生成，导致悬空引用启动警告）
    dump(f"{data}/c/tags/item/storage_blocks/compressedblocks.json",
         {"replace": False, "values": [f"{NS}:{name}" for name in storage_names()]})
    for key, _, _, _, _ in COMPAT_METALS:
        dump(f"{data}/c/tags/item/storage_blocks/{key}.json",
             {"replace": False, "values": [f"{NS}:1x_{key}_block"]})
        dump(f"{data}/c/tags/item/{key}_ingots.json",
             {"replace": False, "values": []})
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
    # 压缩苹果：9 原版苹果 ↔ 1x，逐级 9↔1
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        cur = f"{NS}:{p}_apple"
        prev = "minecraft:apple" if i == 0 else f"{NS}:{LV[i - 1]}_apple"
        compress(cur, prev)
        unpack(cur, prev)
    # 压缩腐肉：无饥饿副作用（Java 侧 FoodProperties 不带任何效果），数值 ×9^层
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        cur = f"{NS}:{p}_rotten_flesh"
        prev = "minecraft:rotten_flesh" if i == 0 else f"{NS}:{LV[i - 1]}_rotten_flesh"
        compress(cur, prev)
        unpack(cur, prev)
    # 烤制食物：熔炉/烟熏炉烤同重数生的 → 熟的；烤制时间 = 9^层 秒（烟熏减半），三重封顶 5 分钟；经验 ×9^层
    for i, p in enumerate(LV[:FOOD_MAX_LEVEL]):
        for raw, cooked in COOKED_FROM:
            for kind, rtype, seconds in (
                    ("smelting", "minecraft:smelting", min(9 ** i, 300)),
                    ("smoking", "minecraft:smoking", min(9 ** i / 2, 150))):
                path = f"{p}_{cooked}_{kind}"
                dump(f"{data}/{NS}/recipe/{path}.json", {
                    "type": rtype, "category": "food",
                    "ingredient": f"{NS}:{p}_{raw}",
                    "result": {"id": f"{NS}:{p}_{cooked}"},
                    "experience": 0.35 * 9 ** i,
                    "cookingtime": int(seconds * 20),
                })
                unlock_advancement(path, f"{NS}:{p}_{cooked}", [f"{NS}:{p}_{raw}"])
    # ---- 压缩物烧炼配方矩阵 (Issue #13)
    # 1. 压缩圆石 -> 压缩石头（1~9 重）
    for i, p in enumerate(LV):
        path = f"{p}_stone_smelting"
        dump(f"{data}/{NS}/recipe/{path}.json", {
            "type": "minecraft:smelting", "category": "blocks",
            "ingredient": f"{NS}:{p}_cobblestone",
            "result": {"id": f"{NS}:{p}_stone"},
            "experience": 0.1 * (9 ** i),
            "cookingtime": 200
        })
        unlock_advancement(path, f"{NS}:{p}_stone", [f"{NS}:{p}_cobblestone"])

    # 2. 压缩深板岩圆石 -> 压缩深板岩（1~9 重）
    for i, p in enumerate(LV):
        path = f"{p}_deepslate_smelting"
        dump(f"{data}/{NS}/recipe/{path}.json", {
            "type": "minecraft:smelting", "category": "blocks",
            "ingredient": f"{NS}:{p}_cobbled_deepslate",
            "result": {"id": f"{NS}:{p}_deepslate"},
            "experience": 0.1 * (9 ** i),
            "cookingtime": 200
        })
        unlock_advancement(path, f"{NS}:{p}_deepslate", [f"{NS}:{p}_cobbled_deepslate"])

    # 3. 压缩粘土块 -> 压缩陶瓦（1~9 重）
    for i, p in enumerate(LV):
        path = f"{p}_terracotta_smelting"
        dump(f"{data}/{NS}/recipe/{path}.json", {
            "type": "minecraft:smelting", "category": "blocks",
            "ingredient": f"{NS}:{p}_clay",
            "result": {"id": f"{NS}:{p}_terracotta"},
            "experience": 0.35 * (9 ** i),
            "cookingtime": 200
        })
        unlock_advancement(path, f"{NS}:{p}_terracotta", [f"{NS}:{p}_clay"])

    # 4. 9 种压缩原木 -> 压缩煤炭块（1~9 重）
    for wood, _, _ in WOODS:
        for i, p in enumerate(LV):
            path = f"{p}_{wood}_log_smelting"
            dump(f"{data}/{NS}/recipe/{path}.json", {
                "type": "minecraft:smelting", "category": "misc",
                "ingredient": f"{NS}:{p}_{wood}_log",
                "result": {"id": f"{NS}:{p}_coal_block"},
                "experience": 0.15 * (9 ** i),
                "cookingtime": 200
            })
            unlock_advancement(path, f"{NS}:{p}_coal_block", [f"{NS}:{p}_{wood}_log"])
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
    # ---- 压缩原木 → 压缩木棍：2 根竖放 = 16 根（原版 2 木板 = 4 木棍；1 原木 = 4 木板 → 2 原木 = 16）
    for p in LV:
        shaped(f"{p}_stick_from_log", ["L", "L"], {"L": f"#{NS}:wood_tool_{p}"},
               f"{NS}:{p}_stick", count=16)
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
    # 压缩盆栽：掉落自身（盆内土壤/作物由方块实体 preRemoveSideEffects 补发）
    for name in pot_names():
        dump(f"{data}/{NS}/loot_table/blocks/{name}.json", self_drop(name))
    # 刷石机：掉落自身
    for name in generator_names():
        dump(f"{data}/{NS}/loot_table/blocks/{name}.json", self_drop(name))
    # 压缩熔炉：掉落自身
    for name in furnace_names():
        dump(f"{data}/{NS}/loot_table/blocks/{name}.json", self_drop(name))
    # 压缩箱子：掉落自身（内容洒出由 BE preRemoveSideEffects 处理）
    dump(f"{data}/{NS}/loot_table/blocks/compressed_chest.json", self_drop("compressed_chest"))
    # 压缩潜影盒：掉落自身（内容保留由 CompressedShulkerBlock#getDrops 直接构建，不走战利品表）
    dump(f"{data}/{NS}/loot_table/blocks/compressed_shulker_box.json", self_drop("compressed_shulker_box"))
    # ---- 方块标签（并入原版命名空间）
    # 所有压缩方块统一镐子采掘；挖掘等级：L1-2 石镐、L3-4 铁镐、L5+ 钻镐封顶
    pickaxe = ([f"{NS}:{n}" for n in storage_names()] + [f"{NS}:{n}" for n in pot_names()]
               + [f"{NS}:{n}" for n in generator_names()]
               + [f"{NS}:{n}" for n in furnace_names()]
               + [f"{NS}:{n}" for n in compat_names()]
               + [f"{NS}:compressed_shulker_box"])
    # 注意：压缩熔炉**不进** needs_stone_tool / needs_iron_tool —— 与原版 minecraft:furnace 一致，
    # 只要任意等级镐（含木镐）即可挖掘并掉落；仅以 #mineable/pickaxe 限定"必须用镐"。
    stone_tool = [f"{NS}:{n}" for n in storage_names() if int(n.split("_", 1)[0][:-1]) <= 2]
    iron_tool = [f"{NS}:{n}" for n in storage_names() if 3 <= int(n.split("_", 1)[0][:-1]) <= 4]
    diamond_tool = [f"{NS}:{n}" for n in storage_names() if int(n.split("_", 1)[0][:-1]) >= 5]
    # 压缩金属包挖掘等级并入同一阶梯
    for m in COMPAT_METALS:
        target = {"stone": stone_tool, "iron": iron_tool, "diamond": diamond_tool}[m[3]]
        target += [f"{NS}:{n}" for n in compat_names() if n.endswith(f"_{m[0]}_block")]
    logs = [f"{NS}:{p}_{w[0]}_log" for w in WOODS for p in LV]
    # 标签补齐（refs #88）：锹/锄按材料采掘类别登记 —— 原版锹/锄对压缩泥土/沙/黏土/雪/泥巴/
    # 耕地/干草/苔藓恢复本类工具速度（此前所有储存方块只进 #mineable/pickaxe）
    shovel_mats = {"clay", "dirt", "sand", "gravel", "mud", "snow", "gunpowder"}
    hoe_mats = {"hay_block", "moss_block"}
    shovel = [f"{NS}:{n}" for n in storage_names()
              if n.split("_", 1)[1] in shovel_mats] + [f"{NS}:{n}" for n in farmland_names()]
    hoe = [f"{NS}:{n}" for n in storage_names() if n.split("_", 1)[1] in hoe_mats]
    # 原版植物可种在压缩土壤上：#dirt/#sand 驱动原版甘蔗/仙人掌/竹子/树苗/花的 canPlaceOn 判定
    dirt_like = [f"{NS}:{n}" for n in storage_names()
                 if n.split("_", 1)[1] in {"dirt", "mud", "moss_block"}]
    sand_like = [f"{NS}:{n}" for n in storage_names() if n.split("_", 1)[1] == "sand"]
    # 信标底座 = 铁/金/钻石/绿宝石块；末影龙免疫 = 黑曜石/末地石（对齐原版成员）
    beacon = [f"{NS}:{n}" for n in storage_names()
              if n.split("_", 1)[1] in {"iron_block", "gold_block", "diamond_block", "emerald_block"}]
    dragon_immune = [f"{NS}:{n}" for n in storage_names()
                     if n.split("_", 1)[1] in {"obsidian", "end_stone"}]
    # 蜜蜂可为压缩樱花树叶授粉（原版 cherry_leaves ∈ #flowers）；#ice 供原版冰面判定
    flowers = [f"{NS}:{n}" for n in leaves_names() if n.endswith("_cherry_leaves")]
    ice = [f"{NS}:{n}" for n in storage_names() if n.split("_", 1)[1] == "blue_ice"]
    # 漏斗的采掘标签值也在这里写（漏斗生成器会重写同样内容），保证单独跑本脚本不丢条目
    iron_hoppers = [f"{NS}:{name}" for _t, name, _e, _z in hopper_tiers.IRON_HOPPERS]
    wooden_hoppers = [f"{NS}:{name}" for _t, name, _e, _z in hopper_tiers.WOODEN_HOPPERS]
    for tag, values in [("mineable/pickaxe", pickaxe + iron_hoppers),
                        ("mineable/axe", [f"{NS}:{n}" for n in pot_names()]
                         + [f"{NS}:compressed_chest"] + wooden_hoppers),
                        ("needs_stone_tool", stone_tool), ("needs_iron_tool", iron_tool + iron_hoppers),
                        ("needs_diamond_tool", diamond_tool),
                        ("logs", logs), ("logs_that_burn", logs),
                        ("leaves", [f"{NS}:{n}" for n in leaves_names()]),
                        ("saplings", [f"{NS}:{n}" for n in sapling_names()]),
                        ("crops", [f"{NS}:{n}" for n in crop_block_names()]),
                        ("mineable/shovel", shovel), ("mineable/hoe", hoe),
                        ("dirt", dirt_like), ("sand", sand_like),
                        ("beacon_base_blocks", beacon), ("dragon_immune", dragon_immune),
                        ("flowers", flowers), ("ice", ice),
                        ("shulker_boxes", [f"{NS}:compressed_shulker_box"])]:
        tag_obj = {"replace": False, "values": sorted(values)}
        if tag in ("mineable/pickaxe", "mineable/axe", "needs_iron_tool"):
            dump_hopper_tag(f"{data}/minecraft/tags/block/{tag}.json", tag_obj)
        else:
            dump(f"{data}/minecraft/tags/block/{tag}.json", tag_obj)
    # 压缩潜影盒进原版 shulker_boxes 物品标签（原版/mod 按 item 标签识别潜影盒）
    dump(f"{data}/minecraft/tags/item/shulker_boxes.json",
         {"replace": False, "values": [f"{NS}:compressed_shulker_box"]})
    # FTB Ultimine 单作物收割黑名单：id 必须与 pot_names() 一致（本体是 pot / hopper_pot，
    # 压缩盆栽在附属 mod），格式跟仓库现有文件一样是 pretty。
    dump_pretty(f"{data}/ftbultimine/tags/block/single_crop_harvesting_blacklist.json",
                {"replace": False, "values": [f"{NS}:{n}" for n in pot_names()]})
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
    # 护甲槽位标签（refs #88）：原版 #enchantable/head_armor 等直接引用四槽位标签，缺槽位导致
    # 水下呼吸/水下速掘/摔落保护/深海探索者/冰霜行者/灵魂疾行/迅捷潜行/绑定诅咒无法附上；
    # #trimmable_armor 同样引用四槽位，盔甲纹饰随之自动生效
    for slot, kind in [("head_armor", "helmet"), ("chest_armor", "chestplate"),
                       ("leg_armor", "leggings"), ("foot_armor", "boots")]:
        dump(f"{data}/minecraft/tags/item/{slot}.json",
             {"replace": False, "values": sorted(n for n in armor if n.endswith(f"_{kind}"))})
    # 木质盔甲 = 皮甲对应物：全套免疫细雪（原版 #freeze_immune_wearables 含皮甲四件）
    dump(f"{data}/minecraft/tags/item/freeze_immune_wearables.json",
         {"replace": False, "values": sorted(n for n in armor if "_wood_" in n)})
    # 压缩镐进 #cluster_max_harvestables：紫水晶簇按 4 个掉落（原版为具体物品清单，
    # 不经 #pickaxes 传递，战利品表 match_tool 直接查它）
    dump(f"{data}/minecraft/tags/item/cluster_max_harvestables.json",
         {"replace": False, "values": sorted(f"{NS}:{n}" for n in tool_names() if n.endswith("_pickaxe"))})
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
    if wood == "oak":
        # 压缩橡木叶掉压缩苹果：0.5% + 0.1%/层（1 重 0.6% … 9 重 1.4%），受时运（沿用原版相对倍数）
        level = int(p[:-1])
        tier = min(level, FOOD_MAX_LEVEL)
        base = 0.005 + 0.001 * level
        mults = (1.0, 1.1111112, 1.25, 1.6666667, 5.0)
        vals = [base * m for m in mults]
        chances = ",\n".join(f"                {v:.10f}".rstrip("0").rstrip(".") for v in vals)
        text = re.sub(r'"chances": \[\s*0\.005[^\]]*\]',
                      '"chances": [\n' + chances + "\n              ]", text)
        text = text.replace('"name": "minecraft:apple"', f'"name": "{NS}:{tier}x_apple"')
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
