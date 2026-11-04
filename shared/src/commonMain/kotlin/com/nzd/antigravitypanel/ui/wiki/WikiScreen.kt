package com.nzd.antigravitypanel.ui.wiki

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.TopBarGlassAnchor
import com.nzd.antigravitypanel.ui.component.TopBarGlassCapsule
import com.nzd.antigravitypanel.ui.component.TopBarGlassPlaceholder
import com.nzd.antigravitypanel.ui.component.rememberTopBarGlassProgress
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * wiki 二级页：一整屏的网页 + 顶部悬浮的玻璃按钮条。
 *
 * 按钮条的排布（用户定的）—— 左右各一颗胶囊，把站内顶栏的功能全接下来：
 * - 左胶囊：**侧边栏 / 搜索 / 倒退**
 * - 右胶囊：**刷新 / 命令面板 / 更多**
 * - 三颗"面板类"按钮（侧边栏、搜索、命令面板）都是**开关**：
 *   面板开着时再点一次就收起（见 [WikiBrowser.toggleSidebar] 等）。
 * - 「更多」弹的是**下拉菜单**（[WikiMoreMenu]），不是对话框。
 *   ⚠️ 它必须留在胶囊最右 —— 那个下拉锚的是调用处的父布局，
 *   让「命令」排它右边会把菜单锚到胶囊中间去。
 * - 站内顶栏（品牌、面包屑、手机版返回链接连同那四颗按钮）由**站点自己**
 *   收起（`data-client` 分支），这一页**不需要**再摆返回键之外的第二套导航。
 * - 退出这一页靠**预测返回手势** / 系统返回键。
 *   ⚠️ 但**已经在网页首页**时不直接退：先弹一句「再按一次返回退出」，
 *   5 秒内再来一次才真退（这道闸在宿主 `AppContent.confirmWikiExit` 里，
 *   因为它同时要管手势和返回键，而那一层不在这一页里）。
 */
