"""生成配装（武器 / 插件）图片的 Android drawable + 名字映射。

源图来自 antigravity-panel-extracted 的 images/nzmimg/collection/{weapons,plugins}，
文件名就是游戏内的物品名（可能带中文与横杠）。

为什么打成 drawable 而不是按名字去资源里查：
Android 资源名只允许 [a-z0-9_.]，中文名没法当资源名，所以统一编号成
weapon_001 / plugin_001，再生成一份 `名称 -> R.drawable.xxx` 的显式 when。
和 MapArt.android.kt 一个路子：编译期就能发现漏文件，也不用反射。

用法：
    python tools/gen_equipment_art.py
"""

import os
import re
import shutil
import sys

from PIL import Image

SRC_ROOT = r"D:\Task\反重力数据面板开发\antigravity-panel-extracted\images\nzmimg\collection"
PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(PROJECT, "shared", "src", "androidMain", "res", "drawable-nodpi")
ANDROID_DIR = os.path.join(
    PROJECT, "shared", "src", "androidMain", "kotlin", "com", "nzd", "antigravitypanel",
    "ui", "component",
)

# 展示尺寸：武器卡里最高 85px、插件格 42px，按 3x 屏留余量
TARGETS = [
    # (源目录, 资源前缀, 目标宽, 目标高或 None 表示按宽等比)
    ("weapons", "weapon", 320, None),
    ("plugins", "plugin", 128, 128),
]

QUALITY = 80


def sanitize(name: str) -> str:
    """物品名里可能带文件名不允许的字符（官方前端也做了同样的替换）。"""
    return re.sub(r'[\\/:*?"<>|]', "", name)


def convert(src_dir: str, prefix: str, width: int, height):
    files = sorted(
        f for f in os.listdir(src_dir)
        if f.lower().endswith((".webp", ".png", ".jpg", ".jpeg"))
    )
    out = []
    total = 0
    for index, filename in enumerate(files, start=1):
        name = sanitize(os.path.splitext(filename)[0])
        res_name = f"{prefix}_{index:03d}"
        dst = os.path.join(RES_DIR, f"{res_name}.webp")
        with Image.open(os.path.join(src_dir, filename)) as im:
            if im.mode not in ("RGB", "RGBA"):
                im = im.convert("RGBA")
            if height is not None:
                im = im.resize((width, height), Image.LANCZOS)
            else:
                ratio = width / im.width
                im = im.resize((width, max(1, round(im.height * ratio))), Image.LANCZOS)
            im.save(dst, "WEBP", quality=QUALITY, method=4)
        total += os.path.getsize(dst)
        out.append((name, res_name))
    return out, total


def kotlin_literal(name: str) -> str:
    return '"' + name.replace("\\", "\\\\").replace('"', '\\"') + '"'


def write_actual(weapons, plugins):
    lines = []
    lines.append("package com.nzd.antigravitypanel.ui.component")
    lines.append("")
    lines.append("import androidx.compose.runtime.Composable")
    lines.append("import androidx.compose.ui.graphics.painter.Painter")
    lines.append("import androidx.compose.ui.res.painterResource")
    lines.append("import com.nzd.antigravitypanel.shared.R")
    lines.append("")
    lines.append("/**")
    lines.append(" * 配装图片的 Android 实现。")
    lines.append(" *")
    lines.append(" * **这个文件是 `tools/gen_equipment_art.py` 生成的，别手改** ——")
    lines.append(" * 物品名和资源编号的对应关系改一处就得改另一处，手改必然对不上。")
    lines.append(" *")
    lines.append(" * 查不到就返回 null，调用方退化成只显示名字：")
    lines.append(" * 官方随时会出新武器，包里没有图比显示一张裂图好。")
    lines.append(" */")
    lines.append("@Composable")
    lines.append("actual fun weaponPainter(name: String): Painter? {")
    lines.append("    val id = when (name) {")
    for name, res in weapons:
        lines.append(f"        {kotlin_literal(name)} -> R.drawable.{res}")
    lines.append("        else -> return null")
    lines.append("    }")
    lines.append("    return painterResource(id = id)")
    lines.append("}")
    lines.append("")
    lines.append("@Composable")
    lines.append("actual fun pluginPainter(name: String): Painter? {")
    lines.append("    val id = when (name) {")
    for name, res in plugins:
        lines.append(f"        {kotlin_literal(name)} -> R.drawable.{res}")
    lines.append("        else -> return null")
    lines.append("    }")
    lines.append("    return painterResource(id = id)")
    lines.append("}")

    path = os.path.join(ANDROID_DIR, "EquipmentArt.android.kt")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return path


def main():
    if not os.path.isdir(SRC_ROOT):
        sys.exit(f"源目录不存在：{SRC_ROOT}")
    os.makedirs(RES_DIR, exist_ok=True)

    # 清掉上一次生成的，避免改名后留下孤儿文件
    for existing in os.listdir(RES_DIR):
        if existing.startswith(("weapon_", "plugin_")):
            os.remove(os.path.join(RES_DIR, existing))

    grand = 0
    weapons = []
    plugins = []
    for folder, prefix, width, height in TARGETS:
        src = os.path.join(SRC_ROOT, folder)
        items, total = convert(src, prefix, width, height)
        grand += total
        if prefix == "weapon":
            weapons = items
        else:
            plugins = items
        print(f"{folder}: {len(items)} 张 -> {total / 1024:.0f} KB")

    path = write_actual(weapons, plugins)
    print(f"生成 {path}")
    print(f"合计 {grand / 1024:.0f} KB，武器 {len(weapons)} / 插件 {len(plugins)}")


if __name__ == "__main__":
    main()
