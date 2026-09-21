package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * 配装图片：武器 / 插件。
 *
 * 按**物品名**查（不是 id）：`center.game.detail` 下发的 `equipmentScheme` 里
 * 名字是稳定的、id 反而不一定对得上，官方 PC 端也是拿名字去拼本地图集路径。
 *
 * 可空：官方随时会出新武器，包里没有的那张就退化成只显示名字，
 * 比显示一张裂图或者占位方块好。
 */
@Composable
expect fun weaponPainter(name: String): Painter?

@Composable
expect fun pluginPainter(name: String): Painter?
