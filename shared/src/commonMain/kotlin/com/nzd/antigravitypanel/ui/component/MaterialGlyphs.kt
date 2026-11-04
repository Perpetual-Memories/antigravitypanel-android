package com.nzd.antigravitypanel.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 项目里用到的几个 Material 图标（filled）。
 *
 * ⚠️ **为什么不加 `material-icons-extended` 依赖**：
 * 那一包是几千个图标，dex 直接涨好几 MB，而我们只用到六个。
 * 这里把 path 本身抄出来，和 [com.nzd.antigravitypanel.ui.wiki.AutoStoriesIcon] 同一个思路。
 *
 * 路径不是手写的：从本机 Gradle 缓存里的 `material-icons-extended` 1.7.8
 * （`BusinessCenterKt` / `ScoreboardKt` / `SaveKt` / `CalculateKt` / `ComputerKt`）
 * 反查字节码里的 `PathBuilder` 调用还原出来，坐标和官方 24dp 图标逐条一致。
 * 转换脚本见工作区 `_bytecode2compose.py`。
 *
 * GitHub 那颗是例外：Material Symbols **没有品牌图标**（Google 2019 年把品牌图标
 * 全撤了），所以用 GitHub 官方的 mark（Simple Icons 收录的那份，CC0）——
 * 同样只抄一条 path，视觉上就是 Material 那颗 24dp 实心图标的口径。
 *
 * ⚠️ 用 `by lazy`：ImageVector 要过一遍 path 解析，放字段初始化里会在类加载时就跑。
 */
object MaterialGlyphs {

