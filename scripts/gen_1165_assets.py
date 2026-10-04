#!/usr/bin/env python3
"""压缩方块 mod 1.16.5 贴图生成器（改造自 gen_assets.py）。

与 1.21.11 版的差异：
- 材料集裁剪为 1.16.5 原版存在的 39 种（剔除 deepslate 系/铜块/1.19+ 原木/1.17+ 建材等 12 种）；
- 树 6 种（剔除红树/樱花/苍白橡树）；
- 更多压缩种子只保留南瓜/西瓜（火把花/瓶子草是 1.20 内容）；
- 盔甲实体层输出到 textures/models/armor/<材质>_layer_<1|2>.png（1.16.5 IArmorMaterial 约定）；
- 不生成滚动容器 GUI png（1.16.5 界面直接 blit 原版 generic_54.png + 自绘滚动条）；
- _asset-src 与输出根全部参数化：源目录只读，预览/图标输出到本 worktree，绝不写主 worktree。

用法：python scripts/gen_1165_assets.py [--src-root <含_asset-src的根>] [--out-root <仓库根>]
"""
import argparse
import colorsys
import os
import random

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_ROOT = os.path.dirname(HERE)
DEFAULT_SRC = r"O:/clawwork/chuansongmen/compressed-blocks"  # 主仓库（只读）：_asset-src 贴图源

LEVELS = [
    (1, "1x", "Compressed", "压缩"),
    (2, "2x", "Double Compressed", "二重压缩"),
    (3, "3x", "Triple Compressed", "三重压缩"),
    (4, "4x", "Quadruple Compressed", "四重压缩"),
    (5, "5x", "Quintuple Compressed", "五重压缩"),
    (6, "6x", "Sextuple Compressed", "六重压缩"),
    (7, "7x", "Septuple Compressed", "七重压缩"),
    (8, "8x", "Octuple Compressed", "八重压缩"),
    (9, "9x", "Nonuple Compressed", "九重压缩"),
]

# 储存材料（1.16.5 可用 39 种：1.21.11 的 51 种剔除 12 种 1.17+ 材料）
# (key, sprite 名, light)
MATERIALS = [
    ("cobblestone", "cobblestone", 0),
    ("stone", "stone", 0),
    ("oak_log", "oak_log", 0),
    ("spruce_log", "spruce_log", 0),
    ("birch_log", "birch_log", 0),
    ("jungle_log", "jungle_log", 0),
    ("acacia_log", "acacia_log", 0),
    ("dark_oak_log", "dark_oak_log", 0),
    ("dirt", "dirt", 0),
    ("sand", "sand", 0),
    ("gravel", "gravel", 0),
    ("netherrack", "netherrack", 0),
    ("end_stone", "end_stone", 0),
    ("obsidian", "obsidian", 0),
    ("coal_block", "coal_block", 0),
    ("iron_block", "iron_block", 0),
    ("lapis_block", "lapis_block", 0),
    ("gold_block", "gold_block", 0),
    ("redstone_block", "redstone_block", 0),
    ("emerald_block", "emerald_block", 0),
    ("diamond_block", "diamond_block", 0),
    ("granite", "granite", 0),
    ("diorite", "diorite", 0),
    ("andesite", "andesite", 0),
    ("sandstone", "sandstone", 0),
    ("red_sandstone", "red_sandstone", 0),
    ("basalt", "basalt_side", 0),
    ("blackstone", "blackstone", 0),
    ("terracotta", "terracotta", 0),
    ("quartz_block", "quartz_block_side", 0),
    ("purpur_block", "purpur_block", 0),
    ("prismarine", "prismarine", 0),
    ("glowstone", "glowstone", 15),
    ("clay", "clay", 0),
    ("hay_block", "hay_block_side", 0),
    ("bone_block", "bone_block_side", 0),
    ("snow", "snow", 0),
    ("blue_ice", "blue_ice", 0),
    # 原版无方块形态：物品 sprite 合成底图
    ("gunpowder", "gunpowder", 0),
]

