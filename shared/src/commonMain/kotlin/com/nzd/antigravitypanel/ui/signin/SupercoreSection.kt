package com.nzd.antigravitypanel.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.supercore.SupercoreGiftRecord
import com.nzd.antigravitypanel.data.supercore.SupercorePlan
import com.nzd.antigravitypanel.data.supercore.SupercoreRole
import com.nzd.antigravitypanel.ui.component.InfoGrid
import com.nzd.antigravitypanel.ui.component.rememberTextFilePicker
import com.nzd.antigravitypanel.ui.settings.SettingsItemMargin
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 签到页里「超核组队活动」那一块。
 *
 * 和另三块并列，但配置最麻烦：**活动网址每期都换**，凭证也必须从这一页的抓包里拿
 * （和 QQ 游戏中心 / 心悦都不是同一套，理由见 `SupercoreCredential`）。
 * 所以未配置时不显示"错误"，而是引导"轻触卡片导入活动页抓包"。
 */
@Composable
fun SupercoreSection(
    state: SupercoreUiState,
    autoRun: Boolean,
    onRunNow: () -> Unit,
    onSaveCredential: (String) -> Unit,
    onImportHar: (String) -> Unit,
    onClearCredential: () -> Unit,
    onSelectRole: (String) -> Unit,
    onAutoRunChange: (Boolean) -> Unit,
    /** 队长确认成团（弹层里还会二次确认）。 */
    onConfirmTeam: () -> Unit,
    /** 切到「领奖记录」时拉官方记录。 */
    onLoadRecords: () -> Unit,
    onConsumeNotice: () -> Unit,
) {
    var sheet by remember { mutableStateOf(false) }
    var confirmTeamDialog by remember { mutableStateOf(false) }

    SmallTitle(text = "超核组队活动")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = { sheet = true },
    ) {
        Column(modifier = if (state.configured) Modifier.padding(16.dp) else Modifier) {
            if (!state.configured) {
                BasicComponent(insideMargin = SettingsItemMargin) {
                    Text(
                        text = "轻触卡片导入活动页抓包：网址和凭证都会自动识别。" +
                            "它和 QQ 游戏中心、心悦悦享卡的凭证都不通用，需要在这一期活动页里单独抓一次。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Button(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxWidth(),
                    onClick = { sheet = true },
                ) {
                    Text("配置超核活动")
                }
            } else {
                val done = state.available && !state.status.stale && !state.status.notStarted &&
                    state.status.signedToday && state.status.tasksDone
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            state.loading && !state.available -> "同步中…"
                            state.status.notStarted -> "活动还没开始"
                            !state.available -> "还没拿到状态"
                            state.status.stale -> "还没拿到今天的状态"
                            state.status.signedToday -> "今天已签到"
                            else -> "今天还没签到"
                        },
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) signInAccentColor() else MiuixTheme.colorScheme.onSurface,
                    )
                    if (state.running || state.loading) {
                        CircularProgressIndicator(
                            progress = null,
                            size = 22.dp,
                            strokeWidth = 3.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.primary,
                                backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }

                Text(
                    text = if (state.running) "正在领任务积分、签到并抽奖…" else supercorePlanLine(state.plan),
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                // 服务端那句"为什么没领到"（没开 / 没成团 / 任务没完成）。
                // 活动没开时它带着开启时间，比"活动还没开始"有用。
                if (state.status.message.isNotBlank()) {
                    Text(
                        text = state.status.message,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                if (state.available) {
                    InfoGrid(
                        items = buildList {
                            add("积分" to (state.status.points?.toString() ?: "—"))
                            add(
                                "签到" to if (state.status.signTotal > 0) {
                                    "${state.status.signClaimed} / ${state.status.signTotal} 天"
                                } else {
                                    "—"
                                },
                            )
                            // 显示「已抽 / 上限」而不是「剩余」：活动没开时服务端也会给
                            // remain=27（那是次数上限），直接摆"27 次"会被读成"已经抽了 27 次"。
                            add(
                                "抽奖" to if (state.status.drawsTotal != null &&
                                    state.status.drawsRemain != null
                                ) {
                                    val used = (state.status.drawsTotal - state.status.drawsRemain)
                                        .coerceAtLeast(0)
                                    "$used / ${state.status.drawsTotal} 次"
                                } else {
                                    "—"
                                },
                            )
                            add(
                                "今日任务" to if (state.status.tasksTotal > 0) {
                                    "${state.status.tasksClaimed} / ${state.status.tasksTotal}"
                                } else {
                                    "—"
                                },
                            )
                        },
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                if (state.error != null) {
                    Text(
                        text = state.error,
                        modifier = Modifier.padding(top = 12.dp),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.error,
                    )
                }

                // 没成团时领取只会拿到"网络繁忙"：队长这里给一颗单独的确认按钮，
                // 队员只显示提示（status.message 里那行）。
                if (state.needsTeamConfirm && state.teamLeader) {
                    Button(
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth(),
                        onClick = { confirmTeamDialog = true },
                        enabled = !state.running && !state.loading,
                    ) {
                        Text("确认成团")
                    }
                }

                // 卡片上**不再摆**「立即领取并抽奖」：这一屏是看状态的，
                // 手动跑一次的动作收在「自动签到」那个弹层里（同一颗按钮还在那儿）。
                // 卡上留一颗通栏主按钮，会让人以为每次进这页都得点一下。
            }
        }
    }

    // 自动跑出来的结果只在这个页面弹：概览页不挂这个弹层，冷启动也不会被打断
    state.notice?.let { notice ->
        WindowDialog(
            show = true,
            title = "超核组队活动",
            onDismissRequest = onConsumeNotice,
        ) {
            Column {
                Text(
                    text = notice,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    onClick = onConsumeNotice,
                ) {
                    Text("好的")
                }
            }
        }
    }

    // 确认成团不可逆：成员会被锁定，不能退出或更换，所以必须先弹一次
    if (confirmTeamDialog) {
        WindowDialog(
            show = true,
            title = "确认成团",
            onDismissRequest = { confirmTeamDialog = false },
        ) {
            Column {
                Text(
                    text = "确认后所有队员会被锁定，不能退出或更换；确认后才会开启任务和签到。只能确认一次，确定吗？",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                        onClick = { confirmTeamDialog = false },
                    ) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmTeamDialog = false
                            onConfirmTeam()
                        },
                    ) {
                        Text("确认成团")
                    }
                }
            }
        }
    }

    SupercoreSheet(
        show = sheet,
        state = state,
        autoRun = autoRun,
        onDismiss = { sheet = false },
        onSaveCredential = onSaveCredential,
        onImportHar = onImportHar,
        onClearCredential = onClearCredential,
        onSelectRole = onSelectRole,
        onAutoRunChange = onAutoRunChange,
        onRunNow = onRunNow,
        onLoadRecords = onLoadRecords,
    )
}

