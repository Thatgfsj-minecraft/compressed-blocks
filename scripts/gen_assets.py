#!/usr/bin/env python3
"""压缩方块 mod 贴图生成器。

全部贴图从原版方块/物品 sprite 程序化重绘生成，不复制任何第三方 mod 的像素。
压缩层视觉语言（0.3.0 起）：
- 方块：底图加灰色滤镜，外围压缩环封顶 5 圈（每圈≈5%）：
  1~5 重=黑/浅黑圈逐级累积；6~8 重新增圈改深紫/浅紫（紫黑黑黑黑→紫紫紫黑黑）；
  9 重最外圈改白色细圈（白紫紫黑黑）
- 非整面物品（树苗/作物/食物/种子/盔甲图标）：按同一配色做逐级染色
  （1~5 重渐深、6~8 重偏紫、9 重泛白+白边）
- 树叶：保留生物染色，只叠压缩环不加灰滤镜
用法：python scripts/gen_assets.py [--out-root <repo根，默认仓库根>]
"""
import argparse
import colorsys
import os
import random
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
VANILLA = os.path.join(ROOT, "_asset-src", "vanilla")
SUBPROJECTS = [os.path.join(ROOT, "1.21.11", "fabric"), os.path.join(ROOT, "1.21.11", "neoforge")]

# 1~9 重：id 前缀 / en 前缀 / zh 前缀
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

# 储存方块材料（0.2.0 起就有）。工具不挂材料：只有圆石线+木线两条（见 TOOL_KINDS）
MATERIALS = [
    ("cobblestone", "Cobblestone", "圆石", False),
    ("stone", "Stone", "石头", False),
    ("cobbled_deepslate", "Cobbled Deepslate", "深板岩圆石", False),
    ("deepslate", "Deepslate", "深板岩", False),
    ("oak_log", "Oak Log", "橡木原木", False),
    ("spruce_log", "Spruce Log", "云杉原木", False),
    ("birch_log", "Birch Log", "白桦原木", False),
    ("jungle_log", "Jungle Log", "丛林原木", False),
    ("acacia_log", "Acacia Log", "金合欢原木", False),
    ("dark_oak_log", "Dark Oak Log", "深色橡木原木", False),
    ("mangrove_log", "Mangrove Log", "红树原木", False),
    ("cherry_log", "Cherry Log", "樱花原木", False),
    ("pale_oak_log", "Pale Oak Log", "苍白橡木原木", False),
    ("dirt", "Dirt", "泥土", False),
    ("sand", "Sand", "沙子", False),
    ("gravel", "Gravel", "沙砾", False),
    ("netherrack", "Netherrack", "下界岩", False),
    ("end_stone", "End Stone", "末地石", False),
    ("obsidian", "Obsidian", "黑曜石", False),
    # 原版矿物块（与 gen_resources.MATERIALS 同步）
    ("coal_block", "Block of Coal", "煤炭块", False),
    ("copper_block", "Block of Copper", "铜块", False),
    ("iron_block", "Block of Iron", "铁块", False),
    ("lapis_block", "Lapis Lazuli Block", "青金石块", False),
    ("gold_block", "Block of Gold", "金块", False),
    ("redstone_block", "Block of Redstone", "红石块", False),
    ("emerald_block", "Block of Emerald", "绿宝石块", False),
    ("diamond_block", "Block of Diamond", "钻石块", False),
]

