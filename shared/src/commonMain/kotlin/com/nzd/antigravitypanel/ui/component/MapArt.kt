package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * 地图底图。
 *
 * 素材来自官方 PC 端（`maps-<mapId>.webp`），下载后统一压到 1024px 宽放进
 * `shared/src/androidMain/res/drawable-nodpi/`：卡片最宽也就 400dp，1024px 在 3x 屏上
 * 已经够用，而原图有 3840×2160 / 1.6 MB 的，直接塞进包里一份就要多出 6 MB。
 *
 * 返回可空是因为素材是**跟着地图 id 走的**：官方出了新图而我们的包里还没有对应文件时，
 * 卡片要能退化成纯色底，而不是崩或者画出一块空白。
 */
@Composable
expect fun mapArtPainter(mapId: Int): Painter?

/**
 * 掉落物卡片的品质外框。官方素材只有两张框加一张锁：
 * `传说.webp`（quality >= 4）、`史诗.webp`（quality 2~3）、`锁.webp`（未解锁遮罩）。
 */
enum class DropFrame {
    LEGENDARY,
    EPIC,
    LOCK,
}

@Composable
expect fun dropFramePainter(frame: DropFrame): Painter
