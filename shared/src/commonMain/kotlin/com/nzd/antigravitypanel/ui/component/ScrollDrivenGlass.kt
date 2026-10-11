package com.nzd.antigravitypanel.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nzd.antigravitypanel.ui.component.liquid.lens
import com.nzd.antigravitypanel.ui.component.liquid.vibrancy
import com.nzd.antigravitypanel.ui.theme.isInDarkTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import kotlin.math.PI
import kotlin.math.pow

/**
 * 滚动驱动的「玻璃显隐」进度，由 RootPage 提供、[TopBarGlassChip] 读取。
 *
 * 取值 0 = 完全透明（页面在顶部），1 = 完全显形（内容已滚过阈值）。
 */
internal val LocalTopBarGlassProgress = staticCompositionLocalOf<State<Float>> { AlwaysVisibleGlass }

/** 没有一级页提供时的默认值：二级页不长滚动玻璃，摆上去就该是完整的一颗。 */
private val AlwaysVisibleGlass: State<Float> = object : State<Float> {
    override val value: Float get() = 1f
}

/**
 * 内容要从顶部滚过多少才让顶栏按钮的玻璃显形。
 *
 * 取 10dp 而不是 0：按下到惯性起来那一帧会有 1~2px 的抖动，
 * 阈值压到 0 会让按钮在手指刚碰到屏幕时就闪一下。
 */
private val TOP_BAR_GLASS_TRIGGER = 10.dp

/**
 * 玻璃胶囊直径。照 NexioSchedule 的 `buttonHeight = 42.dp` ——
 * 比 Miuix `IconButtonDefaults.MinHeight`（40dp，也就是图标的触摸区）大一圈，
 * 玻璃外扩 1dp 才像"玻璃包着按钮"，等大的话看着就是按钮上贴了个圆片。
 */
internal val TOP_BAR_GLASS_SIZE = 42.dp

/**
 * 同一排里两颗玻璃之间的间距。
 *
 * 玻璃直径 42dp、里面图标 24dp，贴着放时两个圆相切，图标看着像一颗连体按钮；
 * 参考实现（NexioSchedule 的 `endAction`）用的是 8dp。
 */
internal val TOP_BAR_GLASS_GAP = 8.dp

private val TOP_BAR_GLASS_BLUR = 8.dp

/** 折射（lens）的两个参数，照参考实现的 `lens(8f.dp.toPx(), 24f.dp.toPx())`。 */
private val TOP_BAR_GLASS_LENS_HEIGHT = 8.dp
private val TOP_BAR_GLASS_LENS_AMOUNT = 24.dp

/**
 * 玻璃底色（叠在模糊之上的那一层纯色）。直接用参考实现写死的两个值：
 * 浅色 `#FAFAFA @0.76` / 深色 `#242424 @0.84`。
 *
 * 之前这里是从主题取 `surface`，但那种"跟着主题走的面色"压在模糊上偏厚，
 * 玻璃的通透感就没了；参考实现用的是**中性灰白 / 中性深灰**，
 * 只负责把底下的内容压暗到能读标题，颜色本身不带主题倾向。
 */
private val GLASS_CONTAINER_LIGHT = Color(0xFFFAFAFA).copy(alpha = 0.76f)
private val GLASS_CONTAINER_DARK = Color(0xFF242424).copy(alpha = 0.84f)

/**
 * 滚动驱动的顶栏按钮玻璃显隐进度：0 = 完全透明，1 = 完全显形。
 *
 * 机制照 NexioSchedule（github.com/HaoZai000/NexioSchedule）的
 * `CollapsibleTopAppBar` + `LiquidTopBarButton`：页面停在顶部时顶栏按钮**没有玻璃底**
 * （只有图标浮在内容上），内容一开始滚动就让玻璃材质随弹簧淡入，滑回顶部再淡出。
 * 逻辑上讲得通 —— 没滚动的时候内容是静止的，图标不需要和背景区分开；
 * 一旦内容从按钮底下穿过去，它才需要"浮"起来。
 *
 * ⚠️ 判据用 Miuix 自己的 `TopAppBarState.contentOffset`，不读列表的 ScrollState：
 *   1. 它和顶栏折叠是同一份量，大标题收没收、小标题出没出都读的这个；
 *   2. 三个一级页各自的列表（LazyColumn / LazyVerticalGrid / 嵌套网格）不用逐一适配；
 *   3. `SmallTopAppBar` 虽然把顶栏钉住（`heightOffsetLimit = 0`，高度不再折叠），
 *      但 Miuix 明确写了 "A pinned small bar retains contentOffset" ——
 *      概览页（摆了账号胶囊时走 SmallTopAppBar）照样能触发。
 *
 * @param scrollBehavior 一级页那一份滚动行为。传 null（二级页）时恒为 1，玻璃常驻。
 */
