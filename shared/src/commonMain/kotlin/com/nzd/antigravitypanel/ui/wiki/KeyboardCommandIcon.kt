package com.nzd.antigravitypanel.ui.wiki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Material Symbols 的「⌘」图标（`keyboard_command_key`，outlined）。
 *
 * 就是站内顶栏「命令面板」那颗按钮的图形 —— 网页那边用的是 lucide 的 `Command`，
 * 同一个 ⌘，所以照抄这个形状语义上最贴。
 *
 * ⚠️ **为什么手抄**：`miuix-icons` 那 156 个图标里只有 `Search`，没有任何
 * 命令面板类的图形。为一个图标拖进整套 Material 图标库不划算
 * （同 [AutoStoriesIcon] 的理由），这里只抄 path 本身 ——
 * 素材来自 Google Material Symbols（Apache-2.0），版权归原作者。
 *
 * ⚠️⚠️ **六段子路径必须在同一个 `.path {}` 里**。原文就是一个 `<path>`：
 * 外框 + 四个内环 + 中心方孔，靠**一条 path 内部**的非零环绕规则挖空。
 * 拆成多个 `.path()` 会各自独立填充 —— 结果是一块实心黑方块
 * （顶栏那颗方块就是这么来的，见 [AutoStoriesIcon] 的注释）。
 *
 * ⚠️ 坐标系：Material Symbols 的 viewBox 是 `0 -960 960 960`（y 朝上、原点在底边），
 * `ImageVector` 的 viewport 却是 `0 0 w h`，所以绝对命令的 y 统一加了 960。
 * 转换脚本见工作区 `_svg2compose.py`。
 *
 * ⚠️ `by lazy`：构建 ImageVector 要走一遍 path 解析，放字段初始化会在类加载时就跑，
 * 冷启动白搭一段；而且这个图标只在 wiki 二级页才会用到。
 */
val KeyboardCommandIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "keyboard_command_key",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    )
        .path(fill = SolidColor(Color.Black)) {
            // ---- 子路径 1：⌘ 的骨架（四个环的外轮廓 + 中间方孔）----
            moveTo(260f, 840f)
            quadToRelative(-58f, 0f, -99f, -41f)
            reflectiveQuadToRelative(-41f, -99f)
            quadToRelative(0f, -58f, 41f, -99f)
            reflectiveQuadToRelative(99f, -41f)
            horizontalLineToRelative(60f)
            verticalLineToRelative(-160f)
            horizontalLineToRelative(-60f)
            quadToRelative(-58f, 0f, -99f, -41f)
            reflectiveQuadToRelative(-41f, -99f)
            quadToRelative(0f, -58f, 41f, -99f)
            reflectiveQuadToRelative(99f, -41f)
            quadToRelative(58f, 0f, 99f, 41f)
            reflectiveQuadToRelative(41f, 99f)
            verticalLineToRelative(60f)
            horizontalLineToRelative(160f)
            verticalLineToRelative(-60f)
            quadToRelative(0f, -58f, 41f, -99f)
            reflectiveQuadToRelative(99f, -41f)
            quadToRelative(58f, 0f, 99f, 41f)
            reflectiveQuadToRelative(41f, 99f)
            quadToRelative(0f, 58f, -41f, 99f)
            reflectiveQuadToRelative(-99f, 41f)
            horizontalLineToRelative(-60f)
            verticalLineToRelative(160f)
            horizontalLineToRelative(60f)
            quadToRelative(58f, 0f, 99f, 41f)
            reflectiveQuadToRelative(41f, 99f)
            quadToRelative(0f, 58f, -41f, 99f)
            reflectiveQuadToRelative(-99f, 41f)
            quadToRelative(-58f, 0f, -99f, -41f)
            reflectiveQuadToRelative(-41f, -99f)
            verticalLineToRelative(-60f)
            horizontalLineTo(400f)
            verticalLineToRelative(60f)
            quadToRelative(0f, 58f, -41f, 99f)
            reflectiveQuadToRelative(-99f, 41f)
            close()
            // ---- 子路径 2：左下那个环的内圈 ----
            moveToRelative(0f, -80f)
            quadToRelative(25f, 0f, 42.5f, -17.5f)
            reflectiveQuadTo(320f, 700f)
            verticalLineToRelative(-60f)
            horizontalLineToRelative(-60f)
            quadToRelative(-25f, 0f, -42.5f, 17.5f)
            reflectiveQuadTo(200f, 700f)
            quadToRelative(0f, 25f, 17.5f, 42.5f)
            reflectiveQuadTo(260f, 760f)
            close()
            // ---- 子路径 3：右下那个环的内圈 ----
            moveToRelative(440f, 0f)
            quadToRelative(25f, 0f, 42.5f, -17.5f)
            reflectiveQuadTo(760f, 700f)
            quadToRelative(0f, -25f, -17.5f, -42.5f)
            reflectiveQuadTo(700f, 640f)
            horizontalLineToRelative(-60f)
            verticalLineToRelative(60f)
            quadToRelative(0f, 25f, 17.5f, 42.5f)
            reflectiveQuadTo(700f, 760f)
            close()
            // ---- 子路径 4：中间那个方孔 ----
            moveTo(400f, 560f)
            horizontalLineToRelative(160f)
            verticalLineToRelative(-160f)
            horizontalLineTo(400f)
            verticalLineToRelative(160f)
            close()
            // ---- 子路径 5：左上那个环的内圈 ----
            moveTo(260f, 320f)
            horizontalLineToRelative(60f)
            verticalLineToRelative(-60f)
            quadToRelative(0f, -25f, -17.5f, -42.5f)
            reflectiveQuadTo(260f, 200f)
            quadToRelative(-25f, 0f, -42.5f, 17.5f)
            reflectiveQuadTo(200f, 260f)
            quadToRelative(0f, 25f, 17.5f, 42.5f)
            reflectiveQuadTo(260f, 320f)
            close()
            // ---- 子路径 6：右上那个环的内圈 ----
            moveToRelative(380f, 0f)
            horizontalLineToRelative(60f)
            quadToRelative(25f, 0f, 42.5f, -17.5f)
            reflectiveQuadTo(760f, 260f)
            quadToRelative(0f, -25f, -17.5f, -42.5f)
            reflectiveQuadTo(700f, 200f)
            quadToRelative(-25f, 0f, -42.5f, 17.5f)
            reflectiveQuadTo(640f, 260f)
            verticalLineToRelative(60f)
            close()
        }
        .build()
}
