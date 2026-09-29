#!/usr/bin/env python3
"""压缩方块 mod 资源生成器：blockstate/模型/物品模型定义/配方/战利品表/标签/语言文件。
输出必须与仓库内容逐字节一致：重跑本脚本后 git status 应保持为空。

命名规则（与 CompressedBlocks.java 严格一致）：
  方块   <prefix>_<mat>                        prefix ∈ compressed/double_compressed/.../nonuple_compressed
  工具   <prefix>_<mat>_<tool>                 tool ∈ pickaxe/axe/shovel/hoe/sword
  木棍   <prefix>_stick
用法：python scripts/gen_resources.py   （写 1.21.11 下 fabric/neoforge 两个子项目）
"""
import json
import os
import shutil
from pathlib import Path

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SUBS = [
    os.path.join(ROOT, "1.21.11", "fabric"),
    os.path.join(ROOT, "1.21.11", "neoforge"),
]
NS = "compressedblocks"

LEVELS = [
    ("compressed", "Compressed", "压缩"),
    ("double_compressed", "Double Compressed", "二重压缩"),
    ("triple_compressed", "Triple Compressed", "三重压缩"),
    ("quadruple_compressed", "Quadruple Compressed", "四重压缩"),
    ("quintuple_compressed", "Quintuple Compressed", "五重压缩"),
    ("sextuple_compressed", "Sextuple Compressed", "六重压缩"),
    ("septuple_compressed", "Septuple Compressed", "七重压缩"),
    ("octuple_compressed", "Octuple Compressed", "八重压缩"),
    ("nonuple_compressed", "Nonuple Compressed", "九重压缩"),
]

# key: (en, zh, 可做工具)
MATERIALS = [
    ("cobblestone", "Cobblestone", "圆石", True),
    ("stone", "Stone", "石头", True),
    ("cobbled_deepslate", "Cobbled Deepslate", "深板岩圆石", True),
    ("deepslate", "Deepslate", "深板岩", True),
    ("oak_log", "Oak Log", "橡木原木", True),
    ("spruce_log", "Spruce Log", "云杉原木", True),
    ("birch_log", "Birch Log", "白桦原木", True),
    ("jungle_log", "Jungle Log", "丛林原木", True),
    ("acacia_log", "Acacia Log", "金合欢原木", True),
    ("dark_oak_log", "Dark Oak Log", "深色橡木原木", True),
    ("mangrove_log", "Mangrove Log", "红树原木", True),
    ("cherry_log", "Cherry Log", "樱花原木", True),
    ("pale_oak_log", "Pale Oak Log", "苍白橡木原木", True),
    ("dirt", "Dirt", "泥土", False),
    ("sand", "Sand", "沙子", False),
    ("gravel", "Gravel", "沙砾", False),
    ("netherrack", "Netherrack", "下界岩", False),
    ("end_stone", "End Stone", "末地石", False),
    ("obsidian", "Obsidian", "黑曜石", False),
]

TOOLS = {
    "pickaxe": (["XXX", " S ", " S "], "镐"),
    "axe": (["XX", "XS", " S"], "斧"),
    "shovel": (["X", "S", "S"], "锹"),
    "hoe": (["XX", " S", " S"], "锄"),
    "sword": (["X", "X", "S"], "剑"),
}


def dump(path, obj, indent=None):
    text = json.dumps(obj, ensure_ascii=False, separators=(",", ":"), indent=indent) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def block_names():
    return [f"{p}_{mat}" for mat, _, _, _ in MATERIALS for p, _, _ in LEVELS]


def tool_names():
    return [f"{p}_{mat}_{t}" for mat, _, _, can in MATERIALS if can
            for p, _, _ in LEVELS for t in TOOLS]


def stick_names():
    return [f"{p}_stick" for p, _, _ in LEVELS]


