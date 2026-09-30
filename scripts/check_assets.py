# -*- coding: utf-8 -*-
"""资产完整性校验：模型/方块状态/物品定义/配方里引用的每个资源都必须真实存在。
用法：python scripts/check_assets.py    （目标版本写死常量，防路径注入）
退出码非 0 = 有缺失。
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET = "1.21.11"
NS = "compressedblocks"
MISSING = []


def jload(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def rel_set(base):
    """目录下全部文件的相对 posix 路径集合（后续只做成员判断，不拼不可信路径）。"""
    out = set()
    for dirpath, _, files in os.walk(base):
        for f in files:
            out.add(os.path.relpath(os.path.join(dirpath, f), base).replace(os.sep, "/"))
    return out


def model_refs_of(res_models, avail, ref, seen=None, depth=0):
    """一个模型（含 compressedblocks 父链）引用的全部贴图 id；模型本身必须在 avail 里。"""
    if depth > 8:
        return []
    seen = seen or set()
    if ref in seen:
        return []
    seen.add(ref)
    ns, _, rest = ref.partition(":")
    if ns != NS or not re.fullmatch(r"[a-z0-9_./-]+", rest) or ".." in rest:
        return []
    if rest + ".json" not in avail:
        MISSING.append(f"model missing: {ref} (referenced)")
        return []
    m = jload(os.path.join(res_models, *rest.split("/")) + ".json")
    out = []
    for v in m.get("textures", {}).values():
        if isinstance(v, str) and not v.startswith("#"):
            out.append(v)
    parent = m.get("parent")
    if isinstance(parent, str):
        out += model_refs_of(res_models, avail, parent, seen, depth + 1)
    return out


def check():
    for loader in ("fabric", "neoforge"):
        res = os.path.join(ROOT, TARGET, loader, "src", "main", "resources", "assets", NS)
        tex = rel_set(os.path.join(res, "textures"))
        models = rel_set(os.path.join(res, "models"))

        # 1) 方块状态 → 模型存在
        bs_dir = os.path.join(res, "blockstates")
        n_bs = 0
        stack_all = [jload(os.path.join(bs_dir, f)) for f in os.listdir(bs_dir)]
        n_bs = len(stack_all)
        while stack_all:
            o = stack_all.pop()
            if isinstance(o, dict):
                ref = o.get("model")
                if isinstance(ref, str):
                    ns, _, rest = ref.partition(":")
                    if ns == NS and rest + ".json" not in models:
                        MISSING.append(f"blockstate -> missing model {ref}")
                stack_all.extend(v for v in o.values() if isinstance(v, (dict, list)))
            elif isinstance(o, list):
                stack_all.extend(v for v in o if isinstance(v, (dict, list)))

        # 2) 物品定义 → 模型存在
        idef_dir = os.path.join(res, "items")
        n_items = 0
        stack_all = []
        for f in os.listdir(idef_dir):
            n_items += 1
            stack_all.append(jload(os.path.join(idef_dir, f)))
        while stack_all:
            o = stack_all.pop()
            if isinstance(o, dict):
                ref = o.get("model")
                if isinstance(ref, str) and "/" in ref and not ref.startswith("#"):
                    ns, _, rest = ref.partition(":")
                    if ns == NS and rest + ".json" not in models:
                        MISSING.append(f"item def -> missing model {ref}")
                stack_all.extend(v for v in o.values() if isinstance(v, (dict, list)))
            elif isinstance(o, list):
                stack_all.extend(v for v in o if isinstance(v, (dict, list)))

        # 3) 模型 → 贴图文件存在
        n_models = 0
        for rel in sorted(models):
            if not rel.endswith(".json"):
                continue
            n_models += 1
            ref = f"{NS}:{rel[:-5]}"
            for t in model_refs_of(os.path.join(res, "models"), models, ref):
                if not isinstance(t, str) or t.startswith("#"):
                    continue
                ns, _, rest = t.partition(":")
                if ns != NS:
                    continue
                if not re.fullmatch(r"[a-z0-9_./-]+", rest) or ".." in rest:
                    MISSING.append(f"suspicious texture ref: {t}")
                elif rest + ".png" not in tex:
                    MISSING.append(f"model {ref} -> missing texture {t}")

        # 4) 配方：1.21.2+ 已废除 {"tag":...}/{"item":...} 对象材料；#tag 必须有标签文件
        data = os.path.join(ROOT, TARGET, loader, "src", "main", "resources", "data")
        tags = rel_set(data)
        rdir = os.path.join(data, NS, "recipe")
        adv_dir = os.path.join(data, NS, "advancement", "recipes")
        for f in os.listdir(rdir):
            if not os.path.exists(os.path.join(adv_dir, f)):
                MISSING.append(f"recipe {f[:-5]} has no unlock advancement")
        stack_all = [jload(os.path.join(rdir, f)) for f in os.listdir(rdir)]
        while stack_all:
            o = stack_all.pop()
            if isinstance(o, dict):
                if "tag" in o or "item" in o:
                    MISSING.append(f"recipe uses legacy object ingredient: {o}")
                stack_all.extend(v for v in o.values() if isinstance(v, (dict, list, str)))
            elif isinstance(o, list):
                stack_all.extend(v for v in o if isinstance(v, (dict, list, str)))
            elif isinstance(o, str) and o.startswith("#"):
                tns, _, tpath = o[1:].partition(":")
                if not re.fullmatch(r"[a-z0-9_.-]+", tns) or not re.fullmatch(r"[a-z0-9_./-]+", tpath):
                    MISSING.append(f"recipe -> suspicious tag ref {o}")
                else:
                    kind = "item" if f"{tns}/tags/item/{tpath}.json" in tags else "block"
                    if f"{tns}/tags/{kind}/{tpath}.json" not in tags:
                        MISSING.append(f"recipe -> missing tag {o[1:]}")

        # 5) 反向：孤儿贴图只是噪音，不算失败，打印数量（按文件名是否出现在模型路径中近似）
        joined = " ".join(models)
        orphans = sum(1 for t in tex if t.split("/")[-1][: -4] not in joined)
        print(f"[{loader}] blockstates={n_bs} itemdefs={n_items} models={n_models} orphan_textures={orphans}")


if __name__ == "__main__":
    check()
    if MISSING:
        print(f"MISSING {len(MISSING)}:")
        for m in MISSING[:60]:
            print("  " + m)
        raise SystemExit(1)
    print("ASSETS OK — 所有引用完整")
