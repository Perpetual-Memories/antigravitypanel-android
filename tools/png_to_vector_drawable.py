#!/usr/bin/env python3
"""把扁平单色图标源图转成 VectorDrawable（HyperIsland 那种单文件矢量图标形式）。

为什么不用自适应图标：HyperOS / MIUI 会对 adaptive icon 的图层做放大再套遮罩，
字形按 70dp 排也会显得顶到边。改成单个 VectorDrawable 交给系统，尺寸完全可控，
也和 HyperIsland 在 HyperOS 上的表现一致。

做法：
1. 复用 make_launcher_icons.extract_glyph 把字形从白底扣出来（得到扁平色 + 抗锯齿 alpha）
2. alpha 阈值化成二值掩膜，用 cv2.findContours 取外轮廓与内洞
3. RDP 抽稀 → Catmull-Rom 转三次贝塞尔，避免放大后出现折线感
4. 输出一个 108dp/1080 视口的 vector：圆角方背景 + 一个 <group> 包住字形

字形路径先铺满 1080 视口，再由 <group> 以画布中心为轴缩放，
所以 group 上的 scaleX/scaleY 就直接等于"字形占整块的比例"，改一个数即可。

用法：
    python tools/png_to_vector_drawable.py <源图> <输出 xml> [字形占整块的比例，默认 0.56]
"""

from __future__ import annotations

import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

from make_launcher_icons import extract_glyph

VIEWPORT = 1080          # 108dp 画布，viewport 取 10 倍，坐标精度够用
DP = 108
CORNER_RATIO = 0.1875    # 源图圆角半径 / 边长
BG_COLOR = "#FEFEFE"     # 源图背景色

TRACE_WIDTH = 512        # 追踪用掩膜的宽度。源图本身只有 256，这里取 512 就够了，
# 再高只会让 RDP 输出更多冗余点（曲线拟合本身已经平滑）
RDP_EPS = 0.6            # RDP 抽稀阈值（追踪掩膜的像素单位）
ALPHA_THRESHOLD = 128
KAPPA = 0.5522847498     # 圆角用三次贝塞尔近似圆的系数


def rdp(points: np.ndarray, eps: float) -> list[tuple[float, float]]:
    """Ramer-Douglas-Peucker 抽稀（闭环首尾相连，先找最远点再递归）。"""
    pts = [tuple(p) for p in points]

    def rec(start: int, end: int) -> list[int]:
        # 找 start..end 之间离线最远的点
        x1, y1 = pts[start]
        x2, y2 = pts[end]
        dx, dy = x2 - x1, y2 - y1
        norm = (dx * dx + dy * dy) ** 0.5
        max_d, max_i = -1.0, -1
        for i in range(start + 1, end):
            x0, y0 = pts[i]
            if norm == 0:
                d = ((x0 - x1) ** 2 + (y0 - y1) ** 2) ** 0.5
            else:
                d = abs(dy * (x0 - x1) - dx * (y0 - y1)) / norm
            if d > max_d:
                max_d, max_i = d, i
        if max_d <= eps or max_i < 0:
            return []
        return rec(start, max_i) + [max_i] + rec(max_i, end)

    n = len(pts)
    if n < 4:
        return pts
    # 闭环：取首尾两点把环拆成两段分别抽稀
    a, b = 0, n // 2
    keep = {a, b} | set(rec(a, b)) | set(rec(b, n - 1)) | set(rec(n - 1, a))
    return [pts[i] for i in sorted(keep)]


def catmull_rom_to_bezier(pts: list[tuple[float, float]]) -> str:
    """闭环点列 → 三次贝塞尔 pathData（Catmull-Rom 转 Bezier，曲线过每个点）。"""
    n = len(pts)
    if n < 3:
        return ""
    d = [f"M{fmt(pts[0][0])},{fmt(pts[0][1])}"]
    for i in range(n):
        p0 = pts[(i - 1) % n]
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        p3 = pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6.0, p1[1] + (p2[1] - p0[1]) / 6.0)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6.0, p2[1] - (p3[1] - p1[1]) / 6.0)
        d.append(
            f"C{fmt(c1[0])},{fmt(c1[1])} {fmt(c2[0])},{fmt(c2[1])} "
            f"{fmt(p2[0])},{fmt(p2[1])}"
        )
    d.append("Z")
    return "".join(d)


def fmt(v: float) -> str:
    s = f"{v:.1f}"
    return s[:-2] if s.endswith(".0") else s


