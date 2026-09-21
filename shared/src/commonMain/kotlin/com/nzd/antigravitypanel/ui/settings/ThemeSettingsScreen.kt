package com.nzd.antigravitypanel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.theme.ColorMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 主题的二级详情页。
 *
 * 结构是 HyperIsland `ThemeSettingsPage` 的还原：
 * 顶部一圈 `TabRow`（跟随系统 / 浅色 / 深色）选主题模式，下面是分 Card 的开关组。
 * 条目图标和间距沿用一级设置页的同一套（黑图标 + 18dp×14dp），二级页不该换一套视觉。
 *
 * 和 HyperIsland 的对应关系：
 * - TabRow 三档 —— 同。我们把 Monet 做成下面的开关而不是第二排 Tab，
 *   因为 HyperIsland 的 monet 与 theme mode 是两个独立开关（combined = 6 种组合），
 *   正好等于我们 [ColorMode] 已有的 6 个取值（0/1/2 + 3/4/5），所以这里只是换了个输入方式，
 *   存的值完全没变。
 * - 「Monet 颜色」—— 同。
 * - 「主题色」自定义取色 dialog —— **没照搬**。HyperIsland 有自己的 seed color 主题管线，
 *   我们的配色是 miuix 的 `ColorSchemeMode.Monet*`，颜色由系统取，没有自定义种子色这一层。
 *   摆一个取色器而它什么都不改，比不摆更糟。
 * - 「悬浮底栏 / 液态玻璃底栏」—— 合成了「液态玻璃底栏」一个开关，
 *   我们的底栏只有"液态玻璃"和"不透明胶囊"两种形态，没有独立的悬浮与否开关。
 * - 「模糊」—— 合并进「液态玻璃底栏」（磨砂就是它的一部分）。
 * - 「预测返回距离」—— 同。`PredictiveNavLayer` 的 `maxTranslationPercent` 本来就是
 *   照 HyperIsland 移植的，这里只是终于把它接到设置上。
 *
 * @param predictiveBackTranslation 预测返回时二级页的最大横移距离（百分比），
 *   取值 0..100，默认 75。
 */
@Composable
fun ThemeSettingsScreen(
    colorMode: Int,
    liquidGlassEnabled: Boolean,
    predictiveBackTranslation: Int,
    onColorModeChange: (Int) -> Unit,
    onLiquidGlassChange: (Boolean) -> Unit,
    onPredictiveBackTranslationChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // Monet 开关在当时的 Tab 基础上平移三位：0/1/2 是普通三档，3/4/5 是它们的 Monet 版本
    val monetEnabled = colorMode >= ColorMode.MONET_SYSTEM
    val baseMode = if (monetEnabled) colorMode - ColorMode.MONET_SYSTEM else colorMode
    val scrollBehavior = MiuixScrollBehavior()

    // 骨架和活动日历的二级页一致：BarBlurHost → Scaffold(BlurredBar + SmallTopAppBar)
    // → BarBackdropContent → 列表。少了中间那层 backdrop，磨砂就只能糊到一层纯色。
    BarBlurHost(enabled = liquidGlassEnabled) {
        Scaffold(
            topBar = {
                BlurredBar(topGradient = true) {
                    SmallTopAppBar(
                        title = "主题",
                        // 透明是必须的：底下 BlurredBar 已经在采样列表内容做模糊了，
                        // 这里再铺一层表面色就把它盖死
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "返回",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        // 顶栏是浮在内容上的：不给 top padding，TabRow 会被顶栏压住
                        top = 12.dp + innerPadding.calculateTopPadding(),
                        bottom = 16.dp + innerPadding.calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
            item(key = "mode") {
                TabRow(
                    tabs = listOf("跟随系统", "浅色", "深色"),
                    selectedTabIndex = baseMode.coerceIn(0, 2),
                    onTabSelected = { index ->
                        onColorModeChange(if (monetEnabled) index + ColorMode.MONET_SYSTEM else index)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "monet") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "Monet 颜色",
                        summary = "使用所选颜色生成软件配色",
                        startAction = { ThemeIcon(MiuixIcons.Theme) },
                        insideMargin = SettingsItemMargin,
                        checked = monetEnabled,
                        onCheckedChange = { enabled ->
                            onColorModeChange(if (enabled) baseMode + ColorMode.MONET_SYSTEM else baseMode)
                        },
                    )
                }
            }

            item(key = "bars") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "液态玻璃底栏",
                        summary = "关闭开关后，底栏材质将由液态玻璃转为无模糊、不透明效果，能一定程度降低功耗",
                        startAction = { ThemeIcon(MiuixIcons.Layers) },
                        insideMargin = SettingsItemMargin,
                        checked = liquidGlassEnabled,
                        onCheckedChange = onLiquidGlassChange,
                    )
                    SliderPreference(
                        value = predictiveBackTranslation.toFloat(),
                        onValueChange = { value -> onPredictiveBackTranslationChange(value.toInt()) },
                        title = "预测返回距离",
                        summary = "调整返回手势中页面的最大横移距离",
                        startAction = { ThemeIcon(MiuixIcons.Back) },
                        valueText = "$predictiveBackTranslation%",
                        valueRange = 0f..100f,
                        steps = 19,
                        showKeyPoints = true,
                        keyPoints = listOf(0f, 25f, 50f, 75f, 100f),
                        insideMargin = SettingsItemMargin,
                    )
                }
            }
                }
            }
        }
    }
}

/**
 * 主题的条目图标。tint 是 `onBackground`（深色模式下白、浅色下黑），
 * 和 HyperIsland 的 `SettingsIcon` 一致 —— 图标是条目本身的一部分，不是次级信息。
 */
@Composable
internal fun ThemeIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.padding(end = 16.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}