private fun supercorePlanLine(plan: SupercorePlan?): String {
    if (plan == null) return "还没读到这一期的活动配置"
    val title = plan.title.ifBlank { "活动 ${plan.activityId}" }
    return "$title · 活动 ${plan.activityId} · ${plan.signFlows.size} 天签到 / ${plan.taskFlows.size} 个任务"
}

/**
 * 超核的底部弹层。两个 Tab：
 * - 「活动」：网址、凭证、角色、自动开关、导入抓包；
 * - 「领奖记录」：官方接口返回的领奖结果（日期 + 奖励）。
 *
 * 抽奖历史单独一页而不是塞在设置下面：它是**结果**，用户抽完就会想看，
 * 和"填网址 / 贴凭证"这种配置不是一类东西。
 */
/** `TabRowWithContour` 的高度（`TabRowDefaults.TabRowWithContourHeight`）。 */
private val SUPERCORE_TAB_HEIGHT = 45.dp

/** 外面那圈 Card 的上下各 12dp 内边距。 */
private val SUPERCORE_TAB_CARD_PADDING = 12.dp

/** TabRow 卡片与内容区之间留 8dp，别让选中胶囊贴着表单。 */
private val SUPERCORE_TAB_GAP = 8.dp

/** 底栏高度，与 `ACTION_BUTTON_HEIGHT` 一致。 */
private val SUPERCORE_ACTION_BAR_HEIGHT = 52.dp

/** 底栏上方那 12dp 间距也算进固定高度里。 */
private val SUPERCORE_SHEET_CHROME = SUPERCORE_TAB_HEIGHT + SUPERCORE_TAB_CARD_PADDING * 2 +
    SUPERCORE_TAB_GAP + 12.dp + SUPERCORE_ACTION_BAR_HEIGHT

/** 两个 Tab 共用的内容区高度：下限保证可用，上限避免整屏都被弹层吃掉。 */
private val SUPERCORE_PANE_MIN_HEIGHT = 220.dp
private val SUPERCORE_PANE_MAX_HEIGHT = 480.dp