# 0.3.0 新建材 24 种：(key, en, zh, sprite 名, light 等级)
BUILD_MATERIALS = [
    ("granite", "Granite", "花岗岩", "granite", 0),
    ("diorite", "Diorite", "闪长岩", "diorite", 0),
    ("andesite", "Andesite", "安山岩", "andesite", 0),
    ("calcite", "Calcite", "方解石", "calcite", 0),
    ("tuff", "Tuff", "凝灰岩", "tuff", 0),
    ("sandstone", "Sandstone", "砂岩", "sandstone", 0),
    ("red_sandstone", "Red Sandstone", "红砂岩", "red_sandstone", 0),
    ("basalt", "Basalt", "玄武岩", "basalt_side", 0),
    ("blackstone", "Blackstone", "黑石", "blackstone", 0),
    ("dripstone_block", "Dripstone Block", "钟乳石", "dripstone_block", 0),
    ("terracotta", "Terracotta", "陶瓦", "terracotta", 0),
    ("quartz_block", "Quartz Block", "石英块", "quartz_block_side", 0),
    ("purpur_block", "Purpur Block", "紫珀块", "purpur_block", 0),
    ("prismarine", "Prismarine", "海晶石", "prismarine", 0),
    ("amethyst_block", "Amethyst Block", "紫水晶块", "amethyst_block", 0),
    ("glowstone", "Glowstone", "荧石", "glowstone", 15),
    ("clay", "Clay", "黏土块", "clay", 0),
    ("hay_block", "Hay Bale", "干草块", "hay_block_side", 0),
    ("bone_block", "Bone Block", "骨块", "bone_block_side", 0),
    ("moss_block", "Moss Block", "苔藓块", "moss_block", 0),
    ("snow", "Snow Block", "雪块", "snow", 0),
    ("blue_ice", "Blue Ice", "蓝冰", "blue_ice", 0),
    ("mud", "Mud", "泥巴", "mud", 0),
    # 原版无方块形态：sprite 名 "gunpowder" 走物品 sprite 合成（gen_gunpowder_base）
    ("gunpowder", "Block of Gunpowder", "火药块", "gunpowder", 0),
]

# 树：key / en / zh / 树叶 sprite / 树苗 sprite / 生物染色（None=贴图自带色）
WOODS = [
    ("oak", "Oak", "橡木", "oak_leaves", "oak_sapling", (119, 171, 47)),
    ("spruce", "Spruce", "云杉", "spruce_leaves", "spruce_sapling", (97, 153, 97)),
    ("birch", "Birch", "白桦", "birch_leaves", "birch_sapling", (128, 167, 85)),
    ("jungle", "Jungle", "丛林", "jungle_leaves", "jungle_sapling", (119, 171, 47)),
    ("acacia", "Acacia", "金合欢", "acacia_leaves", "acacia_sapling", (119, 171, 47)),
    ("dark_oak", "Dark Oak", "深色橡木", "dark_oak_leaves", "dark_oak_sapling", (119, 171, 47)),
    ("mangrove", "Mangrove", "红树", "mangrove_leaves", "mangrove_propagule", (119, 171, 47)),
    ("cherry", "Cherry", "樱花", "cherry_leaves", "cherry_sapling", None),
    ("pale_oak", "Pale Oak", "苍白橡木", "pale_oak_leaves", "pale_oak_sapling", None),
]

# 作物：key / en / zh / 阶段数 / 产物 sprite / 种子 sprite（None=产物直种）
CROPS = [
    ("wheat", "Wheat", "小麦", 8, "item/wheat", "item/wheat_seeds"),
    ("carrot", "Carrot", "胡萝卜", 4, "item/carrot", None),
    ("potato", "Potato", "马铃薯", 4, "item/potato", None),
    ("beetroot", "Beetroot", "甜菜根", 4, "item/beetroot", "item/beetroot_seeds"),
]
# 阶段贴图命名：小麦 wheat_stageN，其余复数 carrots_/potatoes_/beetroots_stageN
CROP_STAGE_SPRITE = {"wheat": "wheat_stage", "carrot": "carrots_stage",
                     "potato": "potatoes_stage", "beetroot": "beetroots_stage"}

