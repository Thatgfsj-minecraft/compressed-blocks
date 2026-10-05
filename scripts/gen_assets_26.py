#!/usr/bin/env python3
"""26.3 数据包资源适配生成器。

1.21.11 -> 26.3 的树特征数据迁移（2026 年份版本线的资源格式漂移）：
- 目录改名：data/<ns>/worldgen/configured_feature/ 并入 data/<ns>/worldgen/feature/
  （26.3 里 configured feature 注册表与 feature 注册表合并，Registries.CONFIGURED_FEATURE 消失，
   TreeGrower 直接引用 ResourceKey<Feature>）
- 顶层平铺：26.3 的 worldgen/feature/*.json 不再有 "config" 包裹层，
  type 与配置字段直接并列在顶层（对照 26.3 原版 oak.json/acacia.json）
- 方块状态提供器：26.3 无 simple_state_provider 包裹，provider 直接就是
  {"id": ..., "properties": {...}}（对照原版 azalea_tree.json/cactus.json；
  "Name"/"Properties" 同步改为 "id"/"properties"）
- 树特征配置键改名：dirt_provider -> below_trunk_provider（26.3 原版键名）

用法：python scripts/gen_assets_26.py [--src <1.21.11源项目>] [--out-root <26.3输出项目>]
从 1.21.11/fabric 的 configured_feature 读取，全部转换后写入 26.3/fabric 的 feature 目录。
"""
import argparse
import json
import os


def convert_state_provider(obj):
    """递归把 1.21.11 的方块状态/状态提供器改成 26.3 的直接形态。

    - {"type": "minecraft:simple_state_provider", "state": {...}} -> 直接展开 state
      （26.3 provider codec 就是 {id, properties} 形态，无包裹层）
    - {"Name": ..., "Properties": {...}} -> {"id": ..., "properties": {...}}（方块状态）
    - provider 类型注册键改名：26.3 的 worldgen/block_state_provider_type 键全部剥离
      _state_provider / _block_provider 后缀（randomized_int_state_provider -> randomized_int、
      rotated_block_provider -> rotated 等，反编译 BlockStateProviderTypes 确认）
    """
    if isinstance(obj, dict):
        if obj.get("type") == "minecraft:simple_state_provider":
            return convert_state_provider(obj.get("state", {}))
        out = {}
        for k, v in obj.items():
            nk, nv = k, v
            if k == "Name" and isinstance(v, str):
                nk, nv = "id", v
            elif k == "Properties" and isinstance(v, dict):
                nk = "properties"
            elif k == "dirt_provider":
                # 26.3 树配置里改名为 below_trunk_provider
                nk = "below_trunk_provider"
            elif (k == "type" and isinstance(v, str) and v.startswith("minecraft:")
                  and (v.endswith("_state_provider") or v.endswith("_block_provider"))):
                # 状态提供器类型键：26.3 去掉了 _state_provider / _block_provider 后缀
                suffix = "_state_provider" if v.endswith("_state_provider") else "_block_provider"
                nk, nv = k, v[: -len(suffix)]
            out[nk] = convert_state_provider(nv)
        return out
    if isinstance(obj, list):
        return [convert_state_provider(x) for x in obj]
    return obj


def convert_advancement(obj):
    """26.3 配方解锁进度的条件键改名：recipe_unlocked.conditions.recipe -> recipes
    （对照 26.3 原版 data/minecraft/advancement/recipes/brewing/glass_bottle.json）。
    仅当 "recipe" 是字符串值（资源 id）时改名；criteria 名 has_the_recipe 不受影响。
    """
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if k == "recipe" and isinstance(v, str):
                k = "recipes"
            out[k] = convert_advancement(v)
        return out
    if isinstance(obj, list):
        return [convert_advancement(x) for x in obj]
    return obj


