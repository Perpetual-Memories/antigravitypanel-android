package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Build 计划的图片：武器 / 插件。
 *
 * 和配装那套 [weaponPainter] / [pluginPainter] **分开**，虽然两边查的都是物品名：
 * 配装只需要"官方下发过的那批"（226 把武器 / 234 个插件），Build 计划要的是
 * wiki 全量（121 把武器 / 528 个插件），两边差得远，合成一个会把配装那批冲掉。
 *
 * 可空：目录里有 3 把武器在 wiki 上就没有图，查不到就退化成只显示名字。
 */
@Composable
expect fun buildWeaponPainter(name: String): Painter?

@Composable
expect fun buildPerkPainter(name: String): Painter?