@Composable
fun WikiScreen(
    onBack: () -> Unit,
    /**
     * 递给宿主的那座桥。挂上之后**系统返回键 / 返回手势和左上角倒退按钮等价**：
     * 网页里还有历史就退一页网页，已经在首页才谈"退出这一页"。
     *
     * 见 [WikiBackBridge] —— 系统返回是在宿主那一层接的，而浏览器长在这一页里。
     */
    backBridge: WikiBackBridge,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = MiuixScrollBehavior()
    // ⚠️ 传 **null**，不是 scrollBehavior：玻璃的显隐判据是「Compose 滚动容器滚过了
    // 阈值 or 正在 overscroll」，而网页是 WebView 自己滚 —— 这两条永远不成立，
    // 传 scrollBehavior 的话玻璃恒为 0，按钮就是几个没底的裸图标。
    // 传 null 走「二级页」那条路，恒为 1，玻璃常驻。
    val glassProgress = rememberTopBarGlassProgress(null)
    val startAnchor = remember { TopBarGlassAnchor() }
    val endAnchor = remember { TopBarGlassAnchor() }
    val browser = rememberWikiBrowser()

    var moreMenu by remember { mutableStateOf(false) }
    // 「更多 → 本地测试」的输入框。开着的时候网页仍留在下面，确认后直接换地址。
    var localTestDialog by remember { mutableStateOf(false) }

    // 把「网页里还有没有历史」回灌给宿主。
    // ⚠️ 用 snapshotFlow 而不是在组合里读 `browser.canGoBack`：读进组合就等于
    // 每次站内跳转都让这一页（连带那颗 WebView）重组一轮。
    LaunchedEffect(browser, backBridge) {
        snapshotFlow { browser.canGoBack }.collect { backBridge.onCanGoBackChange(it) }
    }
    // 「让网页退一页」这个动作挂给宿主（系统返回用）。
    // ⚠️ 挂在 DisposableEffect 里而不是直接在组合里赋值：这一页退场时要把槽清掉，
    // 否则宿主拿着一个指向已销毁 WebView 的闭包。
    DisposableEffect(browser, backBridge) {
        backBridge.bindGoBack { browser.goBack() }
        onDispose { backBridge.unbindGoBack() }
    }

    // ⚠️⚠️ 这页**不开**磨砂（enabled = false），也就不建离屏 backdrop。
    // 原因：开了的话整页内容会挂上 `layerBackdrop`，而那个 Modifier 的 draw() 是
    // `drawContent()`（正常上屏）+ `record(layer)`（**再**录一遍进离屏 GraphicsLayer）
    // —— 顶栏的渐进糊每帧都要采样这块层，于是整颗 WebView 每帧被重复光栅化一次。
    // WebView 落在这种"每帧重录"的离屏层里就是不停闪，拖久了还会把渲染进程拖崩。
    // 关掉之后 backdrop 为 null：玻璃按钮退化成半透明圆片
    // （`TopBarGlassChip` / `TopBarGlassCapsule` 都有这条兜底）；
    // 顶栏那条底色则靠 `BlurredBar(enabled = false)` 一并去掉 —— 见它的调用点。
    BarBlurHost(enabled = false) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                BlurredBar(
                    topGradient = true,
                    // ⚠️⚠️ 这一页**连底色都不铺**（enabled = false）。铺了的话走的是
                    // `barBlurBackground` 最后那条兜底分支 `background(colorScheme.surface)`
                    // ——暗色主题下那是**纯黑**，而这一页底下就是网页（站点本身也是深色
                    // 但偏 #191919），于是在按钮那条下沿切出一条刺眼的黑块
                    // （顶栏本来是"糊住下面的内容"，这一页下面压根没有内容，
                    // 兜底色就无处可藏）。
                    // 关掉之后 BlurredBar 只画那几颗玻璃按钮，网页直接衬在按钮底下。
                    enabled = false,
                    startGlassAnchor = startAnchor,
                    startGlass = {
                        TopBarGlassCapsule(progress = glassProgress) {
                            GlassAction(
                                imageVector = MiuixIcons.Sidebar,
                                contentDescription = "侧边栏",
                                onClick = { browser.toggleSidebar() },
                            )
                            GlassAction(
                                imageVector = MiuixIcons.Search,
                                contentDescription = "搜索",
                                onClick = { browser.toggleSearch() },
                            )
                            GlassAction(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "倒退",
                                onClick = {
                                    // 网页里还有历史就退一页；已经回到首页了就退出二级页
                                    if (browser.canGoBack) browser.goBack() else onBack()
                                },
                            )
                        }
                    },
                    endGlassAnchor = endAnchor,
                    endGlass = {
                        TopBarGlassCapsule(progress = glassProgress) {
                            GlassAction(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "刷新",
                                onClick = { browser.refresh() },
                            )
                            GlassAction(
                                imageVector = KeyboardCommandIcon,
                                contentDescription = "命令面板",
                                onClick = { browser.toggleCommand() },
                            )
                            // 「更多」和它的下拉菜单**必须在同一个 Box 里**：
                            // `OverlayDropdownPopup` 锚的是调用处的父布局，
                            // 单独摆在外面会锚到整块胶囊上。
                            Box {
                                GlassAction(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多",
                                    onClick = { moreMenu = true },
                                )
                                WikiMoreMenu(
                                    browser = browser,
                                    show = moreMenu,
                                    onDismiss = { moreMenu = false },
                                    onLocalTest = { localTestDialog = true },
                                )
                            }
                        }
                    },
                ) {
                    SmallTopAppBar(
                        title = "",
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { TopBarGlassPlaceholder(startAnchor) },
                        actions = { TopBarGlassPlaceholder(endAnchor) },
                    )
                }
            },
        ) { padding ->
            // ⚠️ 刻意**不**包 BarBackdropContent：那层是把内容录进离屏 layer 用的，
            // WebView 不能进（理由见上面 BarBlurHost 那段）。
            //
            // ⚠️⚠️ 网页**铺满整屏**，不给它留任何 Compose 层的空隙：留了就会露出
            // Scaffold 的底色（暗色主题下纯黑），在深色网页上方是一条黑块。
            // 那"让内容躲开按钮"怎么办？站点按宿主写进 `--app-chrome-top` 的值自己让位。
            Box(modifier = Modifier.fillMaxSize()) {
                // ⚠️ 让位值取 [`padding`] 的上边距 —— 就是这一页的顶栏高度
                // （状态栏 + 按钮条），Scaffold 已经算好了，别再自己猜状态栏有多高。
                // ⚠️ 传进去的是 **dp**：`Dp.value` 那个数正好等于网页的 CSS px
                // （站点 viewport 是 width=device-width）。当成设备像素传会放大 density 倍。
                WikiWebView(
                    browser = browser,
                    contentInsetDp = padding.calculateTopPadding().value,
                    modifier = Modifier.fillMaxSize(),
                )
                if (browser.progress > 0f && browser.progress < 1f) {
                    WikiProgress(
                        progress = browser.progress,
                        // 进度条仍压在**按钮条下沿**：它是"这一页在加载"的指示，
                        // 得跟按钮待在一起，不能缩到状态栏底下去。
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = padding.calculateTopPadding()),
                    )
                }
                // 窗口级对话框，摆在 Box 里只是借个位置 —— 它自己开一个 Dialog 窗口，
                // 不受这层布局约束
                LocalTestDialog(
                    show = localTestDialog,
                    onDismiss = { localTestDialog = false },
                    onConfirm = { url ->
                        browser.loadUrl(url)
                        localTestDialog = false
                    },
                )
            }
        }
    }
}

/**
 * 胶囊里的一颗按钮。
 *
 * ⚠️ 尺寸钉 40dp 而不是 42：胶囊本体 42 高，里面那颗再留 1dp 边，
 * 三个并排才不会把胶囊撑得比返回键那一侧明显宽一截。
 */
@Composable
private fun GlassAction(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 网页加载进度条，压在顶栏玻璃下沿。 */
@Composable
private fun WikiProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f))
    Box(modifier = modifier.fillMaxWidth().height(2.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(animated)
                .height(2.dp)
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}