def rounded_rect_path(v: float, r: float) -> str:
    k = r * KAPPA
    return (
        f"M{fmt(r)},0"
        f"L{fmt(v - r)},0"
        f"C{fmt(v - r + k)},0 {fmt(v)},{fmt(r - k)} {fmt(v)},{fmt(r)}"
        f"L{fmt(v)},{fmt(v - r)}"
        f"C{fmt(v)},{fmt(v - r + k)} {fmt(v - r + k)},{fmt(v)} {fmt(v - r)},{fmt(v)}"
        f"L{fmt(r)},{fmt(v)}"
        f"C{fmt(r - k)},{fmt(v)} 0,{fmt(v - r + k)} 0,{fmt(v - r)}"
        f"L0,{fmt(r)}"
        f"C0,{fmt(r - k)} {fmt(r - k)},0 {fmt(r)},0"
        f"Z"
    )


def dominant_color(glyph: Image.Image) -> str:
    """取字形主色（alpha 接近满的像素里出现最多的颜色）。"""
    px = glyph.load()
    w, h = glyph.size
    counter: dict[tuple[int, int, int], int] = {}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 250:
                key = (r >> 2 << 2, g >> 2 << 2, b >> 2 << 2)
                counter[key] = counter.get(key, 0) + 1
    best = max(counter.items(), key=lambda kv: kv[1])[0]
    return "#%02X%02X%02X" % best


def build_paths(glyph: Image.Image) -> tuple[list[str], tuple[float, float]]:
    """返回 (pathData 列表, 追踪坐标系下的 (宽, 高))。"""
    gw, gh = glyph.size
    tw = TRACE_WIDTH
    th = max(1, round(gh * tw / gw))
    big = glyph.resize((tw, th), Image.LANCZOS)
    alpha = np.array(big.getchannel("A"))
    mask = (alpha >= ALPHA_THRESHOLD).astype(np.uint8) * 255

    contours, _ = cv2.findContours(mask, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_NONE)
    paths: list[str] = []
    for c in contours:
        pts = c.reshape(-1, 2).astype(float)
        if len(pts) < 12:
            continue
        simplified = rdp(pts, RDP_EPS)
        if len(simplified) < 3:
            continue
        d = catmull_rom_to_bezier(simplified)
        if d:
            paths.append(d)
    return paths, (float(tw), float(th))


def main() -> None:
    if len(sys.argv) not in (3, 4):
        raise SystemExit(__doc__)
    src = Path(sys.argv[1])
    out = Path(sys.argv[2])
    ratio = float(sys.argv[3]) if len(sys.argv) == 4 else 0.56

    glyph = extract_glyph(src)
    paths, (gw, gh) = build_paths(glyph)
    if not paths:
        raise SystemExit("没追踪到任何轮廓")

    # 字形先铺满 1080 视口（长边对齐、居中），缩放交给 <group>
    fit = VIEWPORT / max(gw, gh)
    tx = (VIEWPORT - gw * fit) / 2
    ty = (VIEWPORT - gh * fit) / 2

    color = dominant_color(glyph)
    r = VIEWPORT * CORNER_RATIO

    parts = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{DP}dp"',
        f'    android:height="{DP}dp"',
        f'    android:viewportWidth="{VIEWPORT}"',
        f'    android:viewportHeight="{VIEWPORT}">',
        "",
        "    <!-- 背景：圆角方，颜色取自源图 -->",
        "    <path",
        f'        android:fillColor="{BG_COLOR}"',
        f'        android:pathData="{rounded_rect_path(VIEWPORT, r)}" />',
        "",
        f"    <!-- 字形：占 {ratio:.0%}。想调大小只改下面这一组的 scaleX/scaleY -->",
        "    <group",
        f'        android:pivotX="{VIEWPORT / 2:g}"',
        f'        android:pivotY="{VIEWPORT / 2:g}"',
        f'        android:scaleX="{ratio:g}"',
        f'        android:scaleY="{ratio:g}">',
        "        <group",
        '            android:pivotX="0"',
        '            android:pivotY="0"',
        f'            android:scaleX="{fit:.5f}"',
        f'            android:scaleY="{fit:.5f}"',
        f'            android:translateX="{tx:.1f}"',
        f'            android:translateY="{ty:.1f}">',
        "            <path",
        f'                android:fillColor="{color}"',
        '                android:fillType="evenOdd"',
        '                android:pathData="',
    ]
    for d in paths:
        parts.append(f"                {d}")
    parts += [
        '" />',
        "",
        "        </group>",
        "    </group>",
        "</vector>",
        "",
    ]

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(parts), encoding="utf-8")
    print(
        f"写出 {out}（{len(paths)} 条轮廓，主色 {color}，"
        f"字形占比 {ratio:.0%}，{len(out.read_bytes())} 字节）"
    )


if __name__ == "__main__":
    main()
