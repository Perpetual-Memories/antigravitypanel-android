package com.nzd.antigravitypanel.ui.wiki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Material Symbols 的「自动翻书」图标（`auto_stories`，filled）。
 *
 * ⚠️ **为什么手抄一条 path 而不加 `material-icons` 依赖**：
 * 项目图标统一走 `miuix-icons`，它 0.9.4 那 156 个图标里没有任何书本类图标；
 * 为一个图标把整套 Material 图标连同 `Icons` 门面一起拖进包里，
 * 只换来几百 KB 无谓的 dex。这里只抄 path 本身 ——
 * 素材来自 Google Material Symbols（Apache-2.0），版权归原作者。
 *
 * ⚠️⚠️ **两个子路径必须在同一个 `.path {}` 里**（原文就是一个 `<path>`）。
 * 矢量图标里每个 `path` 是**各自独立填充**的，非零环绕规则只在**同一条 path 内部**
 * 生效 —— 之前那版把书壳和书签拆成两个 `.path()`，结果外框实心、书签也被吞，
 * 整体就是一块黑的圆角矩形（顶栏那颗方块就是这么来的）。
 * 合成一条 path 之后，叠加区域的绕向关系才和原图一致。
 *
 * ⚠️ 坐标系：Material Symbols 的 viewBox 是 `0 -960 960 960`（y 轴朝上、原点在底边），
 * 而 `ImageVector` 的 viewport 是 `0 0 w h`。所以抄过来时给**绝对命令的 y** 统一加了
 * 960；相对命令的增量不受平移影响，原样保留。转换脚本见工作区 `_svg2compose.py`。
 *
 * ⚠️ 用 `by lazy` 而不是 `val`：构建 ImageVector 要走一遍 path 解析，
 * 放在字段初始化里会在**类加载**时就跑，冷启动白白多一段；
 * 而且用到它之前（比如用户在设置页关掉入口）根本不需要它。
 */
val AutoStoriesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "auto_stories",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    )
        .path(fill = SolidColor(Color.Black)) {
            // ---- 子路径 1：摊开的书（左右两页 + 书脊）----
            moveTo(480f, 800f)
            quadToRelative(-48f, -38f, -104f, -59f)
            reflectiveQuadToRelative(-116f, -21f)
            quadToRelative(-42f, 0f, -82.5f, 11f)
            reflectiveQuadTo(100f, 762f)
            quadToRelative(-21f, 11f, -40.5f, -1f)
            reflectiveQuadTo(40f, 726f)
            verticalLineToRelative(-482f)
            quadToRelative(0f, -11f, 5.5f, -21f)
            reflectiveQuadTo(62f, 208f)
            quadToRelative(46f, -24f, 96f, -36f)
            reflectiveQuadToRelative(102f, -12f)
            quadToRelative(58f, 0f, 113.5f, 15f)
            reflectiveQuadTo(480f, 220f)
            verticalLineToRelative(484f)
            quadToRelative(51f, -32f, 107f, -48f)
            reflectiveQuadToRelative(113f, -16f)
            quadToRelative(36f, 0f, 70.5f, 6f)
            reflectiveQuadToRelative(69.5f, 18f)
            verticalLineToRelative(-480f)
            quadToRelative(15f, 5f, 29.5f, 10.5f)
            reflectiveQuadTo(898f, 208f)
            quadToRelative(11f, 5f, 16.5f, 15f)
            reflectiveQuadToRelative(5.5f, 21f)
            verticalLineToRelative(482f)
            quadToRelative(0f, 23f, -19.5f, 35f)
            reflectiveQuadToRelative(-40.5f, 1f)
            quadToRelative(-37f, -20f, -77.5f, -31f)
            reflectiveQuadTo(700f, 720f)
            quadToRelative(-60f, 0f, -116f, 21f)
            reflectiveQuadToRelative(-104f, 59f)
            close()
            // ---- 子路径 2：右上方被翻起来的那一页 ----
            moveToRelative(80f, -200f)
            verticalLineToRelative(-380f)
            lineToRelative(200f, -200f)
            verticalLineToRelative(400f)
            lineTo(560f, 600f)
            close()
        }
        .build()
}
