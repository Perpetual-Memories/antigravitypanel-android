package com.nzd.antigravitypanel.ui.overview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nzd.antigravitypanel.data.repo.AccountProfile
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 胶囊外形。用百分比圆角而不是固定 dp：高度一改不用跟着改数字，两头始终是个半圆。 */
private val CHIP_SHAPE = RoundedCornerShape(percent = 50)

private val CHIP_AVATAR_SIZE = 30.dp

/**
 * 昵称那一列的最大宽度。
 *
 * 工具条的 navigationIcon 槽位是**不限制宽度**量的（整屏宽都归它），
 * 不设上限的话一个长昵称能把刷新按钮挤到看不见。
 */
private val CHIP_TEXT_MAX_WIDTH = 150.dp

private const val RIPPLE_ALPHA = 0.12f

private const val RIPPLE_GROW_DURATION = 300
private const val RIPPLE_FADE_IN_DURATION = 60
private const val RIPPLE_FADE_OUT_DURATION = 180

/**
 * 概览页顶栏左侧的账号胶囊：圆形头像 + 昵称 + 分区，整块可点。
 *
 * 数据源见 [AccountProfile] —— 头像和昵称只存在于 `center.game.detail` 的
 * `loginUserDetail` 里，是由 [com.nzd.antigravitypanel.data.repo.OverviewRepository] 顺手捞出来的。
 *
 * @param profile 头像 + 昵称（[AccountProfile.nickname] 拿不到时会退到 openid）。
 *   调用方负责先比对过 `openid` 跟当前凭证一致。
 * @param partition 分区展示名（QQ 区 / 微信区），见
 *   [com.nzd.antigravitypanel.data.credential.partitionLabelOf]。空串表示不知道，就只显示一行。
 * @param onClick 点整块区域，调用方一般是弹出「账号详细信息」。
 */
@Composable
fun AccountTopBarChip(
    profile: AccountProfile,
    partition: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            // 裁切必须在最外层：水波纹画以前的那层 graphicsLayer 会被它的形状裁到，
            // 不先裁的话那圈扩散圆会长成一个圆角矩形而不是胶囊
            .clip(CHIP_SHAPE)
            .clickable(
                interactionSource = interactionSource,
                // ⚠️ 用 Miuix 主题给的那套（LocalIndication），和顶栏的「每日首胜宝箱」
                // 是同一个 —— 手指一按下立刻整块泛起一层淡色，这正是"轻触就有反馈"的来源。
                // 别传 null：自己另画一层高亮代替不了它（见 [capsuleRipple] 的说明）。
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .capsuleRipple(interactionSource)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(url = profile.avatarUrl)
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.widthIn(max = CHIP_TEXT_MAX_WIDTH)) {
            Text(
                // 常态是昵称。服务端偶尔只给头像不给名字时退回 openid ——
                // 顶栏留一行空白比显示一串数字更让人以为坏了
                text = profile.nickname.ifBlank { profile.openid.ifBlank { "未知玩家" } },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (partition.isNotBlank()) {
                Text(
                    text = partition,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 圆形头像。
 *
 * ⚠️ 底圈**永远先画上**，图片叠在它上面：取图失败时（官方对部分账号根本不下发头像、
 * 或者 CDN 那一趟没回来）留下来的是一个灰圈 + 人像图标，而不是一块让人以为 app 坏了
 * 的空白。Coil 的 `AsyncImage` 失败是静默的，不给兜底就完全没有痕迹。
 */
@Composable
private fun AccountAvatar(
    url: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(CHIP_AVATAR_SIZE)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) {
            Icon(
                imageVector = MiuixIcons.Contacts,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * 胶囊里的按压水波纹（**只画扩散的那一圈**，整块高亮由 indication 负责）。
 *
 * 照 Material 的思路自己画：从手指按下那一点起一个圆往外扩到能盖住整块区域，抬手后淡出。
 * 不直接用系统那层是因为它是按节点边界铺色的，套在"头像 + 两行字"这种宽而矮的区域上
 * 没有"从手指那里扩散开"的意思；Miuix 的 [LocalIndication] 正好补上整块高亮那一半。
 *
 * ⚠️ **淡出那个进度必须是 State，不能是 Animatable**（这是"轻触没反应"的真正原因）：
 * Compose 的**绘制阶段不做状态观察**，只在 drawWithContent 里读 `Animatable.value`
 * 是**不会**触发重绘的。页面静止时轻触，唯一需要重绘的就是这一圈圆，
 * 结果没人要求重绘 → 完全看不到；长按之所以看得到，是被别的 invalidate 顺带刷出来的。
 * 所以这里用 [animateFloatAsState] —— 它在组合阶段被读，每帧重组都会重建
 * drawWithContent，重绘自然跟得上。扩散进度仍用 [Animatable]，因为按下那一刻
 * 要 `snapTo(0)` 让它重新从手指底下长出来。
 *
 * 也不去实现 `Indication` 接口：新版 compose 已经把它和 `IndicationInstance`
 * 一起标成了 ERROR 级废弃（官方要求迁到 `IndicationNodeFactory` + `Modifier.Node`），
 * 为了一圈水波纹去依赖那套实验 API 不划算，自己 draw 一遍更短也更可控。
 *
 * 圆是画在**内容之下**的（`drawContent` 之前），和 Material 一致：
 * 半透明的那层圆压在头像和文字上会把它们一起染灰。
 */
@Composable
private fun Modifier.capsuleRipple(interactionSource: InteractionSource): Modifier {
    val color = MiuixTheme.colorScheme.onSurface
    val pressPoint = remember { mutableStateOf(Offset.Unspecified) }
    // 扩散进度：按下那一刻要 snapTo(0)，Animatable 才有这个能力
    val grow = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        // 起扩散动画得靠 CoroutineScope：collect 那个 lambda 的接收者是 FlowCollector，
        // 里面直接写 launch 是找不到的（这不是 LaunchedEffect 的 body 本身）
        val animations = this
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressPoint.value = interaction.pressPosition
                    grow.snapTo(0f)
                    pressed = true
                    animations.launch {
                        grow.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = RIPPLE_GROW_DURATION,
                                easing = LinearOutSlowInEasing,
                            ),
                        )
                    }
                }

                is PressInteraction.Release,
                is PressInteraction.Cancel,
                    -> pressed = false
            }
        }
    }

    // 见上面的说明：这一份必须是 State，否则静止页面上轻触不会有重绘
    val visible by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (pressed) RIPPLE_FADE_IN_DURATION else RIPPLE_FADE_OUT_DURATION,
        ),
        label = "capsuleRippleFade",
    )

    return drawWithContent {
        if (visible > 0f) {
            val point = pressPoint.value
            val center = if (point == Offset.Unspecified) center else point
            // 半径取"圆心到四个角最远的那条"，保证水波纹走到最后能盖满整块区域
            val maxRadius = maxOf(
                (center - Offset.Zero).getDistance(),
                (center - Offset(size.width, 0f)).getDistance(),
                (center - Offset(0f, size.height)).getDistance(),
                (center - Offset(size.width, size.height)).getDistance(),
            )
            drawCircle(
                color = color,
                radius = maxRadius * grow.value,
                center = center,
                alpha = visible * RIPPLE_ALPHA,
            )
        }
        drawContent()
    }
}
