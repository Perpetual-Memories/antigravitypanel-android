package com.nzd.antigravitypanel.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.update.DOWNLOAD_EXTRACT_CODE
import com.nzd.antigravitypanel.data.update.DOWNLOAD_PAGE_URL
import com.nzd.antigravitypanel.data.update.UpdateRepository
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
import com.nzd.antigravitypanel.ui.component.LocalBarBlurBackdrop
import com.nzd.antigravitypanel.ui.component.PredictiveNavBackHandler
import com.nzd.antigravitypanel.ui.component.PredictiveNavBackdrop
import com.nzd.antigravitypanel.ui.component.PredictiveNavLayer
import com.nzd.antigravitypanel.ui.component.appIconPainter
import com.nzd.antigravitypanel.ui.component.appVersionName
import com.nzd.antigravitypanel.ui.component.copyToClipboard
import com.nzd.antigravitypanel.ui.component.developerAvatarPainter
import com.nzd.antigravitypanel.ui.component.effect.BgEffectBackground
import com.nzd.antigravitypanel.ui.component.openUrl
import com.nzd.antigravitypanel.ui.component.rememberPredictiveNavLayerState
import com.nzd.antigravitypanel.ui.component.showToast
import com.nzd.antigravitypanel.ui.settings.SettingsItemMargin
import com.nzd.antigravitypanel.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog

/** 项目仓库。关于页的「GitHub」条目点开的就是它，summary 里去掉了协议头。 */
private const val PROJECT_URL = "https://github.com/Perpetual-Memories/antigravitypanel-android"

/** 开发者主页：卡片尾部是 `>` 而不是外链图标，语义上和"跳仓库"不一样。 */
private const val DEVELOPER_URL = "https://github.com/Perpetual-Memories"
private const val QQ_GROUP = "1004721478"

private val DISCLAIMER_LINES = listOf(
    "本应用使用的游戏素材（名称、图标、地图与武器数据等）版权均归《逆战：未来》运营方所有。",
    "本应用完全免费、非商业用途，不收取任何费用，也不含任何形式的广告与推广。",
    "本应用仅读取官方已开放的战绩接口数据，不涉及图像识别、内存注入或任何游戏数据修改。",
)