    /** `business_center`：公文包。概览页顶栏「每日首胜宝箱」用。 */
    val BusinessCenter: ImageVector by lazy {
        buildMaterialIcon("BusinessCenter") {
            moveTo(10f, 16f)
            verticalLineToRelative(-1f)
            lineTo(3.01f, 15f)
            lineTo(3f, 19f)
            curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(-7f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(-4f)
            close()
            moveTo(20f, 7f)
            horizontalLineToRelative(-4.01f)
            lineTo(15.99f, 5f)
            lineToRelative(-2f, -2f)
            horizontalLineToRelative(-4f)
            lineToRelative(-2f, 2f)
            verticalLineToRelative(2f)
            lineTo(4f, 7f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(3f)
            curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(22f, 9f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(14f, 7f)
            horizontalLineToRelative(-4f)
            lineTo(10f, 5f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            close()
        }
    }

    /** `scoreboard`：记分牌。设置页「积分显示单位」用。 */
    val Scoreboard: ImageVector by lazy {
        buildMaterialIcon("Scoreboard") {
            moveTo(17.5f, 13.5f)
            horizontalLineTo(16f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(1.5f)
            verticalLineTo(13.5f)
            close()
            moveTo(20f, 4f)
            horizontalLineToRelative(-3f)
            verticalLineTo(2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineTo(9f)
            verticalLineTo(2f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f)
            curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
            close()
            moveTo(9.5f, 11.5f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(3f)
            verticalLineTo(15f)
            horizontalLineTo(5f)
            verticalLineToRelative(-2.5f)
            curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-1f)
            horizontalLineTo(5f)
            verticalLineTo(9f)
            horizontalLineToRelative(3.5f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
            verticalLineTo(11.5f)
            close()
            moveTo(12.75f, 18f)
            horizontalLineToRelative(-1.5f)
            verticalLineToRelative(-1.5f)
            horizontalLineToRelative(1.5f)
            verticalLineTo(18f)
            close()
            moveTo(12.75f, 14.5f)
            horizontalLineToRelative(-1.5f)
            verticalLineTo(13f)
            horizontalLineToRelative(1.5f)
            verticalLineTo(14.5f)
            close()
            moveTo(12.75f, 11f)
            horizontalLineToRelative(-1.5f)
            verticalLineTo(9.5f)
            horizontalLineToRelative(1.5f)
            verticalLineTo(11f)
            close()
            moveTo(12.75f, 7.5f)
            horizontalLineToRelative(-1.5f)
            verticalLineTo(6f)
            horizontalLineToRelative(1.5f)
            verticalLineTo(7.5f)
            close()
            moveTo(19f, 14f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
            horizontalLineToRelative(-2.5f)
            curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
            verticalLineToRelative(-4f)
            curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
            horizontalLineTo(18f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
            verticalLineTo(14f)
            close()
        }
    }

    /** `save`：软盘。设置页「本地保留」用。 */
    val Save: ImageVector by lazy {
        buildMaterialIcon("Save") {
            moveTo(17f, 3f)
            lineTo(5f, 3f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(21f, 7f)
            lineToRelative(-4f, -4f)
            close()
            moveTo(12f, 19f)
            curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
            reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
            reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
            close()
            moveTo(15f, 9f)
            lineTo(5f, 9f)
            lineTo(5f, 5f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(4f)
            close()
        }
    }

    /** `calculate`：计算器。设置页「用综合分替换经济转化」用。 */
    val Calculate: ImageVector by lazy {
        buildMaterialIcon("Calculate") {
            moveTo(19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            moveTo(13.03f, 7.06f)
            lineTo(14.09f, 6f)
            lineToRelative(1.41f, 1.41f)
            lineTo(16.91f, 6f)
            lineToRelative(1.06f, 1.06f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(1.41f, 1.41f)
            lineToRelative(-1.06f, 1.06f)
            lineTo(15.5f, 9.54f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(-1.06f, -1.06f)
            lineToRelative(1.41f, -1.41f)
            lineTo(13.03f, 7.06f)
            close()
            moveTo(6.25f, 7.72f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(1.5f)
            horizontalLineToRelative(-5f)
            verticalLineTo(7.72f)
            close()
            moveTo(11.5f, 16f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineTo(8f)
            verticalLineToRelative(-2f)
            horizontalLineTo(6f)
            verticalLineToRelative(-1.5f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineTo(16f)
            close()
            moveTo(18f, 17.25f)
            horizontalLineToRelative(-5f)
            verticalLineToRelative(-1.5f)
            horizontalLineToRelative(5f)
            verticalLineTo(17.25f)
            close()
            moveTo(18f, 14.75f)
            horizontalLineToRelative(-5f)
            verticalLineToRelative(-1.5f)
            horizontalLineToRelative(5f)
            verticalLineTo(14.75f)
            close()
        }
    }

    /** `computer`：显示器。设置页「下载 PC 端」用。 */
    val Computer: ImageVector by lazy {
        buildMaterialIcon("Computer") {
            moveTo(20f, 18f)
            curveToRelative(1.1f, 0f, 1.99f, -0.9f, 1.99f, -2f)
            lineTo(22f, 6f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineTo(0f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(24f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-4f)
            close()
            moveTo(4f, 6f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(10f)
            horizontalLineTo(4f)
            verticalLineTo(6f)
            close()
        }
    }

    /**
     * GitHub 的 octocat 剪影。
     *
     * ⚠️ 这条 path 是 Simple Icons 里那份（CC0），**不是** Material Symbols ——
     * Google 的图标集里没有品牌图标。视觉口径仍是 24dp 实心图标。
     */
    val GitHub: ImageVector by lazy {
        buildMaterialIcon("GitHub") {
            moveTo(12f, 0.297f)
            curveToRelative(-6.63f, 0f, -12f, 5.373f, -12f, 12f)
            curveToRelative(0f, 5.303f, 3.438f, 9.8f, 8.205f, 11.385f)
            curveToRelative(0.6f, 0.113f, 0.82f, -0.258f, 0.82f, -0.577f)
            curveToRelative(0f, -0.285f, -0.01f, -1.04f, -0.015f, -2.04f)
            curveToRelative(-3.338f, 0.724f, -4.042f, -1.61f, -4.042f, -1.61f)
            curveTo(4.422f, 18.07f, 3.633f, 17.7f, 3.633f, 17.7f)
            curveToRelative(-1.087f, -0.744f, 0.084f, -0.729f, 0.084f, -0.729f)
            curveToRelative(1.205f, 0.084f, 1.838f, 1.236f, 1.838f, 1.236f)
            curveToRelative(1.07f, 1.835f, 2.809f, 1.305f, 3.495f, 0.998f)
            curveToRelative(0.108f, -0.776f, 0.417f, -1.305f, 0.76f, -1.605f)
            curveToRelative(-2.665f, -0.3f, -5.466f, -1.332f, -5.466f, -5.93f)
            curveToRelative(0f, -1.31f, 0.465f, -2.38f, 1.235f, -3.22f)
            curveToRelative(-0.135f, -0.303f, -0.54f, -1.523f, 0.105f, -3.176f)
            curveToRelative(0f, 0f, 1.005f, -0.322f, 3.3f, 1.23f)
            curveToRelative(0.96f, -0.267f, 1.98f, -0.399f, 3f, -0.405f)
            curveToRelative(1.02f, 0.006f, 2.04f, 0.138f, 3f, 0.405f)
            curveToRelative(2.28f, -1.552f, 3.285f, -1.23f, 3.285f, -1.23f)
            curveToRelative(0.645f, 1.653f, 0.24f, 2.873f, 0.12f, 3.176f)
            curveToRelative(0.765f, 0.84f, 1.23f, 1.91f, 1.23f, 3.22f)
            curveToRelative(0f, 4.61f, -2.805f, 5.625f, -5.475f, 5.92f)
            curveToRelative(0.42f, 0.36f, 0.81f, 1.096f, 0.81f, 2.22f)
            curveToRelative(0f, 1.606f, -0.015f, 2.896f, -0.015f, 3.286f)
            curveToRelative(0f, 0.315f, 0.21f, 0.69f, 0.825f, 0.57f)
            curveTo(20.565f, 22.092f, 24f, 17.592f, 24f, 12.297f)
            curveToRelative(0f, -6.627f, -5.373f, -12f, -12f, -12f)
        }
    }
}

/**
 * 24dp / 24 视口的实心图标骨架。
 *
 * 六个图标都是同一个口径（Material 的默认规格），单独抽出来免得每处都抄一遍
 * 四种 dp 参数；`name` 只影响调试时 toString 的输出。
 */
private fun buildMaterialIcon(
    name: String,
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(fill = SolidColor(Color.Black)) { block() }.build()