@Composable
private fun SupercoreSheet(
    show: Boolean,
    state: SupercoreUiState,
    autoRun: Boolean,
    onDismiss: () -> Unit,
    onSaveCredential: (String) -> Unit,
    onImportHar: (String) -> Unit,
    onClearCredential: () -> Unit,
    onSelectRole: (String) -> Unit,
    onAutoRunChange: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onLoadRecords: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    // 切到记录页就拉一次官方记录（换设备 / 网页领的都能看到）
    LaunchedEffect(show, tab) {
        if (show && tab == 1) onLoadRecords()
    }

    OverlayBottomSheet(
        show = show,
        title = "超核组队活动",
        onDismissRequest = onDismiss,
    ) {
        // 两个 Tab 共用「同一块固定高度的内容区 + 同一根底栏」，切 Tab 时弹层高度不跳。
        // 内容区高度按可用空间算：正常是 480dp，键盘弹起时自己缩，不会把底栏顶出去。
        //
        // 底部先让出系统导航栏（手势条）+ 12dp：弹层是贴底的，不让 inset 的话
        // 底栏两颗按钮会被手势条压住，看着"太低"。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            val paneHeight = (maxHeight - SUPERCORE_SHEET_CHROME)
                .coerceIn(SUPERCORE_PANE_MIN_HEIGHT, SUPERCORE_PANE_MAX_HEIGHT)
            Column(modifier = Modifier.fillMaxWidth()) {
                // 照 Miuix 示例应用里 5/6 个 tab 的写法：**Card 里放 TabRowWithContour**。
                // 卡片是 surfaceContainer、分段条是 surface、选中药丸是 surfaceContainer，
                // 所以选中的那个像从卡片里"挖"出来 —— 和示例的层级一致。
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                    insideMargin = PaddingValues(
                        horizontal = SUPERCORE_TAB_CARD_PADDING,
                        vertical = SUPERCORE_TAB_CARD_PADDING,
                    ),
                ) {
                    TabRowWithContour(
                        tabs = listOf("活动", "领奖记录"),
                        selectedTabIndex = tab,
                        onTabSelected = { tab = it },
                        modifier = Modifier.fillMaxWidth(),
                        // 写死高度，上面的 chrome 计算才有确定的数
                        height = SUPERCORE_TAB_HEIGHT,
                    )
                }
                Spacer(modifier = Modifier.height(SUPERCORE_TAB_GAP))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(paneHeight),
                ) {
                    if (tab == 0) {
                        SupercoreSettingsPane(
                            state = state,
                            autoRun = autoRun,
                            onSaveCredential = onSaveCredential,
                            onSelectRole = onSelectRole,
                            onAutoRunChange = onAutoRunChange,
                            onRunNow = onRunNow,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        SupercoreRecordsPane(
                            records = state.records,
                            total = state.recordsTotal,
                            loading = state.recordsLoading,
                            error = state.recordsError,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                SupercoreActionBar(
                    bound = state.bound,
                    onImportHar = onImportHar,
                    onClearCredential = onClearCredential,
                )
            }
        }
    }
}

/**
 * 弹层底栏：导入抓包 + 清除凭证。
 *
 * 放在内容区外面，**不跟着滚**；没填过凭证时"清除"置灰禁用 —— 位置要一直在，
 * 用户得知道这里能清，只是现在点不动。
 */
@Composable
private fun SupercoreActionBar(
    bound: Boolean,
    onImportHar: (String) -> Unit,
    onClearCredential: () -> Unit,
) {
    val pickHar = rememberTextFilePicker { text ->
        if (text != null) onImportHar(text)
    }
    var confirmClear by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(SUPERCORE_ACTION_BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f),
            minWidth = 0.dp,
            minHeight = 0.dp,
            insideMargin = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary,
                contentColor = Color.White,
            ),
            onClick = { pickHar() },
        ) {
            Icon(
                imageVector = MiuixIcons.Import,
                contentDescription = "从抓包文件导入",
                modifier = Modifier.size(22.dp),
                tint = Color.White,
            )
        }

        Button(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            minHeight = 0.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            enabled = bound,
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.error,
                disabledColor = MiuixTheme.colorScheme.disabledSecondaryVariant,
                contentColor = Color.White,
                disabledContentColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            ),
            onClick = { if (bound) confirmClear = true },
        ) {
            Text("清除凭证")
        }
    }

    ClearSupercoreCredentialDialog(
        show = confirmClear,
        onDismiss = { confirmClear = false },
        onConfirm = {
            confirmClear = false
            onClearCredential()
        },
    )
}

