package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * 关于页要用到的两张图：应用图标与开发者头像。
 *
 * 走 expect/actual 而不是 composeResources：这两张图**只有 Android 端需要**，
 * 而 composeResources 会为每个目标都打包一份资源、还要生成一整套访问器。
 * 目前工程只有 android 一个目标，放进 androidMain 的 res 最省事。
 *
 * 资源本体：
 * - `shared/src/androidMain/res/drawable/ic_app.xml`（与启动图标同一个矢量）
 * - `shared/src/androidMain/res/drawable/ic_app_mark.xml`（只有字形，没有白底板）
 * - `shared/src/androidMain/res/drawable/dev_avatar.png`
 */
@Composable
expect fun appIconPainter(): Painter

/**
 * 关于页顶部的 logo mark。HyperIsland 的 `about_logo_mark` 是一张**纯遮罩**图：
 * 图里只有形状没有颜色，颜色是运行期用渐变刷上去的（见 `AboutScreen` 的
 * `colorfulMask` / `textureBlur`）。所以这里的矢量也只留字形、去掉了白底板——
 * 留着底板的话，整块会被刷成一块实心渐变，字形反而看不见。
 */
@Composable
expect fun appMarkPainter(): Painter

@Composable
expect fun developerAvatarPainter(): Painter

/**
 * 概览状态卡右下角那个大号状态符号。
 *
 * 抄的是 HyperIsland 概览页的做法：一张 110dp 的图标压在卡片右下角、被裁掉一角，
 * 用状态色 + 78% 透明度画。所以这里只要形状，颜色由调用方 tint。
 */
enum class StatusGlyph {
    /** 圆环 + 打勾：已识别、已导入 json 这类"能正常用"的状态。 */
    CHECK,

    /** 圆环 + 感叹号：未输入、过期这类需要处理的状态。 */
    ALERT,
}

/** 资源：`shared/src/androidMain/res/drawable/ic_status_check.xml` / `ic_status_alert.xml`。 */
@Composable
expect fun statusGlyphPainter(glyph: StatusGlyph): Painter
