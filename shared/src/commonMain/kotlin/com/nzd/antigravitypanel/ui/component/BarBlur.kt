package com.nzd.antigravitypanel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState
import kotlin.math.roundToInt

/**
 * 顶栏磨砂。整份是 HyperIsland `compose/component/BlurBars.kt` 的搬运行，
 * 只留下本项目用到的三块：`BarBlurHost` / `BarBackdropContent` / `BlurredBar`。
 *
 * 它和"自己画一层半透明色"的区别在于 **backdrop**：
 * [BarBlurHost] 开一块离屏 layer，[BarBackdropContent] 把页面内容画进去，
 * [BlurredBar] 再从这块 layer 上采样做模糊。
 * 所以**少了 [BarBackdropContent] 那一环，顶栏糊的就是一层纯色**——
 * 离屏 layer 里只有 [BarBlurHost] 铺的那张 `surfaceColor` 底，糊出来当然还是色块。
 */
internal val LocalBarBlurEnabled = staticCompositionLocalOf { false }

internal val LocalBarBlurBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 悬浮底栏占掉的高度（含它自己的下边距）。
 *
 * 一级页必须自己在 `contentPadding` 上补这一段：底栏是浮在内容之上的，
 * 宿主 Scaffold 给一级页的 padding 里没有它（一级页自己带顶栏，一看就明白的那套
 * 「Scaffold 替你把上下都算好」在这里不成立）。
 */
internal val LocalRootBottomBarPadding = staticCompositionLocalOf { 0.dp }

/**
 * 磨砂的宿主：开离屏 layer 并把 [content] 整个包进 [Box]。
 *
 * 只有 RuntimeShader 可用时才建 backdrop，否则后面一律退化成不透明表面色——
 * 不这么做的话，低端机上 `progressiveTextureBlur` 拿不到采样结果，
 * 顶栏会变成一块实色，比直接退化还难看。
 *
 * @param captureForEffects 关掉磨砂但仍然要 backdrop 的场景（比如返回手势的虚化层）。
 */