@Composable
internal fun rememberTopBarGlassProgress(
    scrollBehavior: ScrollBehavior?,
): State<Float> {
    val density = LocalDensity.current
    val triggerPx = with(density) { TOP_BAR_GLASS_TRIGGER.toPx() }
    // Nexio 那份 `OverScrollState` 带 `offset`（能判上下方向），Miuix 0.9.4 只暴露
    // `isOverScrollActive`（javap `OverScrollState`：仅一个 Boolean 字段）。
    // 配合 contentOffset >= 0（在顶部）一起看，等价于 Nexio 的「顶部向下回弹」。
    val overScrollState = LocalOverScrollState.current
    val progress = remember { Animatable(if (scrollBehavior == null) 1f else 0f) }
    LaunchedEffect(scrollBehavior, triggerPx, progress, overScrollState) {
        val state = scrollBehavior?.state ?: return@LaunchedEffect
        // ⚠️ 用 snapshotFlow 订阅，不要拿 derivedStateOf 读进组合：调用方是 RootPage，
        // 它包着 Scaffold 和整张页面，在它身上读布尔状态会让**整页**在每次翻面时重组一次。
        // snapshotFlow 在组合之外订阅，翻面只驱动这几颗小小的玻璃。
        snapshotFlow {
            glassShouldShow(
                contentOffset = state.contentOffset,
                triggerPx = triggerPx,
                overScrollActive = overScrollState.isOverScrollActive,
            )
        }
            .distinctUntilChanged()
            .collect { visible ->
                progress.animateTo(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = glassProgressSpec(visible),
                )
            }
    }
    return progress.asState()
}

/**
 * 顶栏玻璃该不该显形。**完整照 NexioSchedule `CollapsibleTopAppBar` 的
 * `showButtonShadow`**（两条判据，一个不多一个不少）：
 *
 * 1. 正常滚动：内容从顶部滚过阈值（`contentOffset < -10dp`）。
 * 2. **顶部向下回弹**：`contentOffset >= 0`（内容停在顶部）且正在 overscroll。
 *
 * 第 2 条就是「内容不够长的页面」的正解：那种页面压根滚不动，`contentOffset`
 * 永远是 0，光靠第 1 条的话返回键会一直是个没样式的箭头。加了第 2 条之后，
 * 手指在页面顶部往下拉（overscroll）时玻璃照样淡入 —— 和长页面的行为完全一致，
 * 不用为短页面开「常显」的后门。
 *
 * ⚠️ Miuix 0.9.4 的 `OverScrollState` 没有 `offset` 字段（Nexio 那份是 fork 里加的），
 * 拿不到「向下」这个方向，只有 `isOverScrollActive`。但配上第 2 条的
 * `contentOffset >= 0` 之后，能进这条分支的只有「停在顶部 + 越界」这一种情况，
 * 语义等价。
 */
private fun glassShouldShow(
    contentOffset: Float,
    triggerPx: Float,
    overScrollActive: Boolean,
): Boolean {
    if (contentOffset < -triggerPx) return true
    return contentOffset >= 0f && overScrollActive
}

/**
 * 一颗顶栏玻璃的位置。
 *
 * 玻璃**画在 Miuix 顶栏外面**那层不被裁的 overlay 上（顶栏自带 `clipToBounds()`，
 * 会把溢出到 42dp 之外的阴影切成一条直边），但位置得照顶栏槽位里那个
 * [TopBarGlassPlaceholder] 报上来的坐标摆 —— 所以要有这么个中间人。
 */
// ⚠️ 这个类是 public：BlurredBar 是 public 的，它的参数类型不能是 internal
// （编译器会报 "'public' function exposes its 'internal' parameter type"）。
class TopBarGlassAnchor {
    private var _rootOffset by mutableStateOf(IntOffset.Zero)
    private var _rootSize by mutableStateOf(IntSize.Zero)
    private var _placed by mutableStateOf(false)

    /** 这组玻璃该出现的根坐标（左上角）。 */
    val rootOffset: IntOffset get() = _rootOffset

    /** 占位量出来的尺寸。右侧那组要按**右边缘**对齐，光有左上角不够。 */
    val rootSize: IntSize get() = _rootSize

    /** 占位的右边缘（根坐标）。右侧玻璃按它对齐，才能贴着 Miuix 的 `ActionIconPadding`。 */
    val rightEdge: Int get() = _rootOffset.x + _rootSize.width