# 树（1.16.5 原版 6 种）：key / 树叶 sprite / 树苗 sprite / 生物染色
WOODS = [
    ("oak", "oak_leaves", "oak_sapling", (119, 171, 47)),
    ("spruce", "spruce_leaves", "spruce_sapling", (97, 153, 97)),
    ("birch", "birch_leaves", "birch_sapling", (128, 167, 85)),
    ("jungle", "jungle_leaves", "jungle_sapling", (119, 171, 47)),
    ("acacia", "acacia_leaves", "acacia_sapling", (119, 171, 47)),
    ("dark_oak", "dark_oak_leaves", "dark_oak_sapling", (119, 171, 47)),
]

CROPS = [
    ("wheat", 8, "item/wheat", "item/wheat_seeds"),
    ("carrot", 4, "item/carrot", None),
    ("potato", 4, "item/potato", None),
    ("beetroot", 4, "item/beetroot", "item/beetroot_seeds"),
]
CROP_STAGE_SPRITE = {"wheat": "wheat_stage", "carrot": "carrots_stage",
                     "potato": "potatoes_stage", "beetroot": "beetroots_stage"}

FOODS = [
    ("bread", "item/bread"), ("beef", "item/beef"), ("cooked_beef", "item/cooked_beef"),
    ("porkchop", "item/porkchop"), ("cooked_porkchop", "item/cooked_porkchop"),
    ("mutton", "item/mutton"), ("cooked_mutton", "item/cooked_mutton"),
    ("chicken", "item/chicken"), ("cooked_chicken", "item/cooked_chicken"),
    ("rabbit", "item/rabbit"), ("cooked_rabbit", "item/cooked_rabbit"),
    ("cod", "item/cod"), ("cooked_cod", "item/cooked_cod"),
    ("salmon", "item/salmon"), ("cooked_salmon", "item/cooked_salmon"),
    ("melon", "item/melon_slice"), ("rotten_flesh", "item/rotten_flesh"),
    ("baked_potato", "item/baked_potato"),
]

ARMOR_PIECES = ["helmet", "chestplate", "leggings", "boots"]
TOOL_TYPES = ["pickaxe", "axe", "shovel", "hoe", "sword"]

# 0.3.2 压缩金属包 22 种（与 1.21.11 COMPAT_COLORS 一致）
COMPAT_COLORS = {
    "tin": (196, 202, 208), "lead": (105, 110, 130), "zinc": (168, 184, 184),
    "plastic": (228, 228, 232), "silver": (222, 226, 230), "nickel": (158, 158, 162),
    "bronze": (172, 112, 48), "brass": (198, 162, 62), "electrum": (232, 208, 128),
    "invar": (192, 198, 192), "constantan": (198, 128, 88), "steel": (128, 134, 140),
    "manasteel": (88, 188, 168), "uranium": (132, 152, 88), "osmium": (150, 162, 178),
    "signalum": (224, 96, 36), "enderium": (24, 96, 96), "refined_obsidian": (52, 24, 84),
    "refined_glowstone": (206, 186, 110), "lumium": (238, 226, 130),
    "terrasteel": (96, 196, 96), "elementium": (188, 118, 176),
}

SIZE = 16
PURPLE_DARK = (85, 32, 140)
PURPLE_LIGHT = (178, 102, 255)
WHITE_RING = (245, 245, 250)


def load_base(vanilla, name):
    kind, _, file = name.partition("/")
    return Image.open(os.path.join(vanilla, f"{kind}_{file}.png")).convert("RGBA")


def avg_color(img):
    px = list(img.getdata())
    n = len(px)
    return (sum(p[0] for p in px) / n, sum(p[1] for p in px) / n, sum(p[2] for p in px) / n)


