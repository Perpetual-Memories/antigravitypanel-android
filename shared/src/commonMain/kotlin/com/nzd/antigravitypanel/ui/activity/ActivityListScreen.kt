package com.nzd.antigravitypanel.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.domain.ActivityEvent
import com.nzd.antigravitypanel.ui.component.ActivityRow
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 活动日历全量列表。概览页只放得下快截止的三场，剩下的从这里看。
 *
 * 整页的骨架照 miuix 示例应用的 `PullToRefreshPage`：
 * `BarBlurHost` → `Scaffold(BlurredBar + SmallTopAppBar)` → `BarBackdropContent`
 * → `PullToRefresh` → `LazyColumn`，下拉刷新走 `rememberPullToRefreshState`。
 *
 * @param liquidGlassEnabled 与设置页同一个开关，关掉后顶栏退化成不透明表面色。
 * @param refreshing 概览正在拉取。下拉刷新走的就是概览那次 `refresh`，所以进度状态也复用它，
 *   免得出现"下拉转圈停了但数据还没回来"。
 */
@Composable
fun ActivityListScreen(
    activities: List<ActivityEvent>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    liquidGlassEnabled: Boolean = true,
) {
    var selected by remember { mutableStateOf<ActivityEvent?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()

    BarBlurHost(enabled = liquidGlassEnabled) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                BlurredBar(topGradient = true) {
                    SmallTopAppBar(
                        title = "活动日历",
                        // 透明是必须的：底下一层 BlurredBar 已经在采样列表内容做模糊了，
                        // 这里再铺一层 surface 就把它彻底盖死，糊了等于没糊
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
            // 和主 Shell 一个套路：这一层铺满且不裁掉顶栏那一带，列表滚动时从顶栏底下穿过，
            // BlurredBar 才有东西可糊。躲开顶栏靠列表自己的 contentPadding。
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                val contentTop = 12.dp + innerPadding.calculateTopPadding()
                val contentBottom = 12.dp + innerPadding.calculateBottomPadding()
                val contentPadding = remember(contentTop, contentBottom) {
                    PaddingValues(
                        start = 12.dp,
                        top = contentTop,
                        end = 12.dp,
                        bottom = contentBottom,
                    )
                }
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    pullToRefreshState = pullToRefreshState,
                    topAppBarScrollBehavior = scrollBehavior,
                    contentPadding = contentPadding,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = contentPadding,
                    ) {
                        item(key = "activities") {
                            ActivityBlock(
                                activities = activities,
                                onSelect = { selected = it },
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { event ->
        WindowDialog(
            show = true,
            title = event.title,
            onDismissRequest = { selected = null },
        ) {
            Column {
                Text(
                    text = event.periodText(),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    onClick = { selected = null },
                ) {
                    Text("好的")
                }
            }
        }
    }
}

/**
 * 活动那一块：**整块都是卡片底色**，行是连续排下来的，不插分隔线。
 *
 * 之前是一行一个独立卡片、间距 10dp，活动少的时候屏幕上大半是没内容的窗口背景，
 * 看起来就是"一块深灰"。改成连续区块后视觉和概览页的活动日历完全一致。
 */
@Composable
private fun ActivityBlock(
    activities: List<ActivityEvent>,
    onSelect: (ActivityEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        if (activities.isEmpty()) {
            Text(
                text = "暂无进行中或即将开始的活动，下拉可以重新拉取",
                modifier = Modifier.padding(16.dp),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            return@Card
        }
        Column {
            activities.forEach { event ->
                ActivityRow(
                    event = event,
                    onClick = { onSelect(event) },
                )
            }
        }
    }
}