def gen_assets(res, blocks, tools, sticks):
    for name in blocks:
        dump(f"{res}/blockstates/{name}.json",
             {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        dump(f"{res}/models/block/{name}.json",
             {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    for name in tools:
        dump(f"{res}/models/item/{name}.json",
             {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    for name in sticks:
        dump(f"{res}/models/item/{name}.json",
             {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        dump(f"{res}/items/{name}.json",
             {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    icon_src = os.path.join(ROOT, "_asset-src", "icon.png")
    icon_dst = f"{res}/icon.png"
    os.makedirs(os.path.dirname(icon_dst), exist_ok=True)
    shutil.copyfile(icon_src, icon_dst)


def gen_lang(res, blocks, tools, sticks):
    en, zh = {}, {}
    EN_TOOLS = {"pickaxe": "Pickaxe", "axe": "Axe", "shovel": "Shovel", "hoe": "Hoe", "sword": "Sword"}
    name_set = {("block", n) for n in blocks} | {("item", n) for n in tools + sticks}
    for kind, name in name_set:
        lv = next(l for l in LEVELS if name.startswith(l[0] + "_"))
        rest = name[len(lv[0]) + 1:]
        # LEVELS 的 en 前缀（"Compressed"/"Double Compressed"/...）本身已含 Compressed，直接用
        if name.endswith("_stick"):
            en[f"{kind}.{NS}.{name}"] = f"{lv[1]} Stick"
            zh[f"{kind}.{NS}.{name}"] = f"{lv[2]}木棍"
            continue
        mat_key = rest.rsplit("_", 1)[0] if name in tools else rest
        mat = next(m for m in MATERIALS if m[0] == mat_key)
        if name in tools:
            tool_id = rest.rsplit("_", 1)[1]
            en[f"{kind}.{NS}.{name}"] = f"{lv[1]} {mat[1]} {EN_TOOLS[tool_id]}"
            zh[f"{kind}.{NS}.{name}"] = f"{lv[2]}{mat[2]}{TOOLS[tool_id][1]}"
        else:
            en[f"{kind}.{NS}.{name}"] = f"{lv[1]} {mat[1]}"
            zh[f"{kind}.{NS}.{name}"] = f"{lv[2]}{mat[2]}"
    en[f"itemGroup.{NS}"] = "Compressed Blocks"
    zh[f"itemGroup.{NS}"] = "压缩"
    dump(f"{res}/lang/en_us.json", dict(sorted(en.items())), indent=2)
    dump(f"{res}/lang/zh_cn.json", dict(sorted(zh.items())), indent=2)


def prev_item(mat, level):
    """第 level 重方块的上一级：level 1 = 原版方块，否则本 mod 第 level-1 重。"""
    if level == 1:
        return f"minecraft:{mat}"
    return f"{NS}:{LEVELS[level - 2][0]}_{mat}"


def gen_data(data, blocks, tools):
    # 方块与木棍的压缩/解压链
    chains = [(mat, lv) for mat, _, _, _ in MATERIALS for lv in range(1, 10)] + [("stick", lv) for lv in range(1, 10)]
    for mat, lv in chains:
        if mat == "stick":
            cur = f"{NS}:{LEVELS[lv - 1][0]}_stick"
            prev = "minecraft:stick" if lv == 1 else f"{NS}:{LEVELS[lv - 2][0]}_stick"
        else:
            cur = f"{NS}:{LEVELS[lv - 1][0]}_{mat}"
            prev = prev_item(mat, lv)
        cur_file = cur.split(":", 1)[1]  # 不能用 os.path.basename：Windows 会把 id 里的冒号当盘符
        dump(f"{data}/{NS}/recipe/{cur_file}.json", {
            "type": "minecraft:crafting_shaped", "category": "building" if mat != "stick" else "ingredients",
            "pattern": ["PPP", "PPP", "PPP"], "key": {"P": prev},
            "result": {"count": 1, "id": cur},
        })
        dump(f"{data}/{NS}/recipe/unpack_{cur_file}.json", {
            "type": "minecraft:crafting_shapeless", "category": "building" if mat != "stick" else "ingredients",
            "ingredients": [cur],
            "result": {"count": 9, "id": prev},
        })
    # 工具：材料位=同重数压缩方块，棍位=同重数压缩木棍
    for mat, _, _, can in MATERIALS:
        if not can:
            continue
        for lv in range(1, 10):
            block_id = f"{NS}:{LEVELS[lv - 1][0]}_{mat}"
            stick_id = f"{NS}:{LEVELS[lv - 1][0]}_stick"
            for tool, (pattern, _) in TOOLS.items():
                name = f"{LEVELS[lv - 1][0]}_{mat}_{tool}"
                dump(f"{data}/{NS}/recipe/{name}.json", {
                    "type": "minecraft:crafting_shaped", "category": "equipment",
                    "pattern": pattern, "key": {"X": block_id, "S": stick_id},
                    "result": {"count": 1, "id": f"{NS}:{name}"},
                })
    # 战利品表：掉自身
    for name in blocks:
        dump(f"{data}/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{name}",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                       "conditions": [{"condition": "minecraft:survives_explosion"}],
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}],
        })
    # 方块标签（并入原版命名空间，replace=false 合并）
    pickaxe = [f"{NS}:{LEVELS[lv - 1][0]}_{m}" for m in ("cobblestone", "stone", "cobbled_deepslate", "deepslate", "netherrack", "end_stone", "obsidian") for lv in range(1, 10)]
    shovel = [f"{NS}:{LEVELS[lv - 1][0]}_{m}" for m in ("dirt", "sand", "gravel") for lv in range(1, 10)]
    axe = [f"{NS}:{LEVELS[lv - 1][0]}_{m}" for m, _, _, _ in MATERIALS if m.endswith("_log") for lv in range(1, 10)]
    stone_tool = [f"{NS}:{LEVELS[lv - 1][0]}_{m}" for m in ("deepslate", "cobbled_deepslate") for lv in range(1, 10)]
    diamond_tool = [f"{NS}:{LEVELS[lv - 1][0]}_obsidian" for lv in range(1, 10)]
    for tag, values in [("mineable/pickaxe", pickaxe), ("mineable/shovel", shovel),
                        ("mineable/axe", axe), ("needs_stone_tool", stone_tool),
                        ("needs_diamond_tool", diamond_tool)]:
        dump(f"{data}/minecraft/tags/block/{tag}.json", {"replace": False, "values": values})
    # 工具修复材料标签：该材料任意重数压缩方块
    for mat, _, _, can in MATERIALS:
        if not can:
            continue
        values = [f"{NS}:{LEVELS[lv - 1][0]}_{mat}" for lv in range(1, 10)]
        dump(f"{data}/{NS}/tags/item/repair_{mat}.json", {"replace": False, "values": values})


def main():
    blocks, tools, sticks = block_names(), tool_names(), stick_names()
    print(f"blocks={len(blocks)} tools={len(tools)} sticks={len(sticks)}")
    assert len(blocks) == 171 and len(tools) == 585 and len(sticks) == 9
    for sub in SUBS:
        if not os.path.exists(os.path.join(sub, "build.gradle")):
            print(f"skip {sub} (no build.gradle)")
            continue
        res = f"{sub}/src/main/resources/assets/{NS}"
        data = f"{sub}/src/main/resources/data"
        gen_assets(res, blocks, tools, sticks)
        gen_lang(res, blocks, tools, sticks)
        gen_data(data, blocks, tools)
        print(f"generated resources into {sub}")


if __name__ == "__main__":
    main()
