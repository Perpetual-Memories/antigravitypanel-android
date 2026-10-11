package com.nzd.antigravitypanel.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.TopBarGlassAnchor
import com.nzd.antigravitypanel.ui.component.TopBarGlassBackButton
import com.nzd.antigravitypanel.ui.component.TopBarGlassPlaceholder
import com.nzd.antigravitypanel.ui.component.openUrl
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 引用的三级页（设置 → 关于 → 引用）。
 *
 * 布局是 HyperIsland `ReferencesPage` 的还原：最上面一张白色矩形卡片装一句
 * 总述，下面「引用项目」一组，每条是「项目_开发者」+ 一句说明，尾部 🔗 表示点开
 * 会跳外链。条目**不带前置图标**——HyperIsland 的引用页就没有，它只有外链箭头。
 */
@Composable
internal fun ReferencesScreen(
    onBack: () -> Unit,
    liquidGlassEnabled: Boolean = true,
) {
    val scrollBehavior = MiuixScrollBehavior()
    // 引用页条目少、整页装得下，压根滚不动 —— 所以列表必须挂 `.overScrollVertical()`：
    // 玻璃显隐的**第二条**判据是「停在顶部 + 向下回弹」（见 ScrollDrivenGlass 的
    // glassShouldShow），没有 overscroll 的话短页面上返回键永远是个没样式的箭头。
    // 这是 NexioSchedule 的做法（它的「更新设置」页同样是短页面）。
    val listState = rememberLazyListState()
    // 返回键那颗玻璃画在顶栏**外面**那层（顶栏自带 clipToBounds 会裁掉阴影），
    // 槽位里只留一个占位撑住标题，位置由它上报。
    val backGlassAnchor = remember { TopBarGlassAnchor() }

    BarBlurHost(enabled = liquidGlassEnabled) {
        Scaffold(
            topBar = {
                BlurredBar(
                    topGradient = true,
                    startGlassAnchor = backGlassAnchor,
                    startGlass = {
                        TopBarGlassBackButton(
                            onBack = onBack,
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) {
                    SmallTopAppBar(
                        title = "引用",
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            TopBarGlassPlaceholder(backGlassAnchor)
                        },
                    )
                }
            },
        ) { innerPadding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        // 甩到列表顶 / 底时的一记轻振动（NexioSchedule 同款）
                        .scrollEndHaptic()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        // 顶栏是浮在内容上的：不给 top padding 第一条会被压在顶栏底下
                        top = 12.dp + innerPadding.calculateTopPadding(),
                        bottom = 16.dp + innerPadding.calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "description") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = REFERENCES_DESCRIPTION,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    item(key = "projects") {
                        AboutSectionTitle("引用项目")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            REFERENCE_PROJECTS.forEach { project ->
                                AboutAction(
                                    title = project.title,
                                    summary = project.summary,
                                    endIcon = MiuixIcons.Link,
                                    endIconSize = 26.dp,
                                ) { openUrl(project.url) }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class ReferenceProject(
    val title: String,
    val summary: String,
    val url: String,
)

private const val REFERENCES_DESCRIPTION =
    "反重力数据面板开发过程中参考或应用了以下开源项目的接口、设计与实现，在此一并致谢。"

internal val REFERENCE_PROJECTS = listOf(
    ReferenceProject(
        title = "NZM_哈曼@Haman412",
        summary = "一切的起点",
        url = "https://github.com/HaMan412/NZM",
    ),
    ReferenceProject(
        title = "Miuix_YuKongA",
        summary = "个人开发者青睐的 UI 组件库",
        url = "https://github.com/compose-miuix-ui/miuix/",
    ),
    ReferenceProject(
        title = "HyperIsland_芥子@1812z",
        summary = "参考甚至直接使用了其极为优秀的 UI 实现",
        url = "https://github.com/1812z/HyperIsland",
    ),
    ReferenceProject(
        title = "NexioSchedule_Haooz@HaoZai000",
        summary = "参考并使用了其应用顶栏等设计",
        url = "https://github.com/HaoZai000/NexioSchedule",
    ),
    ReferenceProject(
        title = "lsfTB_lsfdc233",
        summary = "参考并使用了其扫码相关UI",
        url = "https://github.com/lsfdc233/lsfTB",
    ),
    ReferenceProject(
        title = "nzm-wiki_幻想延續@lostlightll",
        summary = "逆战:未来最好的wiki，本项目使用了其仓库的资源",
        url = "https://github.com/lostlightll/nzm-wiki",
    ),
)