/**
 * 关于。
 *
 * 页面**上半部分（logo + 应用名 + 版本 + 动态渐变背景 + 渐隐）是 miuix 官方示例
 * `example/.../AboutPage.kt` 的搬运**，不是我自己拼的：
 * - 背景走 `BgEffectBackground`（AGSL 着色器画的四色斑流动渐变），
 * - 渐隐的进度 `scrollProgress` 由列表里那块 `logoSpacer` 的高度反推，
 * - 三个元素各自有错开的进度曲线（版本最先开始淡、图标最后），
 * - 顶栏只在完全滚过去之后才模糊、标题才显形。
 *
 * 下半部分（开发者 / 讨论 / 项目）是我们自己的内容，沿用 HyperIsland 的分组卡片。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    liquidGlassEnabled: Boolean = true,
    predictiveBackTranslation: Int = DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT.toInt(),
) {
    var showDisclaimer by remember { mutableStateOf(false) }
    var showReferences by remember { mutableStateOf(false) }
    val referenceNavState = rememberPredictiveNavLayerState()
    val version = remember { appVersionName() }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    // ---- 检查更新 ----
    val scope = rememberCoroutineScope()
    // 单独一条 HttpClient：它打的是 GitHub，和游戏接口不是一回事。
    // 共用 NzApi 那条连接只会互相拖超时，还会把 cookie 带到一个根本不需要它的域名上
    val updateRepository = remember { UpdateRepository() }
    DisposableEffect(Unit) {
        onDispose { updateRepository.close() }
    }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateCheckState?>(null) }

    fun checkUpdate() {
        // 连点两下不该发两次请求：请求本身没有副作用，但结果回来两次会连着弹两个窗
        if (checkingUpdate) return
        scope.launch {
            checkingUpdate = true
            // 异常在这里接住：null（没新版）和抛异常（没查成）要给两种不同的反馈
            val result = runCatching { updateRepository.fetchIfNewer(version) }
            checkingUpdate = false
            result
                .onSuccess { update ->
                    if (update == null) {
                        showToast("已经是最新版本")
                    } else {
                        updateState = UpdateCheckState.Available(version, update)
                    }
                }
                .onFailure { updateState = UpdateCheckState.Failed }
        }
    }

    /**
     * 去下载页。顺序是**先复制、再提示、最后切浏览器**：
     * 切成浏览器之后 Toast 就看不见了，而用户此刻正需要知道提取码已经在剪贴板里。
     */
    fun goDownload() {
        copyToClipboard(DOWNLOAD_EXTRACT_CODE, "提取码")
        showToast("提取码 $DOWNLOAD_EXTRACT_CODE 已复制，到下载页粘贴即可")
        openUrl(DOWNLOAD_PAGE_URL)
    }

    // 进度不是"滚了多少 dp"，而是"logoSpacer 被滚掉了多少比例"。
    // 用 spacer 的实际高度做分母，logo 内容多高都不会让曲线跑偏。
    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f

                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size)
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    // 顶栏要不要磨砂/显形，只在 1f 这一个临界点上翻转。
    // 放进 derivedStateOf 是为了让它按帧读数、但只在翻转时才触发 recompose。
    val barBackdrop = LocalBarBlurBackdrop.current
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(barBackdrop) {
        derivedStateOf { barBackdrop != null && scrollProgress == 1f }
    }

    BarBlurHost(enabled = liquidGlassEnabled) {
        Scaffold(
            topBar = {
                val barColor = if (blurActive) {
                    Color.Transparent
                } else {
                    if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
                }
                val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                    alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                )
                // 没滚到顶之前连底色都不铺：顶栏就那么浮在渐变上，标题还是隐形的
                BlurredBar(topGradient = true, enabled = blurActive) {
                    SmallTopAppBar(
                        title = "关于",
                        scrollBehavior = topAppBarScrollBehavior,
                        color = barColor,
                        titleColor = titleColor,
                        defaultWindowInsetsPadding = false,
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
                AboutContent(
                    topBarPadding = innerPadding.calculateTopPadding(),
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    scrollBehavior = topAppBarScrollBehavior,
                    lazyListState = lazyListState,
                    scrollProgress = { scrollProgress },
                    version = version,
                    updateChecking = checkingUpdate,
                    onCheckUpdate = ::checkUpdate,
                    onOpenReferences = { showReferences = true },
                    onShowDisclaimer = { showDisclaimer = true },
                )
            }
        }

        // ---- 三级页：引用 ----
        // 挂在关于页自己的 BarBlurHost 里（一级页那层 backdrop 就在手边），
        // 且必须放在 Scaffold **之后**，否则盖不住顶栏。
        // 返回只关这一层，关于页还在——HyperIsland 里引用是平级的二级页，
        // 我们多套一层是因为关于本身已经是二级页了。
        PredictiveNavBackdrop(state = referenceNavState, modifier = Modifier.fillMaxSize())
        PredictiveNavLayer(
            visible = showReferences,
            state = referenceNavState,
            maxTranslationPercent = predictiveBackTranslation.toLong(),
        ) {
            ReferencesScreen(
                onBack = { showReferences = false },
                liquidGlassEnabled = liquidGlassEnabled,
            )
        }
        PredictiveNavBackHandler(
            visible = showReferences,
            enabled = showReferences,
            state = referenceNavState,
            maxTranslationPercent = predictiveBackTranslation.toLong(),
            onDismiss = { showReferences = false },
        )
    }

    UpdateCheckDialogs(
        state = updateState,
        onDismiss = { updateState = null },
        onDownload = { goDownload() },
    )

    if (showDisclaimer) {
        WindowDialog(
            show = true,
            title = "版权及免责声明",
            onDismissRequest = { showDisclaimer = false },
        ) {
            Column {
                DISCLAIMER_LINES.forEach { line ->
                    Text(
                        text = line,
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Button(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(),
                    onClick = { showDisclaimer = false },
                ) {
                    Text("好的")
                }
            }
        }
    }
}

/**
 * 页面的实际内容：一块动态渐变背景打底，logo 那列**浮在**列表之上，
 * 列表用第一项 `logoSpacer` 把位置让出来。
 */
@Composable
private fun AboutContent(
    topBarPadding: Dp,
    bottomPadding: Dp,
    scrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: () -> Float,
    version: String,
    updateChecking: Boolean,
    onCheckUpdate: () -> Unit,
    onOpenReferences: () -> Unit,
    onShowDisclaimer: () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    // 这一层 backdrop 是给 logo 文字采样用的：它采的就是身后那块渐变
    val backdrop = if (isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }

    val scrollPadding = PaddingValues(top = topBarPadding, bottom = bottomPadding)
    val logoTopPadding = topBarPadding + 40.dp

    val isInDark = isInDarkTheme()
    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
            )
        }
    }
    val noiseCoefficient = BlurDefaults.NoiseCoefficient
    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }

    BgEffectBackground(
        dynamicBackground = true,
        isFullSize = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        alpha = { 1f - scrollProgress() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoTopPadding + 52.dp,
                    start = 12.dp,
                    end = 12.dp,
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 图标：88dp 白色圆角方里放一张 74dp 的启动图标
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val iconProgress = ((scrollProgress() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                        clip = true
                        shape = RoundedCornerShape(24.dp)
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    }
                    .background(Color.White),
            ) {
                Image(
                    modifier = Modifier.size(74.dp),
                    painter = appIconPainter(),
                    contentDescription = null,
                )
            }
            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        val projectNameProgress = ((scrollProgress() - 0.20f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .then(
                        if (backdrop != null) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                noiseCoefficient = noiseCoefficient,
                                colors = BlurDefaults.blurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                            )
                        } else {
                            Modifier
                        },
                    )
                    // 遮罩是按**这一层的边界**裁的，圆角就长在首尾两个字的角上。
                    // 把文字往里收一圈（比圆角半径大），字就完整了。
                    // 这一层 padding 必须在 textureBlur **之后**：放在前面等于把遮罩也缩了，
                    // 圆角照样压在字上。
                    .padding(horizontal = 20.dp),
                text = "反重力数据面板",
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val versionCodeProgress = ((scrollProgress() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                text = "v$version",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = 0.dp,
                end = 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 给上面那块浮着的 logo 让位。它的高度就是渐隐进度的分母
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoTopPadding -
                                scrollPadding.calculateTopPadding() + 126.dp,
                        ),
                )
            }

            item(key = "developer") {
                AboutSectionTitle("开发者")
                Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    DeveloperCard()
                }
            }
            item(key = "discussion") {
                AboutSectionTitle("讨论")
                Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    AboutAction(
                        title = "QQ 群",
                        icon = MiuixIcons.Community,
                        summary = QQ_GROUP,
                        endIcon = MiuixIcons.Copy,
                        endIconSize = 26.dp,
                    ) {
                        copyToClipboard(QQ_GROUP, "QQ 群号")
                    }
                }
            }
            item(key = "project") {
                AboutSectionTitle("项目")
                Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    // 检查更新放这一组的第一条：它是这一页里唯一一个"点下去会干活"的条目，
                    // 压在版权声明后面就没人找得到了
                    AboutAction(
                        title = "检查更新",
                        icon = MiuixIcons.Refresh,
                        // 这条摘要改成"说明这条路怎么走"而不是"当前版本号"：
                        // 版本号在上面标题那一栏已经说了，而 api.github.com 在国内时好时坏，
                        // 不提前说清"查不到多半是网络"，用户只会以为 app 坏了
                        summary = if (updateChecking) {
                            "正在检查…"
                        } else {
                            "使用 GitHub Releases API 检查更新，若无法正常获取请尝试准备合适工具。"
                        },
                    ) { onCheckUpdate() }
                    AboutAction(
                        title = "GitHub",
                        icon = MiuixIcons.Info,
                        summary = PROJECT_URL.removePrefix("https://"),
                        endIcon = MiuixIcons.Link,
                        endIconSize = 26.dp,
                    ) { openUrl(PROJECT_URL) }
                    AboutActionWithArrow(
                        title = "版权及免责声明",
                        icon = MiuixIcons.Info,
                    ) { onShowDisclaimer() }
                    // 具体参考了哪些项目不摊在这里，走三级页
                    AboutActionWithArrow(
                        title = "引用",
                        icon = MiuixIcons.Info,
                    ) { onOpenReferences() }
                }
            }
            item(key = "tail") {
                Spacer(modifier = Modifier.height(bottomPadding + 16.dp))
            }
        }
    }
}