def scale_color(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def mix(c, other, t):
    return tuple(max(0, min(255, int(round(c[i] * (1 - t) + other[i] * t)))) for i in range(3))


def darken(img, f):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (int(r * f), int(g * f), int(b * f), a)
    return out


def draw_frame(img, inset, color):
    d = ImageDraw.Draw(img)
    d.rectangle([inset, inset, img.width - 1 - inset, img.height - 1 - inset], outline=color)
    return img


def ring_plan(level):
    """从外到内的压缩环配色：1~5 黑圈累积；6~8 外围换紫；9 重最外圈白。"""
    if level <= 5:
        return ["B"] * level
    if level == 6:
        return ["P", "B", "B", "B", "B"]
    if level == 7:
        return ["P", "P", "B", "B", "B"]
    if level == 8:
        return ["P", "P", "P", "B", "B"]
    return ["W", "P", "P", "B", "B"]


def gray_filter(img):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            px[x, y] = (int((r * 0.55 + lum * 0.45) * 0.85),
                        int((g * 0.55 + lum * 0.45) * 0.85),
                        int((b * 0.55 + lum * 0.45) * 0.85), a)
    return out


def gen_block_tex(base, level, gray=True):
    avg = avg_color(base)
    img = gray_filter(base) if gray else base.copy()
    for i, kind in enumerate(ring_plan(level)):
        if kind == "B":
            color = scale_color(avg, 0.10) if i % 2 == 0 else scale_color(avg, 0.30)
        elif kind == "P":
            color = PURPLE_DARK if i % 2 == 0 else PURPLE_LIGHT
        else:
            color = WHITE_RING
        draw_frame(img, i, color)
    return img


def level_tint(img, level, rim=True):
    out = img.copy()
    px = out.load()
    dark = 1.0 - 0.06 * (min(level, 5) - 1)
    purple = 0.0 if level < 6 else 0.18 * (level - 5)
    white = 0.30 if level >= 9 else 0.0
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            c = (r * dark, g * dark, b * dark)
            if purple:
                c = mix(c, PURPLE_LIGHT, purple)
            if white:
                c = mix(c, (255, 255, 255), white)
            px[x, y] = (int(c[0]), int(c[1]), int(c[2]), a)
    if rim and level >= 9:
        for y in range(out.height):
            for x in range(out.width):
                if px[x, y][3] == 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if not (0 <= nx < out.width and 0 <= ny < out.height) or px[nx, ny][3] == 0:
                        px[x, y] = (240, 240, 255, px[x, y][3])
                        break
    return out


def recolor_by_lum(sprite, mask_sprite, mat_color):
    """按遮罩 sprite 的灰度像素位置，用原像素明度×材料色重着色（工具头部/盔甲通用）。"""
    out = sprite.copy()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = mask_sprite.getpixel((x, y))
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s >= 0.25 or v <= 0.15:
                continue
            sr, sg, sb, sa = sprite.getpixel((x, y))
            lum = (0.299 * sr + 0.587 * sg + 0.114 * sb) / 160.0
            k = max(0.35, min(1.1, lum))
            out.putpixel((x, y), (min(255, int(mat_color[0] * k)),
                                  min(255, int(mat_color[1] * k)),
                                  min(255, int(mat_color[2] * k)), sa))
    return out


def gen_tool_tex(tool, level, base_kind, mat_color):
    kind = "stone" if base_kind == "stone" else "wooden"
    sprite = load_base(VANILLA, f"item/{kind}_{tool}")
    out = recolor_by_lum(sprite, load_base(VANILLA, "item/stone_" + tool), mat_color)
    if level >= 6:
        mask = [(x, y) for y in range(SIZE) for x in range(SIZE)
                if sprite.getpixel((x, y))[3] != 0
                and _is_head_pixel(sprite.getpixel((x, y)))]
        if mask:
            s_min = min(x + y for x, y in mask)
            for x, y in mask:
                if x + y - s_min <= 1:
                    out.putpixel((x, y), (235, 240, 255, 255))
    return out


def _is_head_pixel(rgba):
    r, g, b, a = rgba
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return s < 0.25 and v > 0.15


def gen_stick_tex(level):
    return level_tint(load_base(VANILLA, "item/stick"), level)


def gen_leaves_tex(sprite, tint, level):
    img = sprite.copy()
    if tint:
        px = img.load()
        for y in range(SIZE):
            for x in range(SIZE):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                px[x, y] = (int(r * tint[0] / 255), int(g * tint[1] / 255), int(b * tint[2] / 255), a)
    return gen_block_tex(img, level, gray=False)


def gen_farmland_tex(dirt, moist_top, level):
    base = dirt.copy()
    px = base.load()
    for y in range(SIZE):
        for x in range(SIZE):
            tr, tg, tb, ta = moist_top.getpixel((x, y))
            if ta == 0:
                continue
            dr, dg, db, _ = px[x, y]
            t = ta / 255.0
            px[x, y] = (int(dr * (1 - t) + tr * t), int(dg * (1 - t) + tg * t),
                        int(db * (1 - t) + tb * t), 255)
    return gen_block_tex(base, level)


def gen_gunpowder_base():
    sprite = load_base(VANILLA, "item/gunpowder")
    avg = avg_color(sprite)
    base = Image.new("RGBA", (SIZE, SIZE))
    px = base.load()
    rng = random.Random(7)
    for y in range(SIZE):
        for x in range(SIZE):
            f = 0.72 + rng.random() * 0.26
            px[x, y] = (int(avg[0] * f), int(avg[1] * f), int(avg[2] * f), 255)
    big = sprite.resize((12, 12), Image.NEAREST)
    base.paste(big, (2, 2), big)
    return base


def gen_cane_tex(center_img, level):
    """压缩甘蔗：甘蔗皮与核心颜色按连续小像素段混排，逐层变黑 5%。"""
    cane = load_base(VANILLA, "block/sugar_cane").convert("RGBA")
    if center_img is None:
        f = 1.0 - 0.05 * level
        ip = cane.load()
        for y in range(cane.height):
            for x in range(cane.width):
                r, g, b, a = ip[x, y]
                if a:
                    ip[x, y] = (int(r * f), int(g * f), int(b * f), a)
        return cane
    ref = center_img.convert("RGBA")
    base = avg_color(ref)
    ip = cane.load()
    w, h = cane.size
    opaque = [(x, y) for y in range(h) for x in range(w) if ip[x, y][3] > 0]

    def lum(p):
        return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]

    mean_lum = (sum(lum(ip[x, y]) for x, y in opaque) / len(opaque)) if opaque else 1.0
    if mean_lum <= 0:
        mean_lum = 1.0
    run_id = 0
    for y in range(h):
        x = 0
        while x < w:
            if ip[x, y][3] == 0:
                x += 1
                continue
            seg_start = x
            seg_len = 2 + (run_id % 2)
            while x < w and ip[x, y][3] != 0 and x - seg_start < seg_len:
                x += 1
            use_block = run_id % 2 == 1
            run_id += 1
            if not use_block:
                continue
            for xx in range(seg_start, x):
                r, g, b, a = ip[xx, y]
                f = lum((r, g, b)) / mean_lum
                ip[xx, y] = (min(255, int(base[0] * f)), min(255, int(base[1] * f)),
                             min(255, int(base[2] * f)), a)
    if level > 1:
        f = 1.0 - 0.05 * (level - 1)
        for y in range(h):
            for x in range(w):
                r, g, b, a = ip[x, y]
                if a:
                    ip[x, y] = (int(r * f), int(g * f), int(b * f), a)
    return cane


