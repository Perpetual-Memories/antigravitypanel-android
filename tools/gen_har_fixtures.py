#!/usr/bin/env python3
"""从抓包 HAR 里导出真实响应，生成单元测试用的 Kotlin 夹具。

接口把数字当字符串返回、缺值给空串、昵称头像还要 URL 解码——
这些坑光看代码发现不了，必须拿真实响应跑。夹具固化下来，接口变了测试就会红。

用法：
    python tools/gen_har_fixtures.py <har 文件或目录> [输出 kt]

只挑 iChartId=430662 的数据查询请求，自动跳过 grant / GetAndBindWxOpenid
（那两个是另一套 iChartId=492965，与数据查询无关）。
"""

from __future__ import annotations

import json
import sys
import urllib.parse
from pathlib import Path

# 夹具名 -> (method, 参数过滤, 取 full 响应还是只取 payload)
WANT: dict[str, tuple[str, object, str]] = {
    "RESPONSE_USER_STATS": ("center.user.stats", None, "full"),
    "PAYLOAD_GAME_LIST_P1": (
        "center.user.game.list",
        lambda p: json.loads(p).get("page") == 1,
        "payload",
    ),
    "PAYLOAD_CONFIG_LIST": ("center.config.list", None, "payload"),
    "PAYLOAD_GAME_DETAIL": ("center.game.detail", None, "payload"),
    "PAYLOAD_MAP_STATS": ("center.user.map.stats", None, "payload"),
    "PAYLOAD_COLLECTION_WEAPON": ("collection.weapon.list", None, "payload"),
}

DATA_CHART_ID = "430662"
# Kotlin 原始字符串里表示字面量 $ 的写法
DOLLAR = "${'$'}"


def collect(har_path: Path) -> dict[str, str]:
    found: dict[str, str] = {}
    for entry in har_path_log_entries(har_path):
        if "comm.ams.game.qq.com/ide/" not in entry["request"]["url"]:
            continue
        form = entry["request"].get("postData", {}).get("text") or ""
        fields = dict(urllib.parse.parse_qsl(form, keep_blank_values=True))
        if fields.get("iChartId") != DATA_CHART_ID:
            continue
        for name, (method, cond, kind) in WANT.items():
            if name in found or fields["method"] != method:
                continue
            if cond is not None and not cond(fields.get("param", "{}")):
                continue
            text = entry["response"]["content"]["text"]
            if kind == "full":
                found[name] = text
            else:
                payload = json.loads(text)["jData"]["data"]["data"]
                found[name] = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return found


def har_path_log_entries(har_path: Path):
    har = json.loads(har_path.read_text(encoding="utf-8"))
    return har["log"]["entries"]


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    src = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(
        "shared/src/hostTest/kotlin/com/nzd/antigravitypanel/HarFixtures.kt"
    )

    hars = sorted(src.glob("*.har")) if src.is_dir() else [src]
    found: dict[str, str] = {}
    for har in hars:
        for key, value in collect(har).items():
            found.setdefault(key, value)
        if len(found) == len(WANT):
            break

    missing = [k for k in WANT if k not in found]
    if missing:
        raise SystemExit(f"以下夹具没抓到，换份 HAR 再试：{missing}")

    lines = [
        "package com.nzd.antigravitypanel",
        "",
        "/**",
        " * 真机抓包（Reqable）导出的真实响应，原样固化下来当测试夹具。",
        " * 接口把数字当字符串、缺值给空串，这类坑只有拿真实数据跑才能发现。",
        " *",
        " * 由 tools/gen_har_fixtures.py 生成，不要手改。",
        " */",
        "object HarFixtures {",
    ]
    for key in WANT:
        value = found[key].replace("$", DOLLAR)
        lines.append(f'    const val {key} = """{value}"""')
        lines.append("")
    lines.append("}")

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"写出 {out}（{len(WANT)} 条夹具，{len(out.read_bytes())} 字节）")


if __name__ == "__main__":
    main()