/**
 * 开发者卡片。尾部的图标是 `>`（[ArrowRight]）而不是 🔗：
 * 它点开的是个人主页，语义上和"跳外链"不一样，HyperIsland 也是这么画的。
 */
@Composable
private fun DeveloperCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = { openUrl(DEVELOPER_URL) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = developerAvatarPainter(),
                contentDescription = "开发者头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.68f), CircleShape),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = "永恒的回忆",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "@Perpetual Memories",
                    modifier = Modifier.padding(top = 1.dp),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/**
 * 条目。HyperIsland `SettingsAction` 的搬运：`BasicComponent` 负责标题 / 副标题 /
 * 图标 / 点击反馈，外面那层 Card 由调用方给，一张 Card 里可以摞好几条。
 *
 * @param icon 前面的图标，`null` 时不画（引用页就不需要）。
 */
@Composable
internal fun AboutAction(
    title: String,
    icon: ImageVector? = null,
    summary: String? = null,
    endIcon: ImageVector? = null,
    endIconSize: Dp = 26.dp,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = icon?.let { image -> { AboutIcon(image) } },
        endActions = {
            if (endIcon != null) {
                Icon(
                    imageVector = endIcon,
                    contentDescription = null,
                    modifier = Modifier.size(endIconSize),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        },
        insideMargin = SettingsItemMargin,
        onClick = onClick,
    )
}

/** 带 `>` 的条目：点开的是下一级页面，不是外链。 */
@Composable
internal fun AboutActionWithArrow(
    title: String,
    icon: ImageVector,
    summary: String? = null,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = { AboutIcon(icon) },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        insideMargin = SettingsItemMargin,
        onClick = onClick,
    )
}

/**
 * 条目前面的图标。tint 是 `onBackground`（深色模式下白、浅色下黑），
 * 和 HyperIsland 的 `SettingsIcon` 一致 —— 图标是条目本身的一部分，不是次级信息。
 */
@Composable
private fun AboutIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.padding(end = 16.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

/**
 * 组标题。横向 12dp 是为了和卡片对齐（卡片本身也有 12dp 外边距），
 * 再叠上 `SmallTitle` 自己的 18dp，文字正好和卡片里的条目文字在同一条竖线上。
 */
@Composable
internal fun AboutSectionTitle(text: String) {
    SmallTitle(
        text = text,
        // 注意：Modifier.padding 只有 (all)、(horizontal, vertical)、(start, top, end, bottom)
        // 三种重载，没有 (top, horizontal) —— 混着传会编译不过
        modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp),
        insideMargin = AboutSectionTitleMargin,
    )
}

private val AboutSectionTitleMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