def gen_seeds_tex(sprite, level):
    out = Image.new("RGBA", (SIZE, SIZE))
    offsets = [(0, 0), (-3, 3)] if level == 1 else [(0, 0), (-3, 3), (-6, 6)]
    for dx, dy in offsets:
        out.paste(sprite, (dx, dy), sprite)
    if level >= 3:
        px = out.load()
        for y in range(SIZE):
            for x in range(SIZE):
                if px[x, y][3] == 0:
                    continue
                for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ddx, y + ddy
                    if not (0 <= nx < SIZE and 0 <= ny < SIZE) or px[nx, ny][3] == 0:
                        r, g, b, a = px[x, y]
                        px[x, y] = (min(255, int(r * 0.35 + 255 * 0.65)),
                                    min(255, int(g * 0.35 + 255 * 0.65)),
                                    min(255, int(b * 0.35 + 255 * 0.65)), a)
                        break
    return out


def gen_metal_tex(base_rgb, level, seed):
    rng = random.Random(seed)
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    r, g, b = base_rgb
    for y in range(SIZE):
        row = 0.90 + rng.random() * 0.20
        for x in range(SIZE):
            f = row * (0.94 + rng.random() * 0.12)
            px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), 255)
    return gen_block_tex(img, level, gray=False)


def recolor_full(img, color):
    """整图按明度×材料色重着色（盔甲实体层）。"""
    out = img.copy()
    px = out.load()
    h = out.height
    for y in range(h):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            k = 0.55 + lum * 0.9
            if y > 0 and px[x, y - 1][3] == 0:
                k += 0.18
            if y < h - 1 and px[x, y + 1][3] == 0:
                k -= 0.22
            k += ((x * 7 + y * 13) % 5 - 2) * 0.016
            k = max(0.25, min(1.45, k))
            px[x, y] = (min(255, int(color[0] * k)), min(255, int(color[1] * k)),
                        min(255, int(color[2] * k)), a)
    return out


