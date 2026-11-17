#!/usr/bin/env python3
"""资产域：blockstate、方块模型、物品模型、equipment 定义（gen_assets）。"""

import os
import shutil

from res_common import (
    AGE_TO_STAGE, CROP_MAX_LEVEL, CROPS, LV, NS, ROOT, armor_names, cane_names,
    compat_names, crop_item_id, crop_produce_names, dump, extra_seed_names,
    farmland_names, food_names, furnace_names, generator_names, leaves_names,
    pot_names, sapling_names, stick_names, storage_names, tool_names,
)

# ---------------------------------------------------------------- assets

def gen_assets(res):
    # 储存方块/树叶：cube_all + blockstate + 物品模型定义
    leaf_set = set(leaves_names())
    for name in storage_names() + leaves_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        if name in leaf_set:
            # 树叶透明像素：chunk 层走 CUTOUT_MIPPED（客户端入口注册），模型内 render_type 在 1.21.11 无效
            dump(f"{res}/models/block/{name}.json",
                 {"parent": "minecraft:block/cube_all",
                  "textures": {"all": f"{NS}:block/{name}"}})
        else:
            dump(f"{res}/models/block/{name}.json",
                 {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    # 树苗：cross 单模型；物品走 item/generated 独立模型（原版同构——cross 无 display，直指会全尺寸渲染）
    for name in sapling_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"stage=0": {"model": f"{NS}:block/{name}"},
                           "stage=1": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cross",
              "textures": {"cross": f"{NS}:block/{name}"}})
        dump(f"{res}/models/item/{name}.json",
             {"parent": "minecraft:item/generated",
              "textures": {"layer0": f"{NS}:block/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    # 压缩甘蔗：cross 单模型（age 属性不换图）；物品走 item/generated 独立模型
    for name in cane_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cross",
              "textures": {"cross": f"{NS}:block/{name}"}})
        dump(f"{res}/models/item/{name}.json",
             {"parent": "minecraft:item/generated",
              "textures": {"layer0": f"{NS}:block/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    # 压缩盆栽：分层盆——底板 12×12×1 + 实心腰 10×10×3 + 空心口沿 12×12×2（内孔 10×10）。
    # 所有面按方块坐标取 UV，贴图行与方块高度对齐（口沿亮/腰身暗在贴图行 0..6 分段）。
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
        pot_element([2, 0, 2], [14, 1, 14], cull_down=True),  # 底板
        pot_element([3, 1, 3], [13, 4, 13]),                  # 腰身（实心）
        pot_element([2, 4, 2], [14, 6, 3]),                   # 口沿北
        pot_element([2, 4, 13], [14, 6, 14]),                 # 口沿南
        pot_element([2, 4, 3], [3, 6, 13]),                   # 口沿西
        pot_element([13, 4, 3], [14, 6, 13]),                 # 口沿东
    ]
    for name in pot_names():
        hopper = name == "hopper_pot"
        side_tex = f"{NS}:block/pot_hopper_side" if hopper else f"{NS}:block/pot_side"
        bottom_tex = f"{NS}:block/pot_hopper_bottom" if hopper else f"{NS}:block/pot_bottom"
        model = {
            "gui_light": "side",
            "display": pot_display,
            "textures": {"particle": side_tex, "side": side_tex,
                         "top": f"{NS}:block/pot_top", "bottom": bottom_tex},
            "elements": pot_elements,
        }
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json", model)
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    # 刷石机（3 个压缩等级）：整体方块——顶面纯木板、底面纯铁、侧面三层；物品继承方块模型（3D 图标）
    for name in generator_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cube",
              "textures": {"up": f"{NS}:block/{name}_top", "down": f"{NS}:block/{name}_bottom",
                           "north": f"{NS}:block/{name}", "south": f"{NS}:block/{name}",
                           "west": f"{NS}:block/{name}", "east": f"{NS}:block/{name}",
                           "particle": f"{NS}:block/{name}"}})
        dump(f"{res}/models/item/{name}.json",
             {"parent": f"{NS}:block/{name}"})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    # 压缩熔炉（4 个压缩等级）：带朝向与 lit 状态
    for name in furnace_names():
        dump(f"{res}/blockstates/{name}.json", {
            "variants": {
                "facing=east,lit=false": {"model": f"{NS}:block/{name}", "y": 90},
                "facing=east,lit=true": {"model": f"{NS}:block/{name}_on", "y": 90},
                "facing=north,lit=false": {"model": f"{NS}:block/{name}"},
                "facing=north,lit=true": {"model": f"{NS}:block/{name}_on"},
                "facing=south,lit=false": {"model": f"{NS}:block/{name}", "y": 180},
                "facing=south,lit=true": {"model": f"{NS}:block/{name}_on", "y": 180},
                "facing=west,lit=false": {"model": f"{NS}:block/{name}", "y": 270},
                "facing=west,lit=true": {"model": f"{NS}:block/{name}_on", "y": 270}
            }
        })
        dump(f"{res}/models/block/{name}.json", {
            "parent": "minecraft:block/orientable",
            "textures": {
                "front": f"{NS}:block/{name}_front",
                "side": f"{NS}:block/{name}_side",
                "top": f"{NS}:block/{name}_top"
            }
        })
        dump(f"{res}/models/block/{name}_on.json", {
            "parent": "minecraft:block/orientable",
            "textures": {
                "front": f"{NS}:block/{name}_front_on",
                "side": f"{NS}:block/{name}_side",
                "top": f"{NS}:block/{name}_top"
            }
        })
        dump(f"{res}/items/{name}.json", {
            "model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}
        })
    # 压缩箱子（带朝向，正面锁扣）：与潜影盒同款 BER 实体渲染（原版开盖动画），
    # 方块模型只留 particle；物品走原版 chest special 渲染（texture 填短 id，
    # mapper 自动加 entity/chest/ 前缀），底模用原版 template_chest 平面图标
    dump(f"{res}/models/block/compressed_chest.json",
         {"textures": {"particle": f"{NS}:block/compressed_chest_side"}})
    dump(f"{res}/blockstates/compressed_chest.json",
         {"variants": {"facing=north": {"model": f"{NS}:block/compressed_chest"},
                       "facing=east": {"model": f"{NS}:block/compressed_chest", "y": 90},
                       "facing=south": {"model": f"{NS}:block/compressed_chest", "y": 180},
                       "facing=west": {"model": f"{NS}:block/compressed_chest", "y": 270}}})
    dump(f"{res}/models/item/compressed_chest.json",
         {"parent": "minecraft:item/template_chest",
          "textures": {"particle": f"{NS}:block/compressed_chest_side"}})
    dump(f"{res}/items/compressed_chest.json",
         {"model": {"type": "minecraft:special", "base": f"{NS}:item/compressed_chest",
                    "model": {"type": "minecraft:chest", "texture": f"{NS}:compressed_chest"}}})
    # 压缩潜影盒：BER 实体渲染（开盖动画），方块模型只留 particle；
    # 物品走原版 shulker_box special 渲染（texture 填短 id，mapper 自动加 entity/shulker/ 前缀）
    dump(f"{res}/models/block/compressed_shulker_box.json",
         {"textures": {"particle": f"{NS}:block/compressed_shulker_box_side"}})
    dump(f"{res}/blockstates/compressed_shulker_box.json",
         {"variants": {"": {"model": f"{NS}:block/compressed_shulker_box"}}})
    dump(f"{res}/models/item/compressed_shulker_box.json",
         {"parent": "minecraft:item/template_shulker_box",
          "textures": {"particle": f"{NS}:block/compressed_shulker_box_side"}})
    dump(f"{res}/items/compressed_shulker_box.json",
         {"model": {"type": "minecraft:special", "base": f"{NS}:item/compressed_shulker_box",
                    "model": {"type": "minecraft:shulker_box", "texture": f"{NS}:compressed_shulker_box"}}})
    # 压缩金属包：cube_all + 压缩环贴图（同储存方块渲染与 3D 物品图标）
    for name in compat_names():
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cube_all",
              "textures": {"all": f"{NS}:block/{name}"}})
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
                     {"parent": "minecraft:block/crop",
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