FOODS = [
    ("bread", "Bread", "面包", "item/bread"),
    ("beef", "Raw Beef", "生牛肉", "item/beef"),
    ("cooked_beef", "Steak", "牛排", "item/cooked_beef"),
    ("porkchop", "Raw Porkchop", "生猪排", "item/porkchop"),
    ("cooked_porkchop", "Cooked Porkchop", "熟猪排", "item/cooked_porkchop"),
    ("mutton", "Raw Mutton", "羊肉", "item/mutton"),
    ("cooked_mutton", "Cooked Mutton", "熟羊肉", "item/cooked_mutton"),
    ("chicken", "Raw Chicken", "生鸡肉", "item/chicken"),
    ("cooked_chicken", "Cooked Chicken", "熟鸡肉", "item/cooked_chicken"),
    ("rabbit", "Raw Rabbit", "生兔肉", "item/rabbit"),
    ("cooked_rabbit", "Cooked Rabbit", "熟兔肉", "item/cooked_rabbit"),
    ("cod", "Raw Cod", "生鳕鱼", "item/cod"),
    ("cooked_cod", "Cooked Cod", "熟鳕鱼", "item/cooked_cod"),
    ("salmon", "Raw Salmon", "生鲑鱼", "item/salmon"),
    ("cooked_salmon", "Cooked Salmon", "熟鲑鱼", "item/cooked_salmon"),
    ("melon", "Watermelon", "西瓜", "item/melon_slice"),
    ("rotten_flesh", "Rotten Flesh", "腐肉", "item/rotten_flesh"),
    ("baked_potato", "Baked Potato", "烤土豆", "item/baked_potato"),
]

ARMOR_PIECES = ["helmet", "chestplate", "leggings", "boots"]

TOOL_TYPES = ["pickaxe", "axe", "shovel", "hoe", "sword"]

SIZE = 16
PURPLE_DARK = (85, 32, 140)
PURPLE_LIGHT = (178, 102, 255)
WHITE_RING = (245, 245, 250)


def load_base(name):
    kind, _, file = name.partition("/")
    return Image.open(os.path.join(VANILLA, f"{kind}_{file}.png")).convert("RGBA")


def avg_color(img):
    px = list(img.getdata())
    n = len(px)
    r = sum(p[0] for p in px) / n
    g = sum(p[1] for p in px) / n
    b = sum(p[2] for p in px) / n
    return (r, g, b)


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
    """从外到内的压缩环配色计划。黑黑黑黑黑封顶 5 圈；6~8 重外围换紫；9 重最外圈白。"""
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
    """灰色滤镜：向亮度灰偏移 45% 再压暗 15%，突出压缩环配色。"""
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
    """方块/树叶贴图：灰色滤镜底图 + 封顶 5 圈压缩环（黑圈颜色取自底图均色，紫圈固定配色）。"""
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
    """物品级逐级染色：1~5 重渐深，6~8 重偏紫，9 重泛白+白色剪影描边。"""
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
            if s >= 0.25 or v <= 0.15:  # 非头部像素（把手等）跳过
                continue
            sr, sg, sb, sa = sprite.getpixel((x, y))
            lum = (0.299 * sr + 0.587 * sg + 0.114 * sb) / 160.0
            k = max(0.35, min(1.1, lum))
            out.putpixel((x, y), (min(255, int(mat_color[0] * k)),
                                  min(255, int(mat_color[1] * k)),
                                  min(255, int(mat_color[2] * k)), sa))
    return out


def gen_tool_tex(tool, level, base_kind, mat_color):
    """工具贴图：原版石/木 sprite 头部按遮罩重着色（随重数加深），六重起加浅色高光。"""
    kind = "stone" if base_kind == "stone" else "wooden"
    sprite = load_base(f"item/{kind}_{tool}")
    out = recolor_by_lum(sprite, load_base("item/stone_" + tool), mat_color)
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
    """压缩木棍：木棍 sprite 逐级染色。"""
    return level_tint(load_base("item/stick"), level)


