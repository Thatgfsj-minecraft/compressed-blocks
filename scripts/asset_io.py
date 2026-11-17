#!/usr/bin/env python3
"""贴图 I/O 公共件：定位原版 Minecraft jar、按像素幂等地写 PNG。

gen_assets.py / gen_hopper_assets.py / gen_wooden_hopper_assets.py 共用：
前者要读原版 sprite 缓存，后两者直接从未合并的原版 jar 里取漏斗贴图。
"""
import glob
import os

from PIL import Image


def find_minecraft_jar():
    """定位 Loom 缓存里的合并版原版 jar（漏斗贴图源）。

    优先环境变量 MC_JAR；否则在 GRADLE_USER_HOME / ~/.gradle 的 fabric-loom 缓存里找。
    以前生成器里写死了某台机器的绝对路径，换机器或换 worktree 就会读错源/直接崩，
    所以统一改成"按缓存布局查找 + 环境变量覆盖"。
    """
    override = os.environ.get("MC_JAR")
    if override:
        if not os.path.isfile(override):
            raise SystemExit(f"ERROR: MC_JAR 指向的文件不存在：{override}")
        return override
    roots = []
    if os.environ.get("GRADLE_USER_HOME"):
        roots.append(os.environ["GRADLE_USER_HOME"])
    roots.append(os.path.join(os.path.expanduser("~"), ".gradle"))
    pattern = os.path.join("caches", "fabric-loom", "minecraftMaven", "net", "minecraft",
                           "minecraft-merged", "*", "minecraft-merged-1.21.11*.jar")
    hits = []
    for root in roots:
        for path in glob.glob(os.path.join(root, pattern)):
            if path.endswith("-sources.jar") or path.endswith(".backup"):
                continue
            hits.append(path)
    if not hits:
        raise SystemExit(
            "ERROR: 找不到原版 Minecraft jar（漏斗贴图源）。请先跑一次 Gradle 构建让 Loom 下载，"
            "或设置 MC_JAR=/path/to/minecraft-merged-1.21.11-....jar")
    return sorted(hits)[-1]


def save_png(img, path):
    """写出 PNG，但磁盘上已有**像素完全相同**的文件就跳过（幂等）。

    不同 Pillow/zlib 版本压出的 PNG 字节不同（像素一致），无脑重写会让整个
    textures/ 目录在 git status 里炸成数千个空 diff。这里按像素比对，只写真正变化的内容。
    返回是否真的写了盘。
    """
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path):
        try:
            with Image.open(path) as old:
                old_rgba = old.convert("RGBA")
                if old_rgba.size == img.size and old_rgba.tobytes() == img.convert("RGBA").tobytes():
                    return False
        except OSError:
            pass  # 坏文件/旧格式：直接覆盖
    img.save(path)
    return True
