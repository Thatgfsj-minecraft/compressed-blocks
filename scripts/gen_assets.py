#!/usr/bin/env python3
"""压缩方块 mod 贴图生成器。

全部贴图从原版方块/工具 sprite 程序化重绘生成，不复制任何第三方 mod 的像素：
- 方块：原版贴图做底，按重数加深 + 嵌套方框环（"层层压缩"的视觉语言）
- 工具：原版石/木工具 sprite，把"头部"像素重新着色为对应材料色（随重数加深），把手不动

用法：python scripts/gen_assets.py [--out-root <repo根，默认仓库根>]
输出：各子项目 src/main/resources/assets/compressedblocks/textures/{block,item}/*.png
     以及 _asset-src/preview_blocks.png / preview_tools.png 预览拼图。
"""
import argparse
import colorsys
import os
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

# key: (vanilla 贴图名, en 名, zh 名, 是否可做工具, 工具基材 stone|wood)
MATERIALS = [
    ("cobblestone", "Cobblestone", "圆石", True, "stone"),
    ("stone", "Stone", "石头", True, "stone"),
    ("cobbled_deepslate", "Cobbled Deepslate", "深板岩圆石", True, "stone"),
    ("deepslate", "Deepslate", "深板岩", True, "stone"),
    ("oak_log", "Oak Log", "橡木原木", True, "wood"),
    ("spruce_log", "Spruce Log", "云杉原木", True, "wood"),
    ("birch_log", "Birch Log", "白桦原木", True, "wood"),
    ("jungle_log", "Jungle Log", "丛林原木", True, "wood"),
    ("acacia_log", "Acacia Log", "金合欢原木", True, "wood"),
    ("dark_oak_log", "Dark Oak Log", "深色橡木原木", True, "wood"),
    ("mangrove_log", "Mangrove Log", "红树原木", True, "wood"),
    ("cherry_log", "Cherry Log", "樱花原木", True, "wood"),
    ("pale_oak_log", "Pale Oak Log", "苍白橡木原木", True, "wood"),
    ("dirt", "Dirt", "泥土", False, None),
    ("sand", "Sand", "沙子", False, None),
    ("gravel", "Gravel", "沙砾", False, None),
    ("netherrack", "Netherrack", "下界岩", False, None),
    ("end_stone", "End Stone", "末地石", False, None),
    ("obsidian", "Obsidian", "黑曜石", False, None),
]

TOOL_TYPES = ["pickaxe", "axe", "shovel", "hoe", "sword"]
TOOL_ZH = {"pickaxe": "镐", "axe": "斧", "shovel": "锹", "hoe": "锄", "sword": "剑"}

SIZE = 16


def load_base(name):
    kind, _, file = name.partition("/")
    return Image.open(os.path.join(VANILLA, f"{kind}_{file}")).convert("RGBA")


def avg_color(img):
    px = list(img.getdata())
    r = sum(p[0] for p in px) / len(px)
    g = sum(p[1] for p in px) / len(px)
    b = sum(p[2] for p in px) / len(px)
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
    d.rectangle([inset, inset, SIZE - 1 - inset, SIZE - 1 - inset], outline=color)
    return img


def gen_block_tex(base, level):
    """方块贴图：field 随重数加深 + 深色嵌套环逐级累积（第 n 重比第 n-1 重多一环）。

    九重额外：field 更深 + 中心 2×2 亮点（"压缩到核心"标记），保证与八重可辨。
    """
    avg = avg_color(base)
    f = max(0.42, 0.84 ** (level - 1))
    if level >= 9:
        f = 0.34
    img = darken(base, f)
    ring_a = scale_color(avg, 0.10)
    ring_b = scale_color(avg, 0.28)
    for i in range(level):
        inset = i  # 0,1,2,... 最外圈从边缘开始
        if inset > 7:
            break
        draw_frame(img, inset, ring_a if i % 2 == 0 else ring_b)
    if level >= 9:
        d = ImageDraw.Draw(img)
        light = mix(avg, (255, 255, 255), 0.6)
        d.rectangle([7, 7, 8, 8], fill=light)
    return img


def head_mask(sprite_stone):
    """头部像素 = 石工具 sprite 上的低饱和度灰像素。

    原版同类型工具（木/石/铁…）几何完全一致，只有配色不同，
    所以石 sprite 的灰像素坐标可以直接作为所有材质变体的头部遮罩。
    """
    mask = []
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = sprite_stone.getpixel((x, y))
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < 0.25 and v > 0.15:
                mask.append((x, y))
    return mask


def gen_tool_tex(tool, level, base_kind, mat_color):
    """工具贴图：原版石/木 sprite 的头部按遮罩重新着色为材料色（随重数加深）。"""
    kind = "stone" if base_kind == "stone" else "wooden"
    sprite = load_base(f"item/{kind}_{tool}.png")
    out = sprite.copy()
    mask = head_mask(load_base(f"item/stone_{tool}.png"))
    if not mask:
        print(f"WARN: empty head mask for {tool}", file=sys.stderr)
        return out
    mr, mg, mb, _ = mat_color
    for x, y in mask:
        r, g, b, a = sprite.getpixel((x, y))
        # 用原像素明度（0.35~1.1 拉伸）乘材料色，保留雕刻细节又带足材料色
        lum = (0.299 * r + 0.587 * g + 0.114 * b) / 160.0
        k = max(0.35, min(1.1, lum))
        out.putpixel((x, y), (min(255, int(mr * k)), min(255, int(mg * k)), min(255, int(mb * k)), a))
    # 六重及以上（不可破坏）：沿头部左上轮廓加一道浅色高光作为"无限"标记
    if level >= 6 and mask:
        s_min = min(x + y for x, y in mask)
        for x, y in mask:
            if x + y - s_min <= 1:
                out.putpixel((x, y), (235, 240, 255, 255))
    return out