    /** 占位还没量过时为 false：那时先不画，免得先在错误位置闪一下。 */
    val placed: Boolean get() = _placed

    fun update(rootPosition: Offset, size: IntSize) {
        _rootOffset = IntOffset(rootPosition.x.roundToInt(), rootPosition.y.roundToInt())
        _rootSize = size
        _placed = true
    }
}

/**
 * 顶栏槽位里的占位：撑住标题的位置，同时把"这颗玻璃该摆在哪"报给 [anchor]。
 *
 * 尺寸必须和真玻璃一致（默认 [TOP_BAR_GLASS_SIZE]），标题才不会挪位。
 */
@Composable
internal fun TopBarGlassPlaceholder(
    anchor: TopBarGlassAnchor,
    modifier: Modifier = Modifier,
    width: Dp = TOP_BAR_GLASS_SIZE,
) {
    Box(
        modifier = modifier
            .size(width = width, height = TOP_BAR_GLASS_SIZE)
            .onGloballyPositioned { anchor.update(it.positionInRoot(), it.size) },
    )
}

private const val GLASS_SHOW_RESPONSE = 0.6f
private const val GLASS_HIDE_RESPONSE = 0.4f

/**
 * 显 / 隐走两条不同的弹簧：出现慢一点（response 0.6）、消失快一点（response 0.4）。
 *
 * Folme 的 response 说的是"多久收束"，折成 Compose 的刚度约等于 (2π / response)²；
 * dampingRatio = 1 是临界阻尼、不回弹 —— 按钮底下一层背景如果收尾时在晃，一眼就看得出来。
 */
private fun glassProgressSpec(showing: Boolean): AnimationSpec<Float> {
    val response = if (showing) GLASS_SHOW_RESPONSE else GLASS_HIDE_RESPONSE
    val stiffness = (2.0 * PI / response).pow(2.0).toFloat()
    return spring(dampingRatio = 1f, stiffness = stiffness)
}

/**
 * 玻璃底下那圈阴影。**照 NexioSchedule 的 `LiquidTopBarButton` 手画**：
 * `android.graphics.Path` 画一个**挖空的环**（外圆 CW + 内圆 CCW）+ `BlurMaskFilter` 柔化，
 * 只画圆外那半圈，阴影不会从半透明的玻璃底下透出来。
 *
 * ⚠️ 不能用 `Modifier.dropShadow` / `Modifier.shadow`：那两个是**图层阴影**
 * （`SimpleDropShadowNode.draw()` 末尾 `Painter.draw(painter, drawScope, size)`，
 * 阴影由 `ShadowContext` 按节点尺寸渲染）。
 *
 * ⚠️ 更关键的是：阴影**必须画在淡入淡出的那颗 graphicsLayer 之外**。
 * 任何带 alpha 的 `graphicsLayer` 都会把内容渲进一块**节点尺寸（40×40）的离屏层**，
 * 画到边界外的阴影会被裁成一个方块 —— 这才是"闪一下方形阴影"的真正原因
 * （换 `dropShadow` 之前也是这个，不是它的锅）。所以这里把阴影挂在外层 Box 上，
 * 材质那层的 alpha 走 `drawBackdrop` 自己的 [layerBlock]。
 *
 * @param glassAlpha 显隐进度。**只缩放阴影的大小**（不透明度恒定），
 *   和参考实现一致：progress 为 0 时环宽和模糊半径都收到 0，自然就看不见了。
 */
internal expect fun Modifier.glassDropShadow(glassAlpha: Float, darkTheme: Boolean): Modifier

/**
 * 顶栏按钮的圆形玻璃底。
 *
 * 只负责画背景，**不接管任何手势**：里面还是原来的 `IconButton` / 筛选菜单，
 * 它们的点击、涟漪、下拉锚点一个都不用改 —— 层和层之间不抢事件。
 *
 * 淡入淡出的只有玻璃这一层，**图标始终保持全可见**：整颗节点连图标一起透明的话，
 * 未滚动时顶栏看上去就像少了几个按钮 —— 参考实现也是把材质层单独撇出来的。
 */