@Composable
fun BarBlurHost(
    enabled: Boolean,
    captureForEffects: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if ((enabled || captureForEffects) && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    // ⚠️ 每页一个 OverScrollState，并在这里 Provide 下去：
    // 顶栏（玻璃读它）和页面内容里的 `.overScrollVertical()`（写它）必须拿到**同一个实例**，
    // 「顶部下拉回弹也让玻璃显形」那条判据才成立。
    // 不显式 Provide 的话两边都会落到 `compositionLocalOf` 那个**全局共享的默认实例**上，
    // 二级页盖在一级页上时，下面那页的 overscroll 会把上面那页的玻璃也点亮。
    // 做法照 NexioSchedule `DocumentPageScaffold` 的 `pageOverScrollState`。
    val pageOverScrollState = remember { OverScrollState() }
    CompositionLocalProvider(
        LocalBarBlurEnabled provides enabled,
        LocalBarBlurBackdrop provides backdrop,
        LocalOverScrollState provides pageOverScrollState,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * 把页面内容接进 backdrop。
 *
 * 必须包在 **铺满整屏** 的那一层上，而且它里面不能再拿顶栏高度去 padding——
 * 内容要一直画到屏幕顶边，滚动时从顶栏底下穿过去，顶栏才有东西可糊。
 * 页面的"躲开顶栏"靠列表自己的 `contentPadding` 实现，不是靠裁掉这块区域。
 */
@Composable
fun BarBackdropContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalBarBlurBackdrop.current
    Box(
        modifier = modifier.then(
            if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        ),
    ) {
        content()
    }
}

/**
 * 顶栏那一层。[topGradient] 为真时上满下空，底缘不会切出一条硬边。
 *
 * 糊层比工具条**再往下多铺** [TOP_BAR_BLUR_OVERHANG] 一段，照 NexioSchedule 的
 * `ProgressiveBlurTopBar`：它把糊层做成 80dp + 状态栏，而工具条只有 52dp + 状态栏
 * （Miuix 的 `CollapsedHeight`）—— 多出来的那截就是"渐进"的部分，模糊半径在这一段里
 * 一路收到 0，糊层自己溶进下面的页面内容，不会出现"工具条底缘一条硬边"。
 *
 * ⚠️ 用 `Layout` 而不是 `Box` + `Column`：多铺的这段**不能**算进 Scaffold 的 topBar
 * 高度 —— 那个高度会变成页面内容拿到的 top padding，整个列表会跟着往下挪。
 * 这里报出去的尺寸仍然是工具条那一份，糊层只是**画**出了边界；顶栏在 Scaffold 里
 * 是最后 place 的（画在页面内容之上，见 Miuix `Scaffold` 的 place 顺序），
 * 所以多出来的部分照样压在内容上，正是想要的。
 *
 * @param enabled 关掉时**连底色都不铺**（关于页要的就是这个：没滚到顶之前顶栏
 *   完全透明地浮在渐变上，见 miuix 示例 `AboutPage` 的 `barColor`）。
 *   只有 [enabled] 为真、但设备拿不到 backdrop 时，才退化成不透明表面色。
 */
@Composable
fun BlurredBar(
    topGradient: Boolean = false,
    enabled: Boolean = true,
    /**
     * 顶栏**左侧**那组玻璃（返回键）的定位锚点。由 [content] 槽位里那个
     * [TopBarGlassPlaceholder] 上报位置 —— 原因见 [TopBarGlassLayer]。
     */
    startGlassAnchor: TopBarGlassAnchor? = null,
    /** 顶栏左侧那组玻璃本身。画在顶栏**外面**那层不被裁的 overlay 上。 */
    startGlass: (@Composable () -> Unit)? = null,
    /** 顶栏**右侧**那组玻璃（刷新 / 筛选）的锚点。 */
    endGlassAnchor: TopBarGlassAnchor? = null,
    /** 顶栏右侧那组玻璃本身。 */
    endGlass: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val overhangPx = with(LocalDensity.current) { TOP_BAR_BLUR_OVERHANG.roundToPx() }
    Layout(
        content = {
            Box(
                modifier = Modifier.barBlurBackground(
                    shape = RectangleShape,
                    topGradient = topGradient,
                    enabled = enabled,
                ),
            )
            Box {
                content()
            }
            // ⚠️ 玻璃层是**独立槽位**，不能塞进上面那颗 Box：它要 fillMaxSize 才能
            // 撑出一个"不被裁"的绘制区，塞进 Box 的话 Box 会被它撑到 constraints 的最大高度，
            // 而这个高度就是 Scaffold 拿去当 top padding 的值 —— 整个列表会被顶到屏幕外。
            TopBarGlassLayer(
                startGlassAnchor = startGlassAnchor,
                startGlass = startGlass,
                endGlassAnchor = endGlassAnchor,
                endGlass = endGlass,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val bar = measurables[BAR_CONTENT_SLOT].measure(constraints)
        val blurHeight = bar.height + overhangPx
        // 糊层和玻璃层都按"工具条 + 外扩"这份高度量，量出来的高度**不参与** layout()
        // 报出去的尺寸 —— 报出去的仍然是工具条那一份。
        val overhangConstraints = constraints.copy(minHeight = blurHeight, maxHeight = blurHeight)
        val blur = measurables[BLUR_LAYER_SLOT].measure(overhangConstraints)
        val glass = measurables[GLASS_SLOT].measure(overhangConstraints)
        layout(bar.width, bar.height) {
            blur.place(0, 0)
            bar.place(0, 0)
            // 最后 place：玻璃画在顶栏之上。
            glass.place(0, 0)
        }
    }
}

private const val BLUR_LAYER_SLOT = 0
private const val BAR_CONTENT_SLOT = 1
private const val GLASS_SLOT = 2

/**
 * 顶栏玻璃按钮那一层。**必须画在 Miuix 顶栏之外** —— 这是 [TOP_BAR_GLASS_SIZE] 之外
 * 那圈阴影能不能画全的关键：
 *
 * Miuix 的 `TopAppBar` / `SmallTopAppBar` 内部都挂了 `clipToBounds()`
 * （javap `TopAppBarKt`：`windowInsetsPadding(systemBars.only(Top)).clipToBounds()`），
 * 按钮放在它的 `navigationIcon` / `actions` 槽位里时，溢出到 42dp 之外的阴影会被
 * 裁成一条直边。这一层是顶栏的**兄弟节点**，不在那条裁切线里面，所以能画出去。
 *
 * 位置**不靠算**：顶栏槽位里那个 [TopBarGlassPlaceholder] 会把自己量出来的根坐标
 * 报进 [TopBarGlassAnchor]，这一层照着那个坐标摆。好处是不用猜状态栏多高、
 * 工具条那行是 52dp 还是 50dp、Miuix 的左右留白是多少 —— 关于页还把
 * `defaultWindowInsetsPadding` 关掉了，算的话必然错一个。
 *
 * ⚠️ 槽位里那个占位不能省：`navigationIcon` 一空，SmallTopAppBar 的标题就贴到
 * 16dp 上去了，会被按钮压住。
 */
@Composable
private fun TopBarGlassLayer(
    startGlassAnchor: TopBarGlassAnchor?,
    startGlass: (@Composable () -> Unit)?,
    endGlassAnchor: TopBarGlassAnchor?,
    endGlass: (@Composable RowScope.() -> Unit)?,
) {
    // ⚠️ 这层**必须始终发射一个节点**：BlurredBar 的 Layout 按固定下标取 measurable，
    // 条件性不发射会让 GLASS_SLOT 没了，取的时候直接越界。空场景就摆一颗空 Box。
    // ⚠️ 这两个是 State 而不是普通变量：位移在 offset 的 lambda（布局期）里读它们，
    // 位置变了只重新布局那一颗节点，不会把整组按钮重组一遍。
    val selfRoot = remember { mutableStateOf(IntOffset.Zero) }
    val selfWidth = remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            // ⚠️ 这层**不裁**：玻璃那圈阴影要溢出到顶栏外面去。
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                selfRoot.value = IntOffset(p.x.roundToInt(), p.y.roundToInt())
                selfWidth.value = coords.size.width
            },
    ) {
        if (startGlass != null && startGlassAnchor != null) {
            // 左侧（返回键）：按**左边缘**对齐，占位左上角就是 Miuix 的 NavigationIconPadding。
            GlassAtAnchor(
                anchor = startGlassAnchor,
                selfRoot = selfRoot,
                selfWidth = selfWidth,
                alignEnd = false,
            ) {
                startGlass()
            }
        }
        if (endGlass != null && endGlassAnchor != null) {
            // 右侧（刷新 / 筛选）：按**右边缘**对齐。
            // 占位只有一颗玻璃宽（42dp），而这一组可能是两颗（92dp）——
            // 按左边缘摆的话它会往左戳出去压住标题，按右边缘摆才和
            // Miuix 自己放 actions 的方式一致（Row 靠 End，右留白 ActionIconPadding）。
            GlassAtAnchor(
                anchor = endGlassAnchor,
                selfRoot = selfRoot,
                selfWidth = selfWidth,
                alignEnd = true,
            ) {
                endGlass()
            }
        }
    }
}

@Composable
private fun GlassAtAnchor(
    anchor: TopBarGlassAnchor,
    selfRoot: State<IntOffset>,
    selfWidth: State<Int>,
    alignEnd: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    if (!anchor.placed) return
    Row(
        modifier = (if (alignEnd) Modifier.fillMaxWidth() else Modifier)
            .glassOffset(anchor = anchor, selfRoot = selfRoot, selfWidth = selfWidth, alignEnd = alignEnd),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        content()
    }
}

/**
 * 把这一组玻璃摆到顶栏槽位里那个占位所在的位置。
 *
 * ⚠️ 用 `Modifier.layout` 自己 place，不用 `Modifier.offset`：后者会按位移去
 * **改测量约束**（`constraints.offset(-dx, -dy)`），右侧那组是靠
 * `fillMaxWidth` + 负位移对齐右边缘的，约束一改它就对不齐了。
 *
 * ⚠️ 坐标必须在**布局期**读（不能提到组合期）：大标题收起来的时候顶栏整体在上移，
 * 占位每帧都在动，组合期读就等于让整组按钮（首胜宝箱 / 筛选菜单）跟着每帧重组一遍；
 * 布局期读只重新布局这一颗节点。
 */
private fun Modifier.glassOffset(
    anchor: TopBarGlassAnchor,
    selfRoot: State<IntOffset>,
    selfWidth: State<Int>,
    alignEnd: Boolean,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val root = selfRoot.value
    val dy = anchor.rootOffset.y - root.y
    val dx = if (alignEnd) {
        // 按**右边缘**对齐：占位只有一颗玻璃宽（42dp），这一组可能是两颗（92dp），
        // 按左边缘摆会往左戳出去压住标题。
        anchor.rightEdge - (root.x + selfWidth.value)
    } else {
        anchor.rootOffset.x - root.x
    }
    layout(placeable.width, placeable.height) {
        placeable.place(dx, dy)
    }
}

@Composable
fun Modifier.barBlurBackground(
    shape: Shape,
    topGradient: Boolean = false,
    enabled: Boolean = true,
): Modifier {
    val backdrop = LocalBarBlurBackdrop.current
    val blurEnabled = LocalBarBlurEnabled.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    return if (!enabled) {
        Modifier
    } else if (blurEnabled && backdrop != null) {
        val blendColors = listOf(
            BlendColorEntry(color = surfaceColor.copy(alpha = TOP_BAR_SURFACE_ALPHA)),
        )
        progressiveTextureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = BAR_BLUR_RADIUS,
            gradient = TOP_BAR_PROGRESSIVE_BLUR,
            colors = BlurDefaults.blurColors(blendColors = blendColors),
        )
    } else {
        background(color = surfaceColor, shape = shape)
    }
}

/**
 * 顶栏的渐进糊：顶边满强度，一路收到底边为 0（半径到 0 就是"无损清端"，
 * 糊层在那里直接让位给真实内容，所以底缘没有硬边）。
 *
 * 参数照 NexioSchedule 的 `ProgressiveBlurTopBar` 对齐：
 * - `curve = 1f`：Miuix 的 ramp 是 `pow(smoothstep(t), curve)`，取 1 就是它的
 *   smoothstep 原曲线 —— 和 Nexio shader 里的 `1 - smoothstep(0,1,t)` 是同一条。
 *   之前那个 1.25 把变化全推到末端，看起来就像"糊到底突然切断"。
 * - `startFraction = 0f`：顶边就是满强度（原来是 0.12，最上面那一条反而弱一点）。
 */
private val TOP_BAR_PROGRESSIVE_BLUR = ProgressiveBlur.Top

/**
 * 糊层比工具条多往下铺的高度。NexioSchedule 的糊层是 80dp + 状态栏、
 * 工具条是 52dp + 状态栏，差值就是这 28dp。
 */
private val TOP_BAR_BLUR_OVERHANG = 28.dp

/** NexioSchedule 的 `maxRadius` 是 12dp（Miuix 自己的默认半径是 20dp）。 */
private const val BAR_BLUR_RADIUS = 12f

/**
 * 糊层叠的那层表面色。
 *
 * NexioSchedule 把"可读性"拆成了两笔：糊层里只掺 0.2 的 tint，另有一层
 * `surface` 从 0.85 渐变到透明的遮罩盖在上面。本项目没有那层遮罩，
 * 可读性全靠这一个数，所以留在 0.66 —— 改成 0.2 的话标题压在卡片上会看不清。
 */
private const val TOP_BAR_SURFACE_ALPHA = 0.66f