def convert_loot(obj):
    """26.3 战利品表格式升级（实测：旧复数键被静默忽略，导致树叶/作物表
    alternatives 首项无条件短路，校验器报 87 处 Unreachable entry）：
    - "conditions": [...] -> "condition"：单个直接给，多个合并为 all_of
      （26.3 条件键改单数对象，对照原版 oak_leaves.json）
    - 条件对象内 "condition": "minecraft:x" -> "type": "minecraft:x"
    - "functions": [...] -> "modifier": [...]；函数对象内 "function" -> "type"
    - block_state_property 改名 match_block，字段 block -> blocks、properties -> state
      （反编译 LootItemConditionTypes/LootItemFunctions 确认注册键）
    - match_tool 的剪刀/精准采集判定改为原版注册引用 tool/can_shear / tool/can_silk_touch
      （原版 oak_leaves.json 同款写法，绕开 26.3 DataComponentMatchers 结构漂移）
    """
    if isinstance(obj, dict):
        is_match_tool = obj.get("condition") == "minecraft:match_tool" or obj.get("type") == "minecraft:match_tool"
        if is_match_tool:
            pred = obj.get("predicate")
            if isinstance(pred, dict):
                if pred.get("items") == "minecraft:shears":
                    return "minecraft:tool/can_shear"
                preds = pred.get("predicates")
                if isinstance(preds, dict):
                    ench = preds.get("minecraft:enchantments")
                    if (isinstance(ench, list) and any(
                            isinstance(e, dict) and e.get("enchantments") == "minecraft:silk_touch"
                            for e in ench)):
                        return "minecraft:tool/can_silk_touch"
        out = {}
        conditions = None
        for k, v in obj.items():
            if k == "conditions" and isinstance(v, list):
                conditions = v
                continue
            if k == "functions" and isinstance(v, list):
                out["modifier"] = [convert_loot(x) for x in v]
                continue
            nk, nv = k, v
            if k == "condition" and isinstance(v, str):
                nk = "type"
                if v == "minecraft:block_state_property":
                    nv = "minecraft:match_block"
            elif k == "function" and isinstance(v, str):
                # 函数对象内的注册键：26.3 dispatch 键也是 "type"
                nk = "type"
            out[nk] = convert_loot(nv)
        if out.get("type") == "minecraft:match_block":
            if "block" in out:
                out["blocks"] = out.pop("block")
            if "properties" in out:
                out["state"] = out.pop("properties")
        if conditions is not None:
            conv = [convert_loot(x) for x in conditions]
            out["condition"] = conv[0] if len(conv) == 1 else {"type": "minecraft:all_of", "terms": conv}
        return out
    if isinstance(obj, list):
        return [convert_loot(x) for x in obj]
    return obj


def convert_loot_dir(src_dir, out_dir):
    """整目录战利品表转换（1.21.11 旧格式 -> 26.3 新格式）。"""
    os.makedirs(out_dir, exist_ok=True)
    count = 0
    for root, _, files in os.walk(src_dir):
        for name in sorted(files):
            if not name.endswith(".json"):
                continue
            src_path = os.path.join(root, name)
            rel = os.path.relpath(src_path, src_dir)
            dst_path = os.path.join(out_dir, rel)
            os.makedirs(os.path.dirname(dst_path), exist_ok=True)
            with open(src_path, encoding="utf-8") as f:
                data = json.load(f)
            data = convert_loot(data)
            with open(dst_path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
            count += 1
    print(f"26.3 战利品表转换完成：{count} 个 -> {out_dir}")


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.path.join(root, "1.21.11", "fabric"))
    ap.add_argument("--out-root", default=os.path.join(root, "26.3", "fabric"))
    args = ap.parse_args()

    src_dir = os.path.join(args.src, "src", "main", "resources", "data", "compressedblocks",
                           "worldgen", "configured_feature")
    out_dir = os.path.join(args.out_root, "src", "main", "resources", "data", "compressedblocks",
                           "worldgen", "feature")
    os.makedirs(out_dir, exist_ok=True)
    count = 0
    for name in sorted(os.listdir(src_dir)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(src_dir, name), encoding="utf-8") as f:
            data = json.load(f)
        # 26.3 worldgen/feature JSON 顶层平铺：type 保留，config 内容直接上提
        cfg = data.pop("config", {})
        merged = dict(data)
        merged.update(cfg)
        merged = convert_state_provider(merged)
        with open(os.path.join(out_dir, name), "w", encoding="utf-8", newline="\n") as f:
            json.dump(merged, f, ensure_ascii=False, indent=2)
            f.write("\n")
        count += 1
    print(f"26.3 树特征转换完成：{count} 个 -> {out_dir}")

    # 配方解锁进度（advancement/recipes）：26.3 把 recipe_unlocked 条件键 recipe 改成 recipes
    adv_src = os.path.join(args.src, "src", "main", "resources", "data", "compressedblocks",
                           "advancement", "recipes")
    adv_out = os.path.join(args.out_root, "src", "main", "resources", "data", "compressedblocks",
                           "advancement", "recipes")
    if os.path.isdir(adv_src):
        os.makedirs(adv_out, exist_ok=True)
        adv_count = 0
        for name in sorted(os.listdir(adv_src)):
            if not name.endswith(".json"):
                continue
            with open(os.path.join(adv_src, name), encoding="utf-8") as f:
                data = json.load(f)
            data = convert_advancement(data)
            with open(os.path.join(adv_out, name), "w", encoding="utf-8", newline="\n") as f:
                json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
            adv_count += 1
        print(f"26.3 配方进度转换完成：{adv_count} 个 -> {adv_out}")

    # 战利品表格式升级：主 mod 与附属 mod 各自整目录转换
    loot_src = os.path.join(args.src, "src", "main", "resources", "data", "compressedblocks",
                            "loot_table")
    loot_out = os.path.join(args.out_root, "src", "main", "resources", "data", "compressedblocks",
                            "loot_table")
    if os.path.isdir(loot_src):
        convert_loot_dir(loot_src, loot_out)
    addon_src = os.path.join(root, "1.21.11", "addon", "fabric", "src", "main", "resources",
                             "data", "compressedblockspot", "loot_table")
    addon_out = os.path.join(root, "26.3", "addon", "fabric", "src", "main", "resources",
                             "data", "compressedblockspot", "loot_table")
    if os.path.isdir(addon_src):
        convert_loot_dir(addon_src, addon_out)


if __name__ == "__main__":
    main()