def gen_stick_tex(level):
    """压缩木棍：木棍 sprite 逐级加深 + 剪影描边加深；九重加白色顶点标记。"""
    sprite = load_base("item/stick.png")
    img = darken(sprite, max(0.42, 0.84 ** (level - 1)))
    # 剪影边缘再压暗一档（描边感）
    px = img.load()
    edges = []
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < SIZE and 0 <= ny < SIZE) or px[nx, ny][3] == 0:
                    edges.append((x, y))
                    break
    for x, y in edges:
        r, g, b, a = px[x, y]
        px[x, y] = (int(r * 0.55), int(g * 0.55), int(b * 0.55), a)
    if level >= 9:
        for x, y in mask_top_pixels(sprite, 2):
            px[x, y] = (240, 240, 255, 255)
    return img


def mask_top_pixels(img, count):
    """sprite 剪影最上方的若干像素（用于九重标记）。"""
    pts = [(x, y) for y in range(SIZE) for x in range(SIZE) if img.getpixel((x, y))[3] != 0]
    min_y = min(y for _, y in pts)
    return [(x, y) for x, y in pts if y <= min_y + 1][:count]


def contact_sheet(images, cols, cell=20, scale=4):
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell * scale, rows * cell * scale), (40, 40, 40, 255))
    for i, img in enumerate(images):
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        x = (i % cols) * cell * scale + 2 * scale
        y = (i // cols) * cell * scale + 2 * scale
        sheet.paste(big, (x, y))
    return sheet


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-root", default=ROOT)
    args = ap.parse_args()

    block_tex, item_tex = {}, {}
    for key, en, zh, can_tool, kind in MATERIALS:
        base = load_base(f"block/{key}.png")
        for level, prefix, en_p, zh_p in LEVELS:
            name = f"{prefix}_{key}"
            block_tex[name] = gen_block_tex(base, level)
            if can_tool:
                mat_color = avg_color(block_tex[name]) + (255,)
                for tool in TOOL_TYPES:
                    item_tex[f"{prefix}_{key}_{tool}"] = gen_tool_tex(tool, level, kind, mat_color)
    for level, prefix, en_p, zh_p in LEVELS:
        item_tex[f"{prefix}_stick"] = gen_stick_tex(level)

    print(f"block textures: {len(block_tex)}, item textures: {len(item_tex)}")
    for sub in SUBPROJECTS:
        bdir = os.path.join(args.out_root, os.path.relpath(sub, ROOT), "src", "main", "resources",
                            "assets", "compressedblocks", "textures")
        if os.path.exists(os.path.join(args.out_root, os.path.relpath(sub, ROOT), "build.gradle")):
            for name, img in block_tex.items():
                p = os.path.join(bdir, "block", name + ".png")
                os.makedirs(os.path.dirname(p), exist_ok=True)
                img.save(p)
            for name, img in item_tex.items():
                p = os.path.join(bdir, "item", name + ".png")
                os.makedirs(os.path.dirname(p), exist_ok=True)
                img.save(p)
            print(f"wrote textures into {sub}")

    # mod 图标：九重压缩圆石 8x 放大
    icon = block_tex["9x_cobblestone"].resize((128, 128), Image.NEAREST)
    icon.save(os.path.join(ROOT, "_asset-src", "icon.png"))

    # 预览拼图：方块按材料分行 × 9 重
    sheet = Image.new("RGBA", (10 * 22 * 4, len(MATERIALS) * 22 * 4), (40, 40, 40, 255))
    for r, (key, en, zh, _, _) in enumerate(MATERIALS):
        for c, (level, prefix, _, _) in enumerate(LEVELS):
            img = block_tex[f"{prefix}_{key}"].resize((64, 64), Image.NEAREST)
            sheet.paste(img, (c * 88 + 4, r * 88 + 4))
    sheet.save(os.path.join(ROOT, "_asset-src", "preview_blocks.png"))

    # 工具预览：每种材料 5 行（5 工具）× 9 重
    tool_rows = [(k, e, z) for k, e, z, t, _ in MATERIALS if t]
    icon = 88
    cols = len(LEVELS) * len(TOOL_TYPES)
    sheet = Image.new("RGBA", (cols * icon, len(tool_rows) * len(TOOL_TYPES) * icon), (40, 40, 40, 255))
    for r, (key, en, zh) in enumerate(tool_rows):
        for t_i, tool in enumerate(TOOL_TYPES):
            for c, (level, prefix, _, _) in enumerate(LEVELS):
                img = item_tex[f"{prefix}_{key}_{tool}"].resize((64, 64), Image.NEAREST)
                x = ((t_i * len(LEVELS)) + c) * icon + 4
                y = (r * len(TOOL_TYPES) + t_i) * icon + 4
                sheet.paste(img, (x, y))
    sheet.save(os.path.join(ROOT, "_asset-src", "preview_tools.png"))
    print("previews written to _asset-src/preview_blocks.png, preview_tools.png")


if __name__ == "__main__":
    main()
