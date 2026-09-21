#!/usr/bin/env python3
"""从 PC 端图标源图生成 Android 启动图标资源。

源图（`6.png`）是 256x256 的圆角方图：白底 + 浅蓝字形。
Android 自适应图标要求前景/背景分层，所以这里要把字形从白底里抠出来：

1. "像字形" 的判据用 `blue_score = B - R`（背景与灰色描边都接近 0，字形约 +115）；
2. 圆角方框自身的描边也是淡蓝，用对不透明区域做形态学腐蚀的办法去掉；
3. 反预乘还原字形本色，避免边缘残留白边；
4. 放大时 alpha 走 LANCZOS（平滑边缘）、RGB 走 NEAREST（保持纯色不糊）。

用法：
    python tools/make_launcher_icons.py <源图> <res 目录>
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

# 自适应图标：108dp 画布，可见遮罩约 72dp，关键内容需落在中心 66dp 内。
ADAPTIVE_CANVAS_DP = 108
GLYPH_MAX_DP = 70  # 字形最长边占多少 dp，取 70 兼顾"不被裁"和"不显小"

# 常规（legacy）图标尺寸，单位 px
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# 自适应前景只需要 xxhdpi 以上，但一并生成，成本极低
FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

MASTER = 1024          # 中间母版边长
ERODE_RADIUS = 5       # 腐蚀半径，用于剔除圆角方框描边
MIN_SOURCE_ALPHA = 200  # 源图里字形画在白底上，字形像素必然全不透明；
# 圆角处的抗锯齿噪声 alpha 只有 10~60，卡在这里正好滤掉
MIN_BLUE_SCORE = 8     # 白底本身有 ±1 的抖动（254,254,255），不设下限会把整片背景都算成极淡的字形

BG_COLOR = (254, 254, 254)
CORNER_RATIO = 0.1875  # 源图圆角半径 / 边长


def extract_glyph(src: Path) -> Image.Image:
    """返回母版尺寸的字形 RGBA（背景全透明，RGB 已统一为字形本色）。"""
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    px = im.load()

    # 1. 不透明区域腐蚀，去掉圆角方框描边
    opaque = Image.new("L", (w, h), 0)
    op = opaque.load()
    for y in range(h):
        for x in range(w):
            op[x, y] = 255 if px[x, y][3] > MIN_SOURCE_ALPHA else 0
    eroded = opaque.filter(ImageFilter.MinFilter(ERODE_RADIUS * 2 + 1))
    ep = eroded.load()

    # 2. blue_score 满覆盖基准：取字形内的高分位，避免个别噪点拉高
    scores = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > MIN_SOURCE_ALPHA and ep[x, y] > 0 and (b - r) >= MIN_BLUE_SCORE:
                scores.append(b - r)
    scores.sort()
    full = scores[int(len(scores) * 0.98)]
    if full <= 0:
        raise SystemExit("源图里没找到字形像素，blue_score 基准为 0")

    bg_r, bg_g, bg_b = BG_COLOR

    # 3. 反预乘：pixel = color*a + bg*(1-a)，alpha 由 blue_score 归一化得到
    glyph = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gp = glyph.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            s = b - r
            if a <= MIN_SOURCE_ALPHA or s < MIN_BLUE_SCORE or ep[x, y] == 0:
                continue
            alpha = min(1.0, s / full)
            inv = 1.0 - alpha
            cr = min(255.0, max(0.0, (r - bg_r * inv) / alpha))
            cg = min(255.0, max(0.0, (g - bg_g * inv) / alpha))
            cb = min(255.0, max(0.0, (b - bg_b * inv) / alpha))
            gp[x, y] = (round(cr), round(cg), round(cb), round(alpha * 255))

    bbox = glyph.getbbox()
    if bbox is None:
        raise SystemExit("字形提取结果为空")
    glyph = glyph.crop(bbox)
    print(f"字形包围盒 {bbox}，裁切后 {glyph.size}")

    # 4. 放大到母版：alpha 平滑、RGB 保持纯色
    tw, th = glyph.size
    scale = MASTER / max(tw, th)
    new_size = (max(1, round(tw * scale)), max(1, round(th * scale)))

    alpha_hi = glyph.getchannel("A").resize(new_size, Image.LANCZOS)
    rgb_hi = glyph.convert("RGB").resize(new_size, Image.NEAREST)

    master = Image.new("RGBA", new_size, (0, 0, 0, 0))
    master.paste(rgb_hi, (0, 0))
    master.putalpha(alpha_hi)
    return master


def place(glyph: Image.Image, canvas: int, glyph_max_px: int) -> Image.Image:
    """把字形等比缩放并居中放进 canvas x canvas 的画布，返回 RGBA。"""
    gw, gh = glyph.size
    scale = glyph_max_px / max(gw, gh)
    size = (max(1, round(gw * scale)), max(1, round(gh * scale)))
    scaled = glyph.resize(size, Image.LANCZOS)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.paste(scaled, ((canvas - size[0]) // 2, (canvas - size[1]) // 2))
    return out


def mask_layer(glyph: Image.Image, canvas: int, glyph_max_px: int, shape: str) -> Image.Image:
    """把字形贴上背景色并裁成圆角方 / 圆形，用于 legacy 图标。"""
    out = Image.new("RGBA", (canvas, canvas), BG_COLOR + (255,))
    out.alpha_composite(place(glyph, canvas, glyph_max_px))
    mask = Image.new("L", (canvas, canvas), 0)
    draw = ImageDraw.Draw(mask)
    if shape == "circle":
        draw.ellipse((0, 0, canvas - 1, canvas - 1), fill=255)
    else:
        draw.rounded_rectangle(
            (0, 0, canvas - 1, canvas - 1),
            radius=round(canvas * CORNER_RATIO),
            fill=255,
        )
    out.putalpha(mask)
    return out


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- 由 tools/make_launcher_icons.py 生成，勿手改 -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""

BACKGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- 由 tools/make_launcher_icons.py 生成，勿手改 -->
<color xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#%02X%02X%02X" />
""" % BG_COLOR


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    src = Path(sys.argv[1])
    res = Path(sys.argv[2])
    if not src.is_file():
        raise SystemExit(f"源图不存在：{src}")

    glyph = extract_glyph(src)

    anydpi = res / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text(ADAPTIVE_XML, encoding="utf-8")
    (anydpi / "ic_launcher_round.xml").write_text(ADAPTIVE_XML, encoding="utf-8")

    drawable = res / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "ic_launcher_background.xml").write_text(BACKGROUND_XML, encoding="utf-8")

    for density, px in FOREGROUND_SIZES.items():
        d = res / f"mipmap-{density}"
        d.mkdir(parents=True, exist_ok=True)
        # 自适应画布 108dp；字形最长边按 dp 换算成像素
        glyph_px = round(px * GLYPH_MAX_DP / ADAPTIVE_CANVAS_DP)
        place(glyph, px, glyph_px).save(d / "ic_launcher_foreground.png")

    for density, px in LEGACY_SIZES.items():
        d = res / f"mipmap-{density}"
        d.mkdir(parents=True, exist_ok=True)
        # legacy 图标里字形按源图比例占 78%
        glyph_px = round(px * 0.78)
        mask_layer(glyph, px, glyph_px, "rounded").save(d / "ic_launcher.png")
        mask_layer(glyph, px, glyph_px, "circle").save(d / "ic_launcher_round.png")

    print("完成：", res)


if __name__ == "__main__":
    main()