def gen_leaves_tex(sprite, tint, level):
    """树叶：先按生物染色，再叠压缩环（不加灰滤镜，保绿色）。"""
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
    """压缩耕地：泥土底 + 湿耕地沟壑叠加 + 压缩环。"""
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
    """火药块底图：原版无方块形态——火药物品 sprite 主色噪点打底 + 居中 12×12 堆叠。"""
    sprite = load_base("item/gunpowder")
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
    """压缩甘蔗：有核心方块时，核心颜色与甘蔗本来的绿按连续小像素段混排（几个像素连着一段），
    保留甘蔗明暗；无核心 = 纯甘蔗皮。变黑：纯甘蔗每层 5%（9 层=45%），方块甘蔗每层 5%（L1 不变，9 层=40%）。"""
    cane = load_base("block/sugar_cane").convert("RGBA")
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
    base_lum = 0.299 * base[0] + 0.587 * base[1] + 0.114 * base[2]
    if base_lum <= 0:
        base_lum = 1.0

    def lum(p):
        return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]

    ip = cane.load()
    w, h = cane.size
    opaque = [(x, y) for y in range(h) for x in range(w) if ip[x, y][3] > 0]
    mean_lum = (sum(lum(ip[x, y]) for x, y in opaque) / len(opaque)) if opaque else 1.0

    run_id = 0
    for y in range(h):
        x = 0
        while x < w:
            if ip[x, y][3] == 0:
                x += 1
                continue
            seg_start = x
            seg_len = 2 + (run_id % 2)  # 2-3 像素连续一段
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
    """压缩种子图标（用户定稿）：
    1 重 = 原版图标不动 + 左下角偏移副本（看起来两颗）；
    2 重 = 再加一颗（三颗堆叠，阶梯往左下）；
    3 重 = 保持三颗，整体轮廓边缘发白（高亮）。
    """
    out = Image.new("RGBA", (SIZE, SIZE))
    offsets = [(0, 0), (-3, 3)] if level == 1 else [(0, 0), (-3, 3), (-6, 6)]
    for dx, dy in offsets:
        out.paste(sprite, (dx, dy), sprite)
    if level >= 3:
        px = out.load()
        edge = []
        for y in range(SIZE):
            for x in range(SIZE):
                if px[x, y][3] == 0:
                    continue
                for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ddx, y + ddy
                    if not (0 <= nx < SIZE and 0 <= ny < SIZE) or px[nx, ny][3] == 0:
                        edge.append((x, y))
                        break
        for x, y in edge:
            r, g, b, a = px[x, y]
            px[x, y] = (min(255, int(r * 0.35 + 255 * 0.65)),
                        min(255, int(g * 0.35 + 255 * 0.65)),
                        min(255, int(b * 0.35 + 255 * 0.65)), a)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-root", default=ROOT)
    args = ap.parse_args()

    block_tex, item_tex, armor_layer_tex = {}, {}, {}
    for mat in [(k, e, z, t) for k, e, z, t in MATERIALS] + \
               [(k, e, z, False) for k, e, z, _, _ in BUILD_MATERIALS]:
        key, sprite = mat[0], mat[0]
        for k, e, z, sp, _ in BUILD_MATERIALS:
            if k == mat[0]:
                sprite = sp
        base = gen_gunpowder_base() if sprite == "gunpowder" else load_base("block/" + sprite)
        for _, prefix, _, _ in LEVELS:
            block_tex[f"{prefix}_{key}"] = gen_block_tex(base, int(prefix[:-1]))

    # 树叶（带生物染色 + 压缩环）与耕地
    for wood, _, _, leaves_sprite, _, tint in WOODS:
        sprite = load_base("block/" + leaves_sprite)
        for _, prefix, _, _ in LEVELS:
            block_tex[f"{prefix}_{wood}_leaves"] = gen_leaves_tex(sprite, tint, int(prefix[:-1]))
    dirt_img = load_base("block/dirt")
    moist_top = load_base("block/farmland_moist")
    for _, prefix, _, _ in LEVELS:
        block_tex[f"{prefix}_farmland"] = gen_farmland_tex(dirt_img, moist_top, int(prefix[:-1]))

    # 作物阶段（方块 cross 贴图）
    for crop, _, _, stages, _, _ in CROPS:
        for n in range(stages):
            sprite = load_base("block/" + CROP_STAGE_SPRITE[crop] + str(n))
            for _, prefix, _, _ in LEVELS[:3]:
                block_tex[f"{prefix}_{crop}_stage{n}"] = level_tint(sprite, int(prefix[:-1]), rim=False)

    # 工具两条线：压缩圆石工具（石 sprite）+ 压缩木质工具（木 sprite，9 原木混用）
    for key, base_kind, color_src in (("cobblestone", "stone", "cobblestone"), ("wood", "wood", "oak_log")):
        for _, prefix, _, _ in LEVELS:
            level = int(prefix[:-1])
            mat_color = avg_color(block_tex[f"{prefix}_{color_src}"]) + (255,)
            for tool in TOOL_TYPES:
                item_tex[f"{prefix}_{key}_{tool}"] = gen_tool_tex(tool, level, base_kind, mat_color)
    for _, prefix, _, _ in LEVELS:
        item_tex[f"{prefix}_stick"] = gen_stick_tex(int(prefix[:-1]))

    # 树苗 / 作物产物与种子 / 食物：物品级染色
    for wood, _, _, _, sapling_sprite, _ in WOODS:
        sprite = load_base("block/" + sapling_sprite)
        for _, prefix, _, _ in LEVELS:
            tex = level_tint(sprite, int(prefix[:-1]))
            # 树苗是十字方块模型 + 物品定义共用一张图：block/ 与 item/ 都要给
            block_tex[f"{prefix}_{wood}_sapling"] = tex
            item_tex[f"{prefix}_{wood}_sapling"] = tex
    for crop, _, _, _, produce_sprite, seed_sprite in CROPS:
        produce = load_base(produce_sprite)
        for _, prefix, _, _ in LEVELS[:3]:
            item_tex[f"{prefix}_{crop}"] = level_tint(produce, int(prefix[:-1]))
        if seed_sprite:
            seeds = load_base(seed_sprite)
            for _, prefix, _, _ in LEVELS[:3]:
                item_tex[f"{prefix}_{crop}_seeds"] = gen_seeds_tex(seeds, int(prefix[:-1]))
    # 更多压缩种子（原版其余种子 × 3 级）：同样的堆叠图标
    extra_seeds = [("pumpkin_seeds", "item/pumpkin_seeds"), ("melon_seeds", "item/melon_seeds"),
                   ("torchflower_seeds", "item/torchflower_seeds"), ("pitcher_pod", "item/pitcher_pod")]
    for key, sp in extra_seeds:
        seeds = load_base(sp)
        for _, prefix, _, _ in LEVELS[:3]:
            item_tex[f"{prefix}_{key}"] = gen_seeds_tex(seeds, int(prefix[:-1]))
    for food, _, _, sprite in FOODS:
        base = load_base(sprite)
        for _, prefix, _, _ in LEVELS[:3]:
            item_tex[f"{prefix}_{food}"] = level_tint(base, int(prefix[:-1]))

    # 盔甲：图标=铁甲图标重着色；实体层=铁甲层重着色。
    # 1-5 石头色逐级加深；6 黑+蓝条纹；7 黑+橙条纹；8 黑+红条纹；9 穿戴层全透明（显示皮肤）
    stone_avg = avg_color(load_base("block/stone"))
    stone_color = (int(stone_avg[0]), int(stone_avg[1]), int(stone_avg[2]))
    accent_stripes = {6: (56, 92, 214), 7: (232, 122, 20), 8: (208, 32, 32)}

    def recolor_ornate(gray, accent):
        """下界合金风格花纹：暗部近黑（微透accent）、中间调深accent、棱边高光亮accent——
        铁甲自带的棱线/雕纹在暗底上以accent色浮现。"""
        out = gray.copy()
        px = out.load()
        for y in range(out.height):
            for x in range(out.width):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                l = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                if l > 0.85:
                    c = (accent[0] * 1.05, accent[1] * 1.05, accent[2] * 1.05)  # 顶部棱边：亮 accent
                elif l > 0.72:
                    c = (accent[0] * 0.48, accent[1] * 0.48, accent[2] * 0.48)  # 上斜面：中 accent
                elif l > 0.45:
                    c = (r * 0.10 + accent[0] * 0.14 + 5, g * 0.10 + accent[1] * 0.14 + 5,
                         b * 0.10 + accent[2] * 0.14 + 7)                        # 主体：黑炭带accent色相
                else:
                    c = (r * 0.16 + 3, g * 0.16 + 3, b * 0.16 + 5)               # 轮廓/深影：近黑
                px[x, y] = (min(255, int(c[0])), min(255, int(c[1])), min(255, int(c[2])), a)
        return out

    for piece in ARMOR_PIECES:
        icon = load_base("item/iron_" + piece)
        for _, prefix, _, _ in LEVELS:
            level = int(prefix[:-1])
            if level in accent_stripes:
                item_tex[f"{prefix}_stone_{piece}"] = recolor_ornate(icon, accent_stripes[level])
            else:
                item_tex[f"{prefix}_stone_{piece}"] = level_tint(
                    recolor_by_lum(icon, icon, stone_color + (255,)), level)
    # 木质盔甲：木棕底色，花纹档位整体右移一档（7/8/9 重 = 蓝/橙/红花纹），无隐形档
    wood_color = (150, 115, 68)
    wood_stripes = {7: (56, 92, 214), 8: (232, 122, 20), 9: (208, 32, 32)}
    for piece in ARMOR_PIECES:
        icon = load_base("item/iron_" + piece)
        for _, prefix, _, _ in LEVELS:
            level = int(prefix[:-1])
            if level in wood_stripes:
                item_tex[f"{prefix}_wood_{piece}"] = recolor_ornate(icon, wood_stripes[level])
            else:
                item_tex[f"{prefix}_wood_{piece}"] = level_tint(
                    recolor_by_lum(icon, icon, wood_color + (255,)), level)
    for layer, src in (("humanoid", "humanoid/iron"), ("humanoid_leggings", "humanoid_leggings/iron")):
        layer_img = load_base(src)
        for _, prefix, _, _ in LEVELS:
            level = int(prefix[:-1])
            if level in wood_stripes:
                armor_layer_tex[f"{layer}/wood_{prefix}"] = recolor_ornate(layer_img, wood_stripes[level])
            else:
                armor_layer_tex[f"{layer}/wood_{prefix}"] = recolor_full(layer_img, wood_color)
    for layer, src in (("humanoid", "humanoid/iron"), ("humanoid_leggings", "humanoid_leggings/iron")):
        layer_img = load_base(src)
        for _, prefix, _, _ in LEVELS:
            level = int(prefix[:-1])
            if level >= 9:
                armor_layer_tex[f"{layer}/stone_{prefix}"] = Image.new("RGBA", layer_img.size, (0, 0, 0, 0))
            elif level in accent_stripes:
                armor_layer_tex[f"{layer}/stone_{prefix}"] = recolor_ornate(layer_img, accent_stripes[level])
            else:
                armor_layer_tex[f"{layer}/stone_{prefix}"] = recolor_full(layer_img, stone_color)

    # 压缩甘蔗（纯甘蔗 + 51 材料 × 9 级）：甘蔗图案 + 材料颜色按连续像素段混排，逐层变黑 5%
    def mat_base_img(key):
        for k, e, z, sp, _ in BUILD_MATERIALS:
            if k == key:
                return gen_gunpowder_base() if key == "gunpowder" else load_base("block/" + sp)
        return load_base("block/" + key)

    def cane_key(k):
        return k[:-6] if k.endswith("_block") else k

    cane_specs = [("cane", None)] + [(cane_key(k) + "_cane", mat_base_img(k))
                                     for k, e, z, t in MATERIALS] + \
                 [(cane_key(k) + "_cane", mat_base_img(k)) for k, e, z, _, _ in BUILD_MATERIALS]
    for key, sprite in cane_specs:
        for _, prefix, _, _ in LEVELS:
            block_tex[f"{prefix}_{key}"] = gen_cane_tex(sprite, int(prefix[:-1]))

    # 盆栽（陶盆）：底/侧/顶 + 漏斗变体金属带；顶面中间 8×8 深色内腔（土壤由方块实体渲染器画）
    terra = load_base("block/terracotta")
    block_tex["pot_side"] = gen_block_tex(terra, 1)
    pot_top = block_tex["pot_side"].copy()
    pxt = pot_top.load()
    for y in range(4, 12):
        for x in range(4, 12):
            pxt[x, y] = (52, 40, 34, 255)
    block_tex["pot_top"] = pot_top
    block_tex["pot_bottom"] = darken(terra, 0.85)
    hopper_side = block_tex["pot_side"].copy()
    pxh = hopper_side.load()
    for y in range(5, 8):
        for x in range(SIZE):
            pxh[x, y] = (92, 94, 102, 255)
    block_tex["hopper_pot_side"] = hopper_side

    # 459 储存 + 81 树叶 + 81 树苗 + 9 耕地 + 60 作物阶段 + 468 甘蔗 + 4 盆栽 = 1162
    assert len(block_tex) == 1162, len(block_tex)
    print(f"block textures: {len(block_tex)}, item textures: {len(item_tex)}, armor layers: {len(armor_layer_tex)}")
    for sub in SUBPROJECTS:
        rel = os.path.relpath(sub, ROOT)
        if not os.path.exists(os.path.join(args.out_root, rel, "build.gradle")):
            continue
        tdir = os.path.join(args.out_root, rel, "src", "main", "resources",
                            "assets", "compressedblocks", "textures")
        for name, img in block_tex.items():
            p = os.path.join(tdir, "block", name + ".png")
            os.makedirs(os.path.dirname(p), exist_ok=True)
            img.save(p)
        for name, img in item_tex.items():
            p = os.path.join(tdir, "item", name + ".png")
            os.makedirs(os.path.dirname(p), exist_ok=True)
            img.save(p)
        for name, img in armor_layer_tex.items():
            p = os.path.join(tdir, "entity", "equipment", name + ".png")
            os.makedirs(os.path.dirname(p), exist_ok=True)
            img.save(p)
        print(f"wrote textures into {sub}")

    icon = block_tex["9x_cobblestone"].resize((128, 128), Image.NEAREST)
    icon.save(os.path.join(ROOT, "_asset-src", "icon.png"))

    # 预览：储存方块 + 树叶/耕地
    lv = [l[1] for l in LEVELS]
    sheet_keys = [f"{p}_{m[0]}" for m in MATERIALS + [(k, e, z, False) for k, e, z, _, _ in BUILD_MATERIALS]
                  for p in lv]
    extra_keys = [f"{p}_{w[0]}_leaves" for w in WOODS for p in lv] + \
                 [f"{p}_farmland" for p in lv]
    contact_sheet([(k, block_tex[k]) for k in sheet_keys + extra_keys], 18,
                  os.path.join(ROOT, "_asset-src", "preview_blocks.png"))
    imgs = [f"{p}_{k}_{t}" for k in ("cobblestone", "wood") for t in TOOL_TYPES for p in lv]
    contact_sheet([(k, item_tex[k]) for k in imgs], 45,
                  os.path.join(ROOT, "_asset-src", "preview_tools.png"))
    misc = [k for k in item_tex if k.endswith(("_sapling", "_seeds")) or
            any(k == f"{p}_{f[0]}" for p in lv for f in FOODS) or
            any(k == f"{p}_stone_{piece}" for p in lv for piece in ARMOR_PIECES)]
    contact_sheet([(k, item_tex[k]) for k in sorted(misc)], 27,
                  os.path.join(ROOT, "_asset-src", "preview_items.png"))
    print("previews written to _asset-src/preview_{blocks,tools,items}.png")


def recolor_full(img, color):
    """整图按明度×材料色重着色（盔甲实体层）。"""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 160.0
            k = max(0.3, min(1.15, lum))
            px[x, y] = (min(255, int(color[0] * k)), min(255, int(color[1] * k)),
                        min(255, int(color[2] * k)), a)
    return out


def contact_sheet(pairs, cols, out_path, cell=20, scale=4):
    rows = (len(pairs) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell * scale, rows * cell * scale), (40, 40, 40, 255))
    for i, (name, img) in enumerate(pairs):
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        x = (i % cols) * cell * scale + 2 * scale
        y = (i // cols) * cell * scale + 2 * scale
        sheet.paste(big, (x, y))
    sheet.save(out_path)


if __name__ == "__main__":
    main()
