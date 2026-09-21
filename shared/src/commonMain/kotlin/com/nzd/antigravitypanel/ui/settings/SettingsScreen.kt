package com.nzd.antigravitypanel.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.settings.UserSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
// 图标是 extended 包里声明在 MiuixIcons 上的扩展属性，所以两行都要：
// 没有 extended.* 那行，MiuixIcons.Import 会 unresolved。
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

data class SettingsUiState(
    val retentionMonths: Int = UserSettings.DEFAULT_RETENTION_MONTHS,
    val autoRefreshMinutes: Int = UserSettings.ON_OPEN,
)

/**
 * 设置。
 *
 * 版式照 HyperIsland 的 `SettingsPage`：每组一个 `SmallTitle` + 一张 `Card`，
 * 条目全部带一个灰色图标、**不带任何说明文字**，选项当前值由控件自己显示。
 *
 * 三处间距直接抄 HyperIsland 的 `MiuixComponents.kt`：
 * - `PagePadding`：整页横向留白，卡片从这里开始，不是顶到屏幕边缘
 * - `SectionTitleMargin`：组标题的内边距，横向值和条目一致，标题才正好和条目文字对齐
 * - `SettingsItemMargin`：条目的内边距 18dp×14dp
 *
 * 图标用 `onSurfaceVariantActions`（miuix 给箭头 / 次要图标的那档灰）——
 * HyperIsland 用的是 `onBackground`（纯黑），这里要的是"退到后面去"的灰。
 *
 * 之前每个条目下面都跟一行说明，想法是"用户不懂就写在旁边"，实际效果是
 * 一屏里半屏是灰字，真正要点的那几个反而被淹了。全都删掉，看不懂的进二级页再说。
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAutoRefreshChange: (Int) -> Unit,
    onRetentionMonthsChange: (Int) -> Unit,
    onClearLocalData: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
    onImportJson: () -> Unit,
    modifier: Modifier = Modifier,
    /** Scaffold 给的顶栏 / 底栏高度。加在滚动容器的上下，内容仍从顶栏底下穿过。 */
    insets: PaddingValues = PaddingValues(0.dp),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 清空本地数据不可撤销，点一下就把几百场对局抹了 —— 必须再确认一次
    var confirmClear by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PagePadding)
                .padding(
                    top = insets.calculateTopPadding(),
                    bottom = insets.calculateBottomPadding(),
                ),
        ) {
            SectionTitle(text = "外观")
            PreferenceCard {
                // 主题单独进二级页（照 HyperIsland：外观 → 主题），
                // 一级页只给入口，具体开关在里面
                ArrowPreference(
                    title = "主题",
                    startAction = { PreferenceIcon(MiuixIcons.Theme) },
                    insideMargin = SettingsItemMargin,
                    onClick = onOpenTheme,
                )
            }

            SectionTitle(text = "数据")
            PreferenceCard {
                OverlaySpinnerPreference(
                    title = "自动刷新",
                    items = UserSettings.AUTO_REFRESH_OPTIONS
                        .map { DropdownItem(UserSettings.autoRefreshLabel(it)) },
                    selectedIndex = UserSettings.AUTO_REFRESH_OPTIONS
                        .indexOf(state.autoRefreshMinutes)
                        .coerceAtLeast(0),
                    startAction = { PreferenceIcon(MiuixIcons.Refresh) },
                    insideMargin = SettingsItemMargin,
                    showValue = true,
                    onSelectedIndexChange = { index ->
                        val minutes = UserSettings.AUTO_REFRESH_OPTIONS.getOrNull(index)
                            ?: UserSettings.ON_OPEN
                        onAutoRefreshChange(minutes)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "已设置为${UserSettings.autoRefreshLabel(minutes)}，" +
                                    "也可以点概览右上角按钮随时手动刷新",
                            )
                        }
                    },
                )
                OverlaySpinnerPreference(
                    title = "本地保留",
                    items = UserSettings.OPTIONS.map { DropdownItem(UserSettings.labelOf(it)) },
                    selectedIndex = UserSettings.OPTIONS.indexOf(state.retentionMonths)
                        .coerceAtLeast(0),
                    startAction = { PreferenceIcon(MiuixIcons.Timer) },
                    insideMargin = SettingsItemMargin,
                    showValue = true,
                    onSelectedIndexChange = { index ->
                        UserSettings.OPTIONS.getOrNull(index)?.let(onRetentionMonthsChange)
                    },
                )
                ArrowPreference(
                    title = "清空本地数据",
                    startAction = { PreferenceIcon(MiuixIcons.Delete) },
                    insideMargin = SettingsItemMargin,
                    onClick = { confirmClear = true },
                )
            }

            // 实验性功能放在最后一组之前：它面向本地调试，不该混进日常操作
            SectionTitle(text = "实验性功能")
            PreferenceCard {
                ArrowPreference(
                    title = "导入数据",
                    startAction = { PreferenceIcon(MiuixIcons.Import) },
                    insideMargin = SettingsItemMargin,
                    onClick = onImportJson,
                )
            }

            // 「关于」永远最后一项：它不是操作，是出口
            SectionTitle(text = "其他")
            PreferenceCard {
                ArrowPreference(
                    title = "关于",
                    startAction = { PreferenceIcon(MiuixIcons.Info) },
                    insideMargin = SettingsItemMargin,
                    onClick = onOpenAbout,
                )
            }

            // 底栏是浮在内容上的，末尾留一块空白免得最后一项被压住
            Spacer(modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp))
        }

        // 三个参数全按位置传：Miuix 的 SnackbarHost 参数名在不同版本间改过，
        // 逐个用名字反而容易被名字坑。
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        ) { data -> Snackbar(data) }
    }

    ClearLocalDataDialog(
        show = confirmClear,
        onDismiss = { confirmClear = false },
        onConfirm = {
            confirmClear = false
            onClearLocalData()
        },
    )
}

/**
 * 清空本地数据的二次确认。
 *
 * 这一步不能省：底下躺的是用户几个月累积的对局，删掉之后只能重新爬，
 * 而它旁边两个条目都是"选个值"的无害操作，误触的概率不低。
 */
@Composable
private fun ClearLocalDataDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "清空本地数据",
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "将删除本机保存的全部对局记录，删除后无法恢复。Cookie 和登录状态不受影响。",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
                onClick = onDismiss,
            ) {
                Text("取消")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                    contentColor = MiuixTheme.colorScheme.onError,
                ),
                onClick = onConfirm,
            ) {
                Text("清空")
            }
        }
    }
}

/** 一组的容器：条目自己带内边距，这里只负责把它们圈成一张卡。 */
@Composable
private fun PreferenceCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

/** 组标题。横向 18dp 不是为了缩进，是为了和条目文字对齐（卡片外还有 16dp 页面留白）。 */
@Composable
private fun SectionTitle(text: String) {
    SmallTitle(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        insideMargin = SectionTitleMargin,
    )
}

@Composable
private fun PreferenceIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.padding(end = 16.dp),
        // 纯黑（深色模式下是白）—— HyperIsland 的 `SettingsIcon` 就是 `onBackground`
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

/** 整页横向留白，和 HyperIsland 的 `horizontalContentPadding` 同源。 */
private val PagePadding = 16.dp

/** 条目内边距， HyperIsland `SettingsItemMargin`。主题的二级页也用同一份（同 package）。 */
internal val SettingsItemMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

/** 组标题内边距，横竖两边都比条目收一点。 */
private val SectionTitleMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