@Composable
private fun SupercoreSettingsPane(
    state: SupercoreUiState,
    autoRun: Boolean,
    onSaveCredential: (String) -> Unit,
    onSelectRole: (String) -> Unit,
    onAutoRunChange: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val credentialState = rememberTextFieldState()

    LaunchedEffect(state.credentialRaw) {
        if (credentialState.text.toString() != state.credentialRaw) {
            credentialState.edit { replace(0, length, state.credentialRaw) }
        }
    }

    Column(
        // 高度由上层那个固定高度的 Box 决定；这里只负责在内部滚。
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HintCard(
            text = "每期的活动网址和凭证都从抓包里来：先在活动页里进一次" +
                "（活动没开时点一下领取也会发请求），再导出 HAR，点底栏的「导入抓包」——" +
                "网址和凭证会自动填好。换期也要重新导当期的 HAR。" +
                "凭证只保存在本机（加密），不会上传到任何服务器。",
        )

        Text(
            text = supercorePlanLine(state.plan),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )

        val credentialInput = credentialState.text.toString()
        TextField(
            state = credentialState,
            label = "请求头（T-OPENID / T-ACCESS-TOKEN）",
            modifier = Modifier.fillMaxWidth(),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            trailingIcon = {
                IconButton(
                    onClick = { if (credentialInput.isNotBlank()) onSaveCredential(credentialInput) },
                    enabled = credentialInput.isNotBlank(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "保存凭证",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            },
        )

        if (state.bound && state.error == null &&
            credentialInput == state.credentialRaw && credentialInput.isNotBlank()
        ) {
            Text(
                text = "已保存到本机",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        if (state.roles.isNotEmpty()) {
            RoleCard(
                roles = state.roles,
                selectedKey = state.roleKey,
                onSelect = onSelectRole,
            )
        }

        if (state.error != null) {
            Text(
                text = state.error,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.error,
            )
        }

        // 风险提示放在开关上方：先看清风险，再决定要不要开自动
        RiskNotice()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        ) {
            SwitchPreference(
                checked = autoRun,
                onCheckedChange = onAutoRunChange,
                title = "打开APP自动签到、领奖并抽奖",
                summary = if (autoRun) {
                    "冷启动和每次刷新都会试一次；只花这个活动里攒的积分"
                } else {
                    "关着：需要手动点「立即领取并抽奖」"
                },
                insideMargin = SettingsItemMargin,
            )
        }

        // 没成团时先别让点：服务端只会回"网络繁忙"，确认入口在卡片上
        if (state.needsTeamConfirm) {
            Text(
                text = if (state.teamLeader) {
                    "队伍还没确认成团：关掉弹层，在卡片上点「确认成团」"
                } else {
                    "队伍还没确认成团：等队长在活动页点「确认成团」"
                },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        // 立即执行跟着开关走：它和"自动"是同一组动作，不是底栏里的配置
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRunNow,
            enabled = state.configured && !state.needsTeamConfirm && !state.running && !state.loading,
        ) {
            Text(if (state.running) "正在领取并抽奖…" else "立即领取并抽奖")
        }
    }
}

/** 多角色时的选择器。单个角色只摆一行"发往"，不占一整张卡。 */
@Composable
private fun RoleCard(
    roles: List<SupercoreRole>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val selected = roles.firstOrNull { it.key == selectedKey } ?: roles.first()
    if (roles.size == 1) {
        Text(
            text = "发往：${selected.displayName}${selected.displayPartition.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            BasicComponent(insideMargin = SettingsItemMargin) {
                Text(
                    text = "这个号有 ${roles.size} 个角色，领奖前确认发给谁",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            for (role in roles) {
                BasicComponent(
                    insideMargin = SettingsItemMargin,
                    onClick = { if (role.key != selectedKey) onSelect(role.key) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = role.displayName,
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            val partition = role.displayPartition
                            if (partition.isNotBlank()) {
                                Text(
                                    text = partition,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        if (role.key == selected.key) {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = signInAccentColor(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 官方「领奖记录」。
 *
 * 数据来自活动页同一个接口（`GetCeibaGiftRecord`），所以换设备、在网页领的、
 * 以前领的都能看到，不再依赖本机记账。
 */
@Composable
private fun SupercoreRecordsPane(
    records: List<SupercoreGiftRecord>,
    total: Int,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> CircularProgressIndicator(
                    progress = null,
                    size = 24.dp,
                    strokeWidth = 3.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                    ),
                )

                error != null -> Text(
                    text = "领奖记录没拉到：$error",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error,
                )

                else -> Text(
                    text = "还没有领奖记录。",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 16.dp),
    ) {
        Text(
            text = buildString {
                append("共 ${maxOf(total, records.size)} 条 · 来自活动页的领奖记录")
                if (loading) append(" · 刷新中…")
            },
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.error,
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(records) { record ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // 服务端给的就是 `yyyy-MM-dd HH:mm:ss`，截到分钟够看
                                text = record.giftTime.take(16),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = record.giftName.ifBlank { "奖励" },
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            if (record.realGift) {
                                Text(
                                    text = "实物",
                                    fontSize = 11.sp,
                                    color = signInAccentColor(),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MiuixTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearSupercoreCredentialDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "清除凭证",
        onDismissRequest = onDismiss,
    ) {
        Column {
            Text(
                text = "将删除本机保存的超核活动凭证和今日缓存。领奖记录是活动页的官方数据，不受影响；删除后需要重新抓一次才能再用。",
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
                        contentColor = Color.White,
                    ),
                    onClick = onConfirm,
                ) {
                    Text("清除")
                }
            }
        }
    }
}
