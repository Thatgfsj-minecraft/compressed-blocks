#!/usr/bin/env python3
"""26.3 数据包资源适配生成器。

1.21.11 -> 26.3 的树特征数据迁移（2026 年份版本线的资源格式漂移）：
- 目录改名：data/<ns>/worldgen/configured_feature/ 并入 data/<ns>/worldgen/feature/
  （26.3 里 configured feature 注册表与 feature 注册表合并，Registries.CONFIGURED_FEATURE 消失，
   TreeGrower 直接引用 ResourceKey<Feature>）
- 方块状态提供器字段改名：{"Name": ..., "Properties": {...}} -> {"id": ..., "properties": {...}}
  （对齐 26.3 原版 data/minecraft/worldgen/feature/*.json 的写法）
- 树特征配置键改名：dirt_provider -> below_trunk_provider（26.3 原版键名）

用法：python scripts/gen_assets_26.py [--src <1.21.11源项目>] [--out-root <26.3输出项目>]
从 1.21.11/fabric 的 configured_feature 读取，全部转换后写入 26.3/fabric 的 feature 目录。
"""
import argparse
import json
import os


def convert_state_provider(obj):
    """递归把 Nbt 风格方块状态（Name/Properties）改成 26.3 的 id/properties 风格。"""
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            nk = k
            if k == "Name" and isinstance(v, str):
                nk, v = "id", v
            elif k == "Properties" and isinstance(v, dict):
                nk = "properties"
            elif k == "dirt_provider":
                # 26.3 树配置里改名为 below_trunk_provider
                nk = "below_trunk_provider"
            out[nk] = convert_state_provider(v)
        return out
    if isinstance(obj, list):
        return [convert_state_provider(x) for x in obj]
    return obj


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
        data = convert_state_provider(data)
        with open(os.path.join(out_dir, name), "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        count += 1
    print(f"26.3 树特征转换完成：{count} 个 -> {out_dir}")


if __name__ == "__main__":
    main()
