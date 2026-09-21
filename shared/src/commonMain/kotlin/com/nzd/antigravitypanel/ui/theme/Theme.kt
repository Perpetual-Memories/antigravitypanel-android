package com.nzd.antigravitypanel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * 主题模式。数值与设置页下拉顺序绑定，勿随意调整。
 *
 * 0 跟随系统 / 1 浅色 / 2 深色 / 3 Monet 跟随系统 / 4 Monet 浅色 / 5 Monet 深色
 */
object ColorMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
    const val MONET_SYSTEM = 3
    const val MONET_LIGHT = 4
    const val MONET_DARK = 5

    /** 顺序即设置页下拉顺序，下标必须与上面的常量值对上。 */
    val LABELS = listOf(
        "跟随系统",
        "浅色",
        "深色",
        "Monet 跟随系统",
        "Monet 浅色",
        "Monet 深色",
    )
}

val LocalColorMode = compositionLocalOf { ColorMode.SYSTEM }

/** 由 [ColorMode] 推导当前是否深色，用于系统栏配色与液态玻璃高光。 */
fun resolveDarkTheme(colorMode: Int, systemDark: Boolean): Boolean = when (colorMode) {
    ColorMode.LIGHT, ColorMode.MONET_LIGHT -> false
    ColorMode.DARK, ColorMode.MONET_DARK -> true
    else -> systemDark
}

@Composable
fun AppTheme(
    colorMode: Int = ColorMode.SYSTEM,
    keyColor: Color? = null,
    paletteStyle: Int = 0,
    colorSpec: Int = 0,
    content: @Composable () -> Unit,
) {
    val spec = ThemeColorSpec.entries.getOrNull(colorSpec) ?: ThemeColorSpec.Spec2021
    val style = ThemePaletteStyle.entries.getOrNull(paletteStyle) ?: ThemePaletteStyle.Content
    val controller = remember(colorMode, keyColor, spec, style) {
        when (colorMode) {
            ColorMode.LIGHT -> ThemeController(ColorSchemeMode.Light)
            ColorMode.DARK -> ThemeController(ColorSchemeMode.Dark)
            ColorMode.MONET_SYSTEM -> ThemeController(
                ColorSchemeMode.MonetSystem,
                keyColor = keyColor,
                colorSpec = spec,
                paletteStyle = style,
            )

            ColorMode.MONET_LIGHT -> ThemeController(
                ColorSchemeMode.MonetLight,
                keyColor = keyColor,
                colorSpec = spec,
                paletteStyle = style,
            )

            ColorMode.MONET_DARK -> ThemeController(
                ColorSchemeMode.MonetDark,
                keyColor = keyColor,
                colorSpec = spec,
                paletteStyle = style,
            )

            else -> ThemeController(ColorSchemeMode.System)
        }
    }
    CompositionLocalProvider(LocalColorMode provides colorMode) {
        MiuixTheme(controller = controller, content = content)
    }
}

/** 当前是否处于深色主题。液态玻璃组件依赖它选择高光配色。 */
@Composable
fun isInDarkTheme(): Boolean = resolveDarkTheme(LocalColorMode.current, isSystemInDarkTheme())
