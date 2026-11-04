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
import com.nzd.antigravitypanel.data.export.MatchExportFormat
import com.nzd.antigravitypanel.data.settings.ScoreUnitTarget
import com.nzd.antigravitypanel.data.settings.UserSettings
import com.nzd.antigravitypanel.ui.component.MaterialGlyphs
import com.nzd.antigravitypanel.ui.component.openUrl
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
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
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 官方 PC 端的下载页（蓝奏云）。
 *
 * ⚠️ 必须带 scheme：`wwbxs.lanzoum.com/...` 这样没有 scheme 的串，
 * `Uri.parse` 会把它当**相对路径**处理，ACTION_VIEW 匹配不到任何 Activity，
 * 点了就静默没反应（`openUrl` 内部 `runCatching` 会把异常吃掉）。
 */
const val PC_DOWNLOAD_URL = "https://wwbxs.lanzoum.com/b011mli8fi"

/**
 * 「积分显示单位」那一行的副标题：把当前勾选说成一句人话。
 *
 * 三个都勾 / 都没勾时给整句（"全部以万显示" / "全部显示完整数字"），
 * 否则列出页面名 —— 只写"已选 2 项"的话用户还得点开才知道是哪两项。
 */
private fun scoreUnitSummary(targets: Set<ScoreUnitTarget>): String = when {
    targets.isEmpty() -> "全部显示完整数字"
    targets.size == ScoreUnitTarget.entries.size -> "全部以万显示"
    else -> "以万显示：" + ScoreUnitTarget.entries
        .filter { it in targets }
        .joinToString("、") { it.label }
}

