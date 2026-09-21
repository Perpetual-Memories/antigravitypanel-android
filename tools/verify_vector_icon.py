#!/usr/bin/env python3
"""校验 png_to_vector_drawable.py 产出的 VectorDrawable 有没有把字形画对。

做法：把生成的 XML 光栅化，与"源字形按同一套 group 变换贴上去"的参考掩膜算 IoU。
纯数值判断，不依赖肉眼看图。

    IoU >= 0.94  轮廓还原正常
    IoU <  0.5   坐标系算错了（比如把 1024 母版和 512 追踪图混用），不是图形错了

用法：
    python tools/verify_vector_icon.py <源图> <生成的 xml> [渲染边长，默认 512]
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from make_launcher_icons import extract_glyph, BG_COLOR

TRACE_WIDTH = 512   # 必须与 png_to_vector_drawable.py 里的 TRACE_WIDTH 一致

NUM = re.compile(r"[-+]?(?:\d*\.\d+|\d+)")
CMD = re.compile(r"[MmLlCcZz]")


# ---------- pathData 解析与光栅化 ----------

def tokenize(d: str):
    toks, i = [], 0
    while i < len(d):
        m = CMD.match(d, i)
        if m:
            toks.append(m.group(0))
            i = m.end()
            continue
        m = NUM.match(d, i)
        if m:
            toks.append(float(m.group(0)))
            i = m.end()
            continue
        i += 1
    return toks


def parse(d: str):
    """返回子路径列表，每条是 [(x, y), ...] 折线（贝塞尔已离散化）。"""
    toks = tokenize(d)
    subs, cur, pos, start = [], [], (0.0, 0.0), (0.0, 0.0)
    i = 0
    while i < len(toks):
        c = toks[i]
        if c in "Mm":
            if cur:
                subs.append(cur)
            x, y = toks[i + 1], toks[i + 2]
            pos = (pos[0] + x, pos[1] + y) if c == "m" else (x, y)
            start, cur = pos, [pos]
            i += 3
        elif c in "Ll":
            x, y = toks[i + 1], toks[i + 2]
            pos = (pos[0] + x, pos[1] + y) if c == "l" else (x, y)
            cur.append(pos)
            i += 3
        elif c in "Cc":
            rel = c == "c"
            pts = []
            for k in range(3):
                px, py = toks[i + 1 + 2 * k], toks[i + 2 + 2 * k]
                pts.append((pos[0] + px, pos[1] + py) if rel else (px, py))
            p0, p1, p2, p3 = pos, pts[0], pts[1], pts[2]
            for s in range(1, 13):
                t = s / 12
                mt = 1 - t
                cur.append(
                    (
                        mt**3 * p0[0] + 3 * mt * mt * t * p1[0]
                        + 3 * mt * t * t * p2[0] + t**3 * p3[0],
                        mt**3 * p0[1] + 3 * mt * mt * t * p1[1]
                        + 3 * mt * t * t * p2[1] + t**3 * p3[1],
                    )
                )
            pos = p3
            i += 7
        elif c in "Zz":
            cur.append(start)
            subs.append(cur)
            cur, pos = [], start
            i += 1
        else:
            i += 1
    if cur:
        subs.append(cur)
    return subs


def rasterize(subs, size: int) -> np.ndarray:
    """even-odd 填充，返回 0/255 掩膜（用射线交叉计数，避免依赖 cv2）。"""
    grid = np.zeros((size, size), np.uint8)
    ys = np.arange(size) + 0.5
    for sub in subs:
        poly = np.array(sub, dtype=float)
        if len(poly) < 3:
            continue
        # 只扫多边形纵向范围内的行
        y0 = max(0, int(np.floor(poly[:, 1].min())))
        y1 = min(size, int(np.ceil(poly[:, 1].max())) + 1)
        for y in range(y0, y1):
            yc = y + 0.5
            xs = []
            n = len(poly)
            for i in range(n):
                x1, y1_ = poly[i]
                x2, y2_ = poly[(i + 1) % n]
                if (y1_ <= yc < y2_) or (y2_ <= yc < y1_):
                    xs.append(x1 + (yc - y1_) * (x2 - x1) / (y2_ - y1_))
            xs.sort()
            for k in range(0, len(xs) - 1, 2):
                a = max(0, int(np.ceil(xs[k] - 0.5)))
                b = min(size, int(np.floor(xs[k + 1] - 0.5)) + 1)
                if b > a:
                    grid[y, a:b] ^= 255
    return grid


# ---------- XML 解析 ----------

def group_transforms(xml: str) -> dict[str, float]:
    """把嵌套 <group> 的变换复合成 {sx, sy, tx, ty}（由内向外累乘）。"""
    def one(tag: str) -> dict[str, float]:
        def f(name, default=0.0):
            m = re.search(rf'android:{name}="([-\d.]+)"', tag)
            return float(m.group(1)) if m else default

        return {
            "px": f("pivotX"), "py": f("pivotY"),
            "sx": f("scaleX", 1.0), "sy": f("scaleY", 1.0),
            "tx": f("translateX"), "ty": f("translateY"),
        }

    m = {"sx": 1.0, "sy": 1.0, "tx": 0.0, "ty": 0.0}
    for g in reversed([one(t) for t in re.findall(r"<group(.*?)>", xml, re.S)]):
        m = {
            "sx": g["sx"] * m["sx"],
            "sy": g["sy"] * m["sy"],
            "tx": g["sx"] * m["tx"] + g["tx"] + g["px"] * (1 - g["sx"]),
            "ty": g["sy"] * m["ty"] + g["ty"] + g["py"] * (1 - g["sy"]),
        }
    return m


def main() -> None:
    if len(sys.argv) not in (3, 4):
        raise SystemExit(__doc__)
    src, xml_path = Path(sys.argv[1]), Path(sys.argv[2])
    size = int(sys.argv[3]) if len(sys.argv) == 4 else 512

    xml = xml_path.read_text(encoding="utf-8")
    vw = float(re.search(r'android:viewportWidth="([\d.]+)"', xml).group(1))
    tr = group_transforms(xml)

    # --- 参考掩膜：源字形 → 追踪尺寸 → 套 group 变换 ---
    glyph = extract_glyph(src)
    gw, gh = glyph.size
    th = max(1, round(gh * TRACE_WIDTH / gw))
    tmask = (
        np.array(glyph.resize((TRACE_WIDTH, th), Image.LANCZOS).getchannel("A")) >= 128
    )
    k = size / vw
    dw, dh = max(1, round(TRACE_WIDTH * tr["sx"] * k)), max(1, round(th * tr["sy"] * k))
    ref = np.zeros((size, size), np.uint8)
    oy, ox = round(tr["ty"] * k), round(tr["tx"] * k)
    ref[oy : oy + dh, ox : ox + dw] = (
        np.array(Image.fromarray((tmask * 255).astype(np.uint8)).resize(
            (dw, dh), Image.NEAREST)) > 0
    )

    # --- 渲染生成图：背景与字形分开光栅化，只比字形 ---
    glyph_mask = np.zeros((size, size), np.uint8)
    bg_mask = np.zeros((size, size), np.uint8)
    for pm in re.finditer(r'<path\b(.*?)android:pathData="(.*?)"\s*/>', xml, re.S):
        head, d = pm.group(1), pm.group(2)
        in_group = "<group" in xml[: pm.start()]
        s = (tr["sx"], tr["sy"], tr["tx"], tr["ty"]) if in_group else (1.0, 1.0, 0.0, 0.0)
        subs = [
            [[(x * s[0] + s[2]) * k, (y * s[1] + s[3]) * k] for x, y in sub]
            for sub in parse(d)
        ]
        layer = rasterize(subs, size)
        if in_group:
            glyph_mask ^= layer
        else:
            bg_mask ^= layer

    inter = int(np.count_nonzero(ref & glyph_mask))
    union = int(np.count_nonzero(ref | glyph_mask))
    iou = inter / union if union else 0.0

    ys, xs = np.where(glyph_mask > 0)
    bw = int(xs.max() - xs.min() + 1) if len(xs) else 0
    bh = int(ys.max() - ys.min() + 1) if len(ys) else 0

    print(f"参考 {int(np.count_nonzero(ref))} px，渲染字形 {int(np.count_nonzero(glyph_mask))} px")
    print(f"IoU = {iou:.4f}  (交集 {inter} / 并集 {union})")
    print(f"渲染字形 bbox {bw}x{bh} -> 占画布 {bw / size:.3f} x {bh / size:.3f}")

    # 顺带看看字形有没有溢出背景
    spill = int(np.count_nonzero(glyph_mask & ~bg_mask.astype(bool)))
    print(f"溢出背景的像素 {spill}")
    print("结论:", "OK（轮廓还原正常）" if iou >= 0.94 else "偏低，检查坐标系是否一致")


if __name__ == "__main__":
    main()