def recolor_ornate(gray, accent):
    """下界合金风格花纹（6/7/8 重条纹盔甲）。"""
    out = gray.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            l = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            if l > 0.85:
                c = (accent[0] * 1.05, accent[1] * 1.05, accent[2] * 1.05)
            elif l > 0.72:
                c = (accent[0] * 0.48, accent[1] * 0.48, accent[2] * 0.48)
            elif l > 0.45:
                c = (r * 0.10 + accent[0] * 0.14 + 5, g * 0.10 + accent[1] * 0.14 + 5,
                     b * 0.10 + accent[2] * 0.14 + 7)
            else:
                c = (r * 0.16 + 3, g * 0.16 + 3, b * 0.16 + 5)
            px[x, y] = (min(255, int(c[0])), min(255, int(c[1])), min(255, int(c[2])), a)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src-root", default=DEFAULT_SRC, help="含 _asset-src 的目录（只读）")
    ap.add_argument("--out-root", default=DEFAULT_ROOT, help="输出仓库根")
    args = ap.parse_args()
    global VANILLA
    VANILLA = os.path.join(args.src_root, "_asset-src", "vanilla")
    out_res = os.path.join(args.out_root, "1.16.5", "forge", "src", "main",
                           "resources", "assets", "compressedblocks")
    tdir = os.path.join(out_res, "textures")

    block_tex, item_tex, armor_layer_tex = {}, {}, {}

    # 1. 储存方块 39 材料 × 9
    for key, sprite, _ in MATERIALS:
        base = gen_gunpowder_base() if sprite == "gunpowder" else load_base(VANILLA, "block/" + sprite)
        for _, prefix, _, _ in LEVELS:
            block_tex[f"{prefix}_{key}"] = gen_block_tex(base, int(prefix[:-1]))

    # 2. 树叶 6 木 × 9
    for wood, leaves_sprite, _, tint in WOODS:
        sprite = load_base(VANILLA, "block/" + leaves_sprite)
        for _, prefix, _, _ in LEVELS:
            block_tex[f"{prefix}_{wood}_leaves"] = gen_leaves_tex(sprite, tint, int(prefix[:-1]))

    # 3. 耕地 9
    dirt_img = load_base(VANILLA, "block/dirt")
    moist_top = load_base(VANILLA, "block/farmland_moist")
    for _, prefix, _, _ in LEVELS:
        block_tex[f"{prefix}_farmland"] = gen_farmland_tex(dirt_img, moist_top, int(prefix[:-1]))

    # 4. 作物阶段（方块 cross 贴图，3 级封顶）
    for crop, stages, _, _ in CROPS:
        for n in range(stages):
            sprite = load_base(VANILLA, "block/" + CROP_STAGE_SPRITE[crop] + str(n))
            for _, prefix, _, _ in LEVELS[:3]:
                block_tex[f"{prefix}_{crop}_stage{n}"] = level_tint(sprite, int(prefix[:-1]), rim=False)

    # 5. 工具（圆石线石 sprite + 木线木 sprite）与木棍
    for key, base_kind, color_src in (("cobblestone", "stone", "cobblestone"),
                                      ("wood", "wood", "oak_log")):
        for _, prefix, _, _ in LEVELS:
            level = int(prefix[:-1])
            mat_color = avg_color(block_tex[f"{prefix}_{color_src}"]) + (255,)
            for tool in TOOL_TYPES:
                item_tex[f"{prefix}_{key}_{tool}"] = gen_tool_tex(tool, level, base_kind, mat_color)
    for _, prefix, _, _ in LEVELS:
        item_tex[f"{prefix}_stick"] = gen_stick_tex(int(prefix[:-1]))

    # 6. 树苗（block/ 与 item/ 共用一张）
    for wood, _, sapling_sprite, _ in WOODS:
        sprite = load_base(VANILLA, "block/" + sapling_sprite)
        for _, prefix, _, _ in LEVELS:
            tex = level_tint(sprite, int(prefix[:-1]))
            block_tex[f"{prefix}_{wood}_sapling"] = tex
            item_tex[f"{prefix}_{wood}_sapling"] = tex

    # 7. 作物产物/种子 + 更多压缩种子（1.16.5 只有南瓜/西瓜）
    for crop, stages, produce_sprite, seed_sprite in CROPS:
        produce = load_base(VANILLA, produce_sprite)
        for _, prefix, _, _ in LEVELS[:3]:
            item_tex[f"{prefix}_{crop}"] = level_tint(produce, int(prefix[:-1]))
        if seed_sprite:
            seeds = load_base(VANILLA, seed_sprite)
            for _, prefix, _, _ in LEVELS[:3]:
                item_tex[f"{prefix}_{crop}_seeds"] = gen_seeds_tex(seeds, int(prefix[:-1]))
    for key in ("pumpkin_seeds", "melon_seeds"):
        seeds = load_base(VANILLA, "item/" + key)
        for _, prefix, _, _ in LEVELS[:3]:
            item_tex[f"{prefix}_{key}"] = gen_seeds_tex(seeds, int(prefix[:-1]))

    # 8. 压缩食物 18 × 3
    for food, sprite in FOODS:
        base = load_base(VANILLA, sprite)
        for _, prefix, _, _ in LEVELS[:3]:
            item_tex[f"{prefix}_{food}"] = level_tint(base, int(prefix[:-1]))

    # 9. 盔甲图标与实体层
    stone_avg = avg_color(load_base(VANILLA, "block/stone"))
    stone_color = (int(stone_avg[0]), int(stone_avg[1]), int(stone_avg[2]))
    accent_stripes = {6: (56, 92, 214), 7: (232, 122, 20), 8: (208, 32, 32)}
    wood_color = (150, 115, 68)
    wood_stripes = {7: (56, 92, 214), 8: (232, 122, 20), 9: (208, 32, 32)}
    for piece in ARMOR_PIECES:
        icon = load_base(VANILLA, "item/iron_" + piece)
        for _, prefix, _, _ in LEVELS:
            level = int(prefix[:-1])
            if level in accent_stripes:
                item_tex[f"{prefix}_stone_{piece}"] = recolor_ornate(icon, accent_stripes[level])
            else:
                item_tex[f"{prefix}_stone_{piece}"] = level_tint(
                    recolor_by_lum(icon, icon, stone_color + (255,)), level)
            if level in wood_stripes:
                item_tex[f"{prefix}_wood_{piece}"] = recolor_ornate(icon, wood_stripes[level])
            else:
                item_tex[f"{prefix}_wood_{piece}"] = level_tint(
                    recolor_by_lum(icon, icon, wood_color + (255,)), level)
    for layer_src, suffix in (("humanoid/iron", "layer_1"), ("humanoid_leggings/iron", "layer_2")):
        layer_img = load_base(VANILLA, layer_src)
        for _, prefix, _, _ in LEVELS:
            level = int(prefix[:-1])
            if level >= 9:
                # 九重石甲穿戴层全透明（显示皮肤）
                armor_layer_tex[f"stone_{prefix}_{suffix}"] = Image.new(
                    "RGBA", layer_img.size, (0, 0, 0, 0))
            elif level in accent_stripes:
                armor_layer_tex[f"stone_{prefix}_{suffix}"] = recolor_ornate(
                    layer_img, accent_stripes[level])
            else:
                armor_layer_tex[f"stone_{prefix}_{suffix}"] = recolor_full(layer_img, stone_color)
            if level in wood_stripes:
                armor_layer_tex[f"wood_{prefix}_{suffix}"] = recolor_ornate(
                    layer_img, wood_stripes[level])
            else:
                armor_layer_tex[f"wood_{prefix}_{suffix}"] = recolor_full(layer_img, wood_color)

    # 10. 压缩甘蔗（纯甘蔗 + 39 材料线 × 9）
    def mat_base_img(key):
        for k, sp, _ in MATERIALS:
            if k == key:
                return gen_gunpowder_base() if key == "gunpowder" else load_base(VANILLA, "block/" + sp)
        return load_base(VANILLA, "block/" + key)

    def cane_key(k):
        return k[:-6] if k.endswith("_block") else k

    cane_specs = [("cane", None)] + [(cane_key(k) + "_cane", mat_base_img(k))
                                     for k, _, _ in MATERIALS]
    for key, sprite in cane_specs:
        for _, prefix, _, _ in LEVELS:
            block_tex[f"{prefix}_{key}"] = gen_cane_tex(sprite, int(prefix[:-1]))

    # 11. 盆栽（陶盆分层贴图）
    terra = load_base(VANILLA, "block/terracotta")
    pot_side = gen_block_tex(terra, 1).copy()
    pxe = pot_side.load()
    for y in range(1, 4):
        for x in range(16):
            r, g, b, a = pxe[x, y]
            pxe[x, y] = (int(r * 0.88), int(g * 0.88), int(b * 0.88), a)
    for y in range(4, 6):
        for x in range(16):
            r, g, b, a = pxe[x, y]
            pxe[x, y] = (min(255, int(r * 1.12)), min(255, int(g * 1.12)),
                         min(255, int(b * 1.12)), a)
    block_tex["pot_side"] = pot_side
    pot_top = gen_block_tex(terra, 1).copy()
    pxt = pot_top.load()
    for y in range(3, 13):
        for x in range(3, 13):
            pxt[x, y] = (52, 40, 34, 255)
    block_tex["pot_top"] = pot_top
    block_tex["pot_bottom"] = darken(terra, 0.85)
    iron_pot = load_base(VANILLA, "block/iron_block")
    hop = pot_side.copy()
    hop.paste(iron_pot.crop((0, 0, 16, 4)), (0, 0))
    pxh = hop.load()
    for x in range(SIZE):
        pxh[x, 0] = (10, 10, 10, 255)
    block_tex["pot_hopper_side"] = hop
    block_tex["pot_hopper_bottom"] = gen_block_tex(iron_pot.copy(), 1, gray=False)

    # 12. 刷石机 3 档（侧/顶/底）
    cobble = load_base(VANILLA, "block/cobblestone")
    red = load_base(VANILLA, "block/redstone_block")
    red_avg = avg_color(red)
    iron = load_base(VANILLA, "block/iron_block")
    wood = load_base(VANILLA, "block/oak_planks")
    for tier in (1, 2, 3):
        prefix = ['', '2x_', '3x_'][tier - 1]
        img = cobble.copy()
        pxi = img.load()
        for x, y in ((2, 3), (3, 2), (12, 4), (13, 3), (4, 12), (3, 13), (11, 13), (12, 12),
                     (7, 5), (8, 5), (7, 10), (8, 10), (5, 7), (5, 8), (10, 7), (10, 8)):
            pxi[x, y] = (int(red_avg[0]), int(red_avg[1]), int(red_avg[2]), 255)
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 1), (8, 14), (1, 7), (14, 8)):
            iron_px = iron.getpixel((x % 16, y % 16))
            pxi[x, y] = (iron_px[0], iron_px[1], iron_px[2], 255)
        img.paste(wood.crop((0, 0, 16, 5)), (0, 0))
        img.paste(iron.crop((0, 0, 16, 5)), (0, 11))
        if tier >= 2:
            for x in range(SIZE):
                pxi[x, 0] = (10, 10, 10, 255)
                pxi[x, 15] = (10, 10, 10, 255)
        block_tex[f"{prefix}cobblestone_generator"] = gen_block_tex(img, tier, gray=False)
        block_tex[f"{prefix}cobblestone_generator_top"] = gen_block_tex(wood.copy(), tier, gray=False)
        block_tex[f"{prefix}cobblestone_generator_bottom"] = gen_block_tex(iron.copy(), tier, gray=False)

    # 13. 压缩金属包 22 × 9
    for key, base_rgb in COMPAT_COLORS.items():
        for _, prefix, _, _ in LEVELS:
            block_tex[f"{prefix}_{key}_block"] = gen_metal_tex(
                base_rgb, int(prefix[:-1]), seed=hash(key) & 0xFFFF)

    # 14. 压缩箱子/潜影盒（原版实体贴图裁面合成）
    chest_src = Image.open(os.path.join(VANILLA, "entity", "chest", "normal.png")).convert("RGBA")
    shulker_src = Image.open(os.path.join(VANILLA, "entity", "shulker", "shulker.png")).convert("RGBA")

    def stack16(top_img, bottom_img):
        out = Image.new("RGBA", (SIZE, SIZE))
        out.paste(top_img, (1, 0))
        out.paste(bottom_img, (1, top_img.height))
        px = out.load()
        done = top_img.height + bottom_img.height
        for y in range(SIZE):
            px[0, y] = px[1, y]
            px[15, y] = px[14, y]
            if y >= done:
                for x in range(SIZE):
                    px[x, y] = px[x, done - 1]
        return out

    def flat16(img):
        out = Image.new("RGBA", (SIZE, SIZE))
        ox = (SIZE - img.width) // 2
        oy = (SIZE - img.height) // 2
        out.paste(img, (ox, oy))
        px = out.load()
        for y in range(SIZE):
            for x in range(SIZE):
                px[x, y] = img.getpixel((min(max(x - ox, 0), img.width - 1),
                                         min(max(y - oy, 0), img.height - 1)))
        return out

    def black_edge(img):
        out = img.copy()
        px = out.load()
        for i in range(SIZE):
            px[i, 0] = (10, 10, 10, 255)
            px[i, 15] = (10, 10, 10, 255)
            px[0, i] = (10, 10, 10, 255)
            px[15, i] = (10, 10, 10, 255)
        return out

    lid_top = chest_src.crop((28, 0, 42, 14))
    lid_front = chest_src.crop((14, 14, 28, 19))
    box_front = chest_src.crop((14, 33, 28, 43))
    box_side = chest_src.crop((28, 33, 42, 43))
    box_inside = chest_src.crop((14, 19, 28, 33))
    front = stack16(lid_front, box_front)
    front.paste(chest_src.crop((1, 1, 3, 5)), (7, 1))
    block_tex["compressed_chest_front"] = black_edge(front)
    block_tex["compressed_chest_side"] = black_edge(stack16(lid_front, box_side))
    block_tex["compressed_chest_top"] = black_edge(flat16(lid_top))
    block_tex["compressed_chest_bottom"] = black_edge(darken(flat16(box_inside), 0.8))

    shell_top = shulker_src.crop((16, 0, 30, 14))
    shell_side = shulker_src.crop((14, 16, 28, 24))
    spiral = shulker_src.crop((33, 29, 41, 37))
    block_tex["compressed_shulker_box_side"] = black_edge(stack16(shell_side, darken(shell_side, 0.85)))
    block_tex["compressed_shulker_box_top"] = black_edge(flat16(shell_top))
    block_tex["compressed_shulker_box_bottom"] = black_edge(flat16(spiral))

    # 压缩箱子实体贴图（BER 开盖用）
    chest_entity = chest_src.copy()
    cpx = chest_entity.load()
    for y in range(chest_entity.height):
        for x in range(chest_entity.width):
            r, g, b, a = cpx[x, y]
            if a:
                cpx[x, y] = (int(r * 0.88), int(g * 0.88), int(b * 0.88), a)
    for seam_y in (18, 33):
        for x in range(0, 56):
            cpx[x, seam_y] = (10, 10, 10, 255)
    entity_tex = {"chest/compressed_chest": chest_entity}

    # 数量自校验
    assert len(block_tex) == 351 + 54 + 54 + 9 + 60 + 360 + 5 + 9 + 198 + 4 + 3, len(block_tex)
    assert len(item_tex) == 90 + 9 + 54 + 6 + 12 + 6 + 54 + 72, len(item_tex)
    assert len(armor_layer_tex) == 36, len(armor_layer_tex)
    print(f"block={len(block_tex)} item={len(item_tex)} armor_layers={len(armor_layer_tex)}")

    # 输出
    for name, img in block_tex.items():
        p = os.path.join(tdir, "block", name + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
    for name, img in item_tex.items():
        p = os.path.join(tdir, "item", name + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
    for name, img in entity_tex.items():
        p = os.path.join(tdir, "entity", name + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
    # 1.16.5 盔甲实体层约定路径：textures/models/armor/<材质>_layer_<1|2>.png
    for name, img in armor_layer_tex.items():
        p = os.path.join(tdir, "models", "armor", name + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
    # mod 图标（9x 压缩圆石放大）
    icon = block_tex["9x_cobblestone"].resize((128, 128), Image.NEAREST)
    icon.save(os.path.join(out_res, "icon.png"))
    print(f"wrote textures into {tdir}")


if __name__ == "__main__":
    main()
