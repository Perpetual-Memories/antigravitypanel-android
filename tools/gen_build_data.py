"""生成「Build 计划」要用的武器 / 插件目录、图片与画笔映射。

源数据来自 nzm-wiki（`https://github.com/lostlightll/nzm-wiki`）：
- 武器：`data/weapons/*.mdx` 的 frontmatter（title / use_type / weapon_type / rarity）
- 插件：`data/perks/slot-{1,2,3,4}/*.mdx`（title / slot / rarity / icon）

**图片优先复用包里已经有的那批**（`weapon_NNN` / `plugin_NNN`，由 `gen_equipment_art.py`
从官方 PC 端图集生成）：名字对得上就直接用，只有对不上的才从 wiki 转一张新的。
这样既能覆盖 wiki 全量（121 把武器 / 528 个插件），又不把同一张图打包两遍。

产出：
- `shared/src/androidMain/res/drawable-nodpi/build_weapon_NNN.webp`（缺的那些）
- `shared/src/androidMain/res/drawable-nodpi/build_perk_NNN.webp`（缺的那些）
- `shared/src/commonMain/.../data/build/BuildCatalog.kt`（目录数据）
- `shared/src/androidMain/.../ui/component/BuildArt.android.kt`（名称 -> drawable）

用法：
    python tools/gen_build_data.py [wiki 根目录]
"""

import os
import re
import sys

from PIL import Image

DEFAULT_WIKI = r"C:\Users\Perpetual Memories\AppData\Local\Temp\nzmwiki\nzm-wiki-main"

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(PROJECT, "shared", "src", "androidMain", "res", "drawable-nodpi")
COMMON_DIR = os.path.join(
    PROJECT, "shared", "src", "commonMain", "kotlin", "com", "nzd", "antigravitypanel",
)
ANDROID_DIR = os.path.join(
    PROJECT, "shared", "src", "androidMain", "kotlin", "com", "nzd", "antigravitypanel",
)

# 展示尺寸：武器卡里最大 85px、插件格 42px，按 3x 屏留余量。和 gen_equipment_art.py 一致。
WEAPON_WIDTH = 320
PERK_SIZE = 128
QUALITY = 80

USE_TYPE_ENUM = {
    "主武器": "MAIN",
    "副武器": "SIDE",
    "近战武器": "MELEE",
}


def read_frontmatter(path):
    """只取 frontmatter 里的**顶层** `key: value`（嵌套的列表/映射不解析）。"""
    with open(path, encoding="utf-8") as fp:
        text = fp.read()
    match = re.match(r"^---\r?\n(.*?)\r?\n---", text, re.S)
    if not match:
        return {}
    out = {}
    for line in match.group(1).split("\n"):
        entry = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$", line.strip())
        if entry:
            out[entry.group(1)] = entry.group(2).strip().strip('"')
    return out


def existing_art_map(prefix):
    """读 `EquipmentArt.android.kt`，拿回「物品名 -> 已有资源名」。"""
    path = os.path.join(ANDROID_DIR, "ui", "component", "EquipmentArt.android.kt")
    with open(path, encoding="utf-8") as fp:
        source = fp.read()
    segment = source.split(
        "actual fun %sPainter(name: String): Painter? {" % prefix
    )[1].split("\n}")[0]
    return {
        name: res
        for name, res in re.findall(r'"((?:[^"\\]|\\.)*)" -> R\.drawable\.(\w+)', segment)
    }


def clean_previous(res_dir):
    """删掉上一次生成的图。

    不清理的话，条目变少时（比如去重后从 316 张降到 295 张）会留下一批
    `build_perk_296..316.webp` 孤儿文件 —— Android 会把 res 目录下的文件**全部**打进包，
    它们没人引用，纯粹占体积。
    """
    removed = 0
    if not os.path.isdir(res_dir):
        return removed
    for name in os.listdir(res_dir):
        if name.startswith(("build_weapon_", "build_perk_")) and name.endswith(".webp"):
            os.remove(os.path.join(res_dir, name))
            removed += 1
    return removed


def image_files(directory):
    """目录里的图片文件（不含子目录）-> 「文件名（去扩展名）: 完整路径」。"""
    if not os.path.isdir(directory):
        return {}
    return {
        os.path.splitext(name)[0]: os.path.join(directory, name)
        for name in os.listdir(directory)
        if os.path.isfile(os.path.join(directory, name))
        and name.lower().endswith((".png", ".webp", ".jpg", ".jpeg"))
    }


def convert(src, dst, width, height):
    with Image.open(src) as im:
        if im.mode not in ("RGB", "RGBA"):
            im = im.convert("RGBA")
        if height is not None:
            im = im.resize((width, height), Image.LANCZOS)
        else:
            ratio = width / im.width
            im = im.resize((width, max(1, round(im.height * ratio))), Image.LANCZOS)
        im.save(dst, "WEBP", quality=QUALITY, method=4)
    return os.path.getsize(dst)