@Composable
internal fun TopBarGlassChip(
    modifier: Modifier = Modifier,
    size: Dp = TOP_BAR_GLASS_SIZE,
    progress: State<Float> = LocalTopBarGlassProgress.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val glassAlpha = progress.value
    val darkTheme = isInDarkTheme()
    // backdrop 为 null 有两种情况：设备拿不到 RuntimeShader，或者用户关了磨砂。
    // 两种都退化成一块半透明圆片 —— 遮挡力够用，只是没有模糊。
    val backdrop = LocalBarBlurBackdrop.current
    val containerColor = if (darkTheme) GLASS_CONTAINER_DARK else GLASS_CONTAINER_LIGHT
    // ⚠️ DrawBackdropElement 的字段是**按引用比较**的，而它的 update() 结尾无条件
    // invalidateDrawCache()：这部分 lambda 每次重组都新建的话，动画期间每个玻璃节点
    // 都会被推着 update 一整轮。底色很少变，按它当 key 记住就能常驻。
    val onDrawSurface: (DrawScope.() -> Unit)? = remember(containerColor) {
        { drawCircle(containerColor) }
    }
    // 边缘光：参考实现是用 `edgeLight(...)` 单独挂一层描边高光，
    // Miuix 把这事做进了 `drawBackdrop` 的 highlight 里，
    // 玻璃类小圆片对应的预设就是 GlassStrokeSmall（有 light / dark 两版）。
    // 同样必须记住：它是按引用比较的字段之一。
    val highlight: BackdropEffectScope.() -> Highlight = remember(darkTheme) {
        {
            if (darkTheme) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight
        }
    }

    Box(
        // 阴影挂在外层：**不能**放在下面材质那颗 graphicsLayer 里 ——
        // 那层是节点尺寸（42×42）的离屏层，画到边界外的阴影会被裁成一个方块。
        modifier = modifier
            .size(size)
            .glassDropShadow(glassAlpha = glassAlpha, darkTheme = darkTheme),
        contentAlignment = Alignment.Center,
    ) {
        val material = if (backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = GlassShapeBlock,
                effects = GlassEffects,
                highlight = highlight,
                // 材质自己那一层的 alpha：参考实现就是这么写的（`layerBlock` 只写 alpha）。
                // 这层是**材质自己**的大小，它不往边界外画，所以不会触发上面那个裁剪问题。
                layerBlock = { alpha = glassAlpha },
                onDrawSurface = onDrawSurface,
            )
        } else {
            Modifier
                .graphicsLayer { alpha = glassAlpha }
                .background(color = containerColor, shape = CircleShape)
        }
        Box(modifier = Modifier.size(size).then(material))
        content()
    }
}

/**
 * 二级页顶栏左侧的返回按钮，套同一颗 [TopBarGlassChip]。
 *
 * ⚠️ 显隐判据**和右侧那些按钮完全一致**：页面停在顶部时没有玻璃，内容滚过阈值
 * 才淡入。所以这里必须显式收一份 [scrollBehavior] —— 二级页不像一级页（`AppContent`
 * 的 `RootPage`）那样在外层 Provide [LocalTopBarGlassProgress]，不传的话拿到的
 * 就是那个恒为 1 的默认值，
 * 玻璃会变成常驻，和右侧对不上。
 *
 * 判据本身见 [rememberTopBarGlassProgress]：读的是 `TopAppBarState.contentOffset`
 * （`SmallTopAppBar` 虽然把顶栏钉住了，但仍保留 contentOffset，所以照样触发）。
 *
 * @param scrollBehavior 这一页传给顶栏的那份滚动行为。传 null 时退回读
 *   [LocalTopBarGlassProgress]（一级页已经在外层 Provide 过一份）。
 */
@Composable
internal fun TopBarGlassBackButton(
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior? = null,
    modifier: Modifier = Modifier,
) {
    val progress = if (scrollBehavior != null) {
        rememberTopBarGlassProgress(scrollBehavior)
    } else {
        LocalTopBarGlassProgress.current
    }
    TopBarGlassChip(modifier = modifier, progress = progress) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = "返回",
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * `drawBackdrop` 的 shape / effects 是**按引用比较**的：lambda 每次重组都新建的话，
 * 那个节点会跟着 update → invalidateDraw，糊的采样层等于每帧重新录一遍。
 * 这两个都不依赖组合状态，提到顶层当常量就永远不会变。
 */
private val GlassShapeBlock: () -> Shape = { CircleShape }

/**
 * 玻璃的材质管线，和参考实现 `LiquidTopBarButton` 的三步一致：
 * `vibrancy()` 提饱和 → `blur(8dp)` → `lens(...)` 加边缘折射。
 *
 * 之前少了 lens：模糊是有的，但圆边没有那圈折射，看着就是"一块糊了的圆"，
 * 不像玻璃 —— lens 带来的边缘弯折才是液态玻璃最显眼的特征。
 */
private val GlassEffects: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(TOP_BAR_GLASS_BLUR.toPx())
    lens(TOP_BAR_GLASS_LENS_HEIGHT.toPx(), TOP_BAR_GLASS_LENS_AMOUNT.toPx())
}
