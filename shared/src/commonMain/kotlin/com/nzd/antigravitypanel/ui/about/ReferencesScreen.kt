package com.nzd.antigravitypanel.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.openUrl
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

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

    BarBlurHost(enabled = liquidGlassEnabled) {
        Scaffold(
            topBar = {
                BlurredBar(topGradient = true) {
                    SmallTopAppBar(
                        title = "引用",
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
)