def kotlin_literal(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def collect_weapons(wiki, art_map, res_dir):
    """返回 [(name, enum_name, type, rarity, drawable_or_None)]。"""
    directory = os.path.join(wiki, "data", "weapons")
    normal = image_files(os.path.join(wiki, "public", "icons", "weapons", "normal"))
    large = image_files(os.path.join(wiki, "public", "icons", "weapons", "large"))

    rows = []
    for filename in sorted(os.listdir(directory)):
        if not filename.endswith(".mdx"):
            continue
        info = read_frontmatter(os.path.join(directory, filename))
        name = info.get("title") or filename[:-4]
        use_type = info.get("use_type", "")
        rows.append(
            (
                name,
                USE_TYPE_ENUM.get(use_type, "MAIN"),
                info.get("weapon_type") or use_type or "武器",
                info.get("rarity", ""),
            )
        )

    # 按名字去重：武器名就是计划条目的 id，重复会让列表 key 撞车
    seen = set()
    out = []
    added = 0
    dropped = 0
    for name, enum_name, weapon_type, rarity in sorted(rows, key=lambda r: r[0]):
        if name in seen:
            dropped += 1
            continue
        seen.add(name)
        drawable = art_map.get(name)
        if drawable is None:
            src = normal.get(name) or large.get(name)
            if src is not None:
                added += 1
                drawable = "build_weapon_%03d" % added
                convert(
                    src,
                    os.path.join(res_dir, drawable + ".webp"),
                    WEAPON_WIDTH,
                    None,
                )
        out.append((name, enum_name, weapon_type, rarity, drawable))
    return out, added, dropped


def collect_perks(wiki, art_map, res_dir):
    """
    返回 [(name, slot, rarity, drawable_or_None)]，一个名字可能占多个槽位。

    **同一个 (名字, 槽位) 只留一条。** wiki 的 slot 目录里存在同名多文件
    （文件名形如 `伤害属性-20703040455.mdx`，即"同一个插件的不同数值档位"），
    slot-3 的「伤害属性」就有 4 份、`武器驱动装置` 有 7 份 ——
    它们 rarity / icon / description 全都一样，只有内部 id 和数值行不同。

    不去重的后果是列表 key 撞车直接崩（`Key "伤害属性#3" was already used`）；
    即便不崩，界面上也是同名字同图标连着排好几行，点了存进去的还是同一个名字，
    对用户没有任何意义。

    唯一的例外是 slot-2 的「连锁充能」：两份的 rarity 和 icon 真不一样，
    属于"名字撞了的两个插件"。但本功能只记**名字**，选哪个存进去都一样，
    所以同样只留一条，取排序后的第一个（结果稳定，重跑不会变）。
    """
    icons = image_files(os.path.join(wiki, "public", "icons", "perks"))

    # 按 (名字, 槽位) 去重，sorted 保证"留哪条"是确定的
    unique = {}
    dropped = 0
    for slot in (1, 2, 3, 4):
        directory = os.path.join(wiki, "data", "perks", "slot-%d" % slot)
        if not os.path.isdir(directory):
            continue
        for filename in sorted(os.listdir(directory)):
            if not filename.endswith(".mdx"):
                continue
            info = read_frontmatter(os.path.join(directory, filename))
            name = info.get("title") or filename[:-4]
            # 插件名里混进了零宽字符（wiki 的 perks.json 就有），会同时毁掉搜索和查图
            name = name.replace("\u200b", "").strip()
            if not name:
                continue
            raw_rarity = info.get("rarity", "")
            rarity = int(raw_rarity) if raw_rarity.isdigit() else 0
            if (name, slot) in unique:
                dropped += 1
                continue
            unique[(name, slot)] = (rarity, info.get("icon", ""))

    out = []
    added = 0
    for name, slot in sorted(unique):
        rarity, icon = unique[(name, slot)]
        drawable = art_map.get(name)
        if drawable is None and icon in icons:
            added += 1
            drawable = "build_perk_%03d" % added
            convert(icons[icon], os.path.join(res_dir, drawable + ".webp"), PERK_SIZE, PERK_SIZE)
        out.append((name, slot, rarity, drawable))
    return out, added, dropped


def write_catalog(weapons, perks):
    lines = [
        "package com.nzd.antigravitypanel.data.build",
        "",
        "/**",
        " * Build 计划的武器 / 插件目录。",
        " *",
        " * **由 `tools/gen_build_data.py` 生成，别手改** —— 改了下次跑脚本就冲掉了。",
        " *",
        " * 数据来自 nzm-wiki（`https://github.com/lostlightll/nzm-wiki`）：",
        # 别在这里写 `*.mdx`：Kotlin 的块注释是**可嵌套**的，路径里的 `/*`
        # 会开出一个没有结尾的注释，整个文件直接解析失败（编译报
        # "Unclosed comment"，而且同一文件里所有声明都变成 unresolved）
        " * 武器取 `data/weapons/` 下的 mdx，插件取 `data/perks/slot-1` 到 `slot-4` 下的 mdx。",
        " * 不用 wiki 的 `data/perks/perks.json`：那份里混着零宽字符 U+200B，",
        " * 200 个名字只有 66 个能和官方图集对上。",
        " *",
        " * 图片是**另一套**（`BuildArt`），和配装那套 `weaponPainter` / `pluginPainter`",
        " * 分开：这边要的是 wiki 全量（528 个插件），配装那边只要官方下发过的那批。",
        " */",
        "val BUILD_WEAPONS: List<BuildWeapon> = listOf(",
    ]
    for name, enum_name, weapon_type, rarity, _ in weapons:
        lines.append(
            "    BuildWeapon(%s, WeaponSlot.%s, %s, %s),"
            % (
                kotlin_literal(name),
                enum_name,
                kotlin_literal(weapon_type),
                kotlin_literal(rarity),
            )
        )
    lines.append(")")
    lines.append("")
    lines.append("val BUILD_PERKS: List<BuildPerk> = listOf(")
    for name, slot, rarity, _ in perks:
        lines.append("    BuildPerk(%s, %d, %d)," % (kotlin_literal(name), slot, rarity))
    lines.append(")")

    path = os.path.join(COMMON_DIR, "data", "build", "BuildCatalog.kt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fp:
        fp.write("\n".join(lines) + "\n")
    return path


def write_painters(weapons, perks):
    lines = [
        "package com.nzd.antigravitypanel.ui.component",
        "",
        "import androidx.compose.runtime.Composable",
        "import androidx.compose.ui.graphics.painter.Painter",
        "import androidx.compose.ui.res.painterResource",
        "import com.nzd.antigravitypanel.shared.R",
        "",
        "/**",
        " * Build 计划图片的 Android 实现。",
        " *",
        " * **由 `tools/gen_build_data.py` 生成，别手改。**",
        " *",
        " * 名字在包里已有图（配装那批 `weapon_NNN` / `plugin_NNN`）时**直接复用**，",
        " * 只有对不上的才用脚本补出来的 `build_weapon_NNN` / `build_perk_NNN`。",
        " * 复用是为了不把同一张图打包两遍 —— 两批源图都是游戏内同一套美术。",
        " */",
    ]
    for fun, getter, rows in (
        ("buildWeaponPainter", "weapon", ((r[0], r[4]) for r in weapons)),
        ("buildPerkPainter", "perk", ((r[0], r[3]) for r in perks)),
    ):
        lines.append("@Composable")
        lines.append("actual fun %s(name: String): Painter? {" % fun)
        lines.append("    val id = when (name) {")
        emitted = set()
        for name, drawable in rows:
            if drawable is None or name in emitted:
                continue
            emitted.add(name)
            lines.append("        %s -> R.drawable.%s" % (kotlin_literal(name), drawable))
        lines.append("        else -> return null")
        lines.append("    }")
        lines.append("    return painterResource(id = id)")
        lines.append("}")
        lines.append("")

    path = os.path.join(ANDROID_DIR, "ui", "component", "BuildArt.android.kt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fp:
        fp.write("\n".join(lines))


def main():
    wiki = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_WIKI
    if not os.path.isdir(wiki):
        print("wiki 目录不存在：%s" % wiki)
        return 1

    weapon_art = existing_art_map("weapon")
    plugin_art = existing_art_map("plugin")
    print("已有武器图 %d 张，插件图 %d 张" % (len(weapon_art), len(plugin_art)))

    stale = clean_previous(RES_DIR)
    if stale:
        print("清掉上一次生成的 %d 张孤儿图" % stale)

    weapons, weapon_added, weapon_dropped = collect_weapons(wiki, weapon_art, RES_DIR)
    perks, perk_added, perk_dropped = collect_perks(wiki, plugin_art, RES_DIR)

    no_art = [name for name, _, _, _, art in weapons if art is None]
    print(
        "武器 %d 把（补图 %d，复用 %d，无图 %d，去重丢弃 %d）：%s"
        % (
            len(weapons),
            weapon_added,
            len(weapons) - weapon_added - len(no_art),
            len(no_art),
            weapon_dropped,
            no_art,
        )
    )
    perk_names = {name for name, _, _, _ in perks}
    print(
        "插件 %d 条 / %d 个名字（补图 %d，去重丢弃 %d）"
        % (len(perks), len(perk_names), perk_added, perk_dropped)
    )

    print("写出 " + write_catalog(weapons, perks))
    write_painters(weapons, perks)
    print("写出 BuildArt.android.kt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