data class SettingsUiState(
    val retentionMonths: Int = UserSettings.DEFAULT_RETENTION_MONTHS,
    val autoRefreshMinutes: Int = UserSettings.ON_OPEN,
    /** 「概览页顶栏显示账号信息」（见 `UserSettings.setTopBarAccount`）。 */
    val topBarAccountEnabled: Boolean = UserSettings.DEFAULT_TOP_BAR_ACCOUNT,
    /** 「积分显示单位」：哪些页面折成万（见 `UserSettings.setScoreWanTargets`）。 */
    val scoreWanTargets: Set<ScoreUnitTarget> = ScoreUnitTarget.DEFAULT,
    /** 「用综合分替换经济转化」（见 `UserSettings.setCompositeScore`）。 */
    val compositeScoreEnabled: Boolean = UserSettings.DEFAULT_COMPOSITE_SCORE,

    /**
     * 「代理模式（暂停官方同步）」（见 `UserSettings.setProxyMode`）。默认**关**。
     *
     * ⚠️ 这个值只反映**当前这次进程**里的开关状态。落盘的是"下一次启动要保护一轮"
     * 的一次性标记，不是开关本身 —— 所以重启后这里必然读到 false。
     * 别拿它去反推"用户上次是不是开着代理模式"。
     */
    val proxyModeEnabled: Boolean = false,
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
    onTopBarAccountChange: (Boolean) -> Unit,
    /**
     * 「积分显示单位」：切换**一个**页面的单位。
     *
     * 传"切哪一项"而不是"算好的整份集合"：弹层里的条目是组合期拍下的快照，
     * 连点两项时第二下拿到的可能是旧集合（结果就是点了没反应、或者两项一起变）。
     * 让调用方去读**当前**的落盘值再翻，怎么点都不会丢。
     */
    onScoreUnitToggle: (ScoreUnitTarget) -> Unit,
    onCompositeScoreChange: (Boolean) -> Unit,
    onProxyModeChange: (Boolean) -> Unit,
    onClearLocalData: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
    /** 数据组：导出整包（zip，含凭证 / 战绩 / 地图分布）。 */
    onExportData: () -> Unit,
    /** 数据组：导入整包（读本应用导出的 zip，也兼容早期的对局 JSON）。 */
    onImportData: () -> Unit,
    onExportMatches: (MatchExportFormat) -> Unit,
    modifier: Modifier = Modifier,
    /** Scaffold 给的顶栏 / 底栏高度。加在滚动容器的上下，内容仍从顶栏底下穿过。 */
    insets: PaddingValues = PaddingValues(0.dp),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 清空本地数据不可撤销，点一下就把几百场对局抹了 —— 必须再确认一次
    var confirmClear by remember { mutableStateOf(false) }
    // 导出的 zip 里是**明文的登录凭证**，先讲清楚再让用户选保存位置
    var confirmExportData by remember { mutableStateOf(false) }
    // 导出要先选格式：文件写到哪儿由系统对话框决定，格式只有用户知道想要哪种
    var exportFormatDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 甩到列表顶 / 底时的一记轻振动（NexioSchedule 同款）
                .scrollEndHaptic()
                .padding(horizontal = PagePadding)
                .padding(
                    top = insets.calculateTopPadding(),
                    bottom = insets.calculateBottomPadding(),
                ),
        ) {
            SectionTitle(text = "外观")
            PreferenceCard {
                // 顶栏那颗账号胶囊（头像 + 昵称 + 分区）的开关**就摆在外观组里**。
                // 它和主题一样属于"看起来的样子"，压到主题那个二级页里没人找得到 ——
                // 二级页给的是"整体外观怎么配"，这个是单个功能的开合。
                SwitchPreference(
                    title = "概览页顶栏显示账号信息",
                    startAction = { PreferenceIcon(MiuixIcons.Contacts) },
                    insideMargin = SettingsItemMargin,
                    checked = state.topBarAccountEnabled,
                    onCheckedChange = onTopBarAccountChange,
                )
                // 主题单独进二级页（照 HyperIsland：外观 → 主题），
                // 一级页只给入口，具体开关在里面
                ArrowPreference(
                    title = "主题",
                    startAction = { PreferenceIcon(MiuixIcons.Theme) },
                    insideMargin = SettingsItemMargin,
                    onClick = onOpenTheme,
                )
                // 积分显示单位紧跟在「主题」后面：它也是"数字长什么样"这件事。
                // 用下拉而不是开关，是因为三个页面各有各的偏好（扫读的用万、核对的用全量），
                // 一个总开关满足不了；**多选**靠 collapseOnSelection = false 实现 ——
                // 默认行为是点一项就收起弹层，那样连点两项都得重新展开。
                OverlayDropdownPreference(
                    entries = listOf(
                        DropdownEntry(
                            items = ScoreUnitTarget.entries.map { target ->
                                val on = target in state.scoreWanTargets
                                DropdownItem(
                                    text = target.label,
                                    summary = if (on) "以万为单位" else "完整数字",
                                    selected = on,
                                    // 只交出"点了哪一项"，勾/取消由调用方按**当前**值算
                                    onClick = { onScoreUnitToggle(target) },
                                )
                            },
                        ),
                    ),
                    title = "积分显示单位",
                    summary = scoreUnitSummary(state.scoreWanTargets),
                    startAction = { PreferenceIcon(MaterialGlyphs.Scoreboard) },
                    insideMargin = SettingsItemMargin,
                    // 右侧不重复一个页面名：当前状态由下面那行 summary 一句话说清
                    showValue = false,
                    collapseOnSelection = false,
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
                    startAction = { PreferenceIcon(MaterialGlyphs.Save) },
                    insideMargin = SettingsItemMargin,
                    showValue = true,
                    onSelectedIndexChange = { index ->
                        UserSettings.OPTIONS.getOrNull(index)?.let(onRetentionMonthsChange)
                    },
                )
                // ---- 迁移：整包进出 ----
                // 这两条放在「数据」组而不是「实验性功能」：它们是常规的换机流程，
                // 用户翻设置找的就是"数据"这一组。
                ArrowPreference(
                    title = "导出数据",
                    // 副标题只说一句"含凭证"就够了：这才是用户点之前必须知道的事，
                    // 其它内容（战绩 / 地图分布 / 设置）写在确认弹窗里。
                    summary = "含登录凭证，zip 包",
                    startAction = { PreferenceIcon(MiuixIcons.Remove) },
                    insideMargin = SettingsItemMargin,
                    onClick = { confirmExportData = true },
                )
                ArrowPreference(
                    title = "导入数据",
                    summary = "读取本应用导出的 zip",
                    startAction = { PreferenceIcon(MiuixIcons.Import) },
                    insideMargin = SettingsItemMargin,
                    onClick = onImportData,
                )
                // 清空永远放这一组的最后一个：它和上面两条都是"动数据"，
                // 但只有它不可撤销，紧贴着别的按钮容易被顺手点到
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
                // 综合分是"同一份数据换一种读法"：不发请求、不改落盘，
                // 关掉就能原样回到「经济转化」那个比值，所以敢默认开。
                // 具体算法长按详情页那一格能看到。
                SwitchPreference(
                    title = "用综合分替换经济转化",
                    startAction = { PreferenceIcon(MaterialGlyphs.Calculate) },
                    insideMargin = SettingsItemMargin,
                    checked = state.compositeScoreEnabled,
                    onCheckedChange = onCompositeScoreChange,
                )
                // 代理模式：给"必须用代理才能打开 wiki 的地区"留一个安全窗口。
                // 开了之后 App 一个官方请求都不发（签到/领取也停），免得带凭证的请求
                // 从境外 IP 出去。⚠️ 重启 App 会自动关掉，不会长期漏签。
                SwitchPreference(
                    title = "临时停止同步数据（代理模式）",
                    summary = "开启后 App 不再请求任何官方接口，方便挂代理访问 Wiki。重启应用后自动关闭。",
                    startAction = { PreferenceIcon(MiuixIcons.Pause) },
                    insideMargin = SettingsItemMargin,
                    checked = state.proxyModeEnabled,
                    onCheckedChange = onProxyModeChange,
                )
                ArrowPreference(
                    title = "导出历史战绩为纯文本格式",
                    startAction = { PreferenceIcon(MiuixIcons.Share) },
                    insideMargin = SettingsItemMargin,
                    onClick = { exportFormatDialog = true },
                )
            }

            // 「其他」放在最后一组，「关于」永远最后一项：它不是操作，是出口
            SectionTitle(text = "其他")
            PreferenceCard {
                // PC 端是另一个包，但用户找它就是从设置页找 ——
                // 放在「关于」上面，先给"还想装别的"再给"这是什么"。
                // 标题点名版本号并写「下载」：点开的是网盘里的安装包，不是介绍页了，
                // 不写版本号的话用户不知道拿到的是不是最新那版。
                // 提取码放在 summary（第二行小字），网盘页要密码，不写在这儿等于让人白跑一趟。
                ArrowPreference(
                    // 标题带上 App 全名：光说「PC 端」用户还得猜是哪个 PC 端
                    title = "下载PC 端《反重力数据面板》 1.9.2",
                    summary = "密码:5ytc",
                    startAction = { PreferenceIcon(MaterialGlyphs.Computer) },
                    insideMargin = SettingsItemMargin,
                    onClick = { openUrl(PC_DOWNLOAD_URL) },
                )
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

    BackupExportDialog(
        show = confirmExportData,
        onDismiss = { confirmExportData = false },
        onConfirm = {
            confirmExportData = false
            onExportData()
        },
    )

    ExportFormatDialog(
        show = exportFormatDialog,
        onDismiss = { exportFormatDialog = false },
        onPick = { format ->
            exportFormatDialog = false
            onExportMatches(format)
        },
    )
}

/**
 * 选导出格式。
 *
 * 只有两个按钮，不给单选列表：这里是"选完立刻弹保存位置"，多一步确认没意义。
 * 说明文字里把"哪些局才有后半段数据"讲清楚 —— 否则导出来的表有一半是空的，
 * 用户只会以为是导出坏了。
 */
@Composable
private fun ExportFormatDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onPick: (MatchExportFormat) -> Unit,
) {
    WindowDialog(
        show = show,
        title = "导出为纯文本",
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "导出本机保存的全部对局。打开过详情页的对局会补上金币、" +
                "Boss 伤害、配装等单局数据，没有的就留空。内容全部使用英文和数字。",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
                onClick = { onPick(MatchExportFormat.CSV) },
            ) {
                Text("CSV")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
                onClick = { onPick(MatchExportFormat.JSON) },
            ) {
                Text("JSON")
            }
        }
    }
}

/**
 * 导出数据包的确认。
 *
 * 这一步不是"多一次点击"：导出的 zip 里躺的是**明文的 cookie 和四套签到凭证**，
 * 拿到文件的人可以直接以用户身份操作账号。必须在系统保存对话框之前把这件事说清楚。
 */
@Composable
private fun BackupExportDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "导出数据",
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "把登录凭证、签到凭证、全部战绩（含已缓存的单局详情）、" +
                "地图分布和设置打包成一个 zip 文件，换设备后可以直接导入。\n\n" +
                "包内是明文，请自己保管，不要发给别人。",
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
                colors = ButtonDefaults.buttonColors(),
                onClick = onConfirm,
            ) {
                Text("导出")
            }
        }
    }
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
