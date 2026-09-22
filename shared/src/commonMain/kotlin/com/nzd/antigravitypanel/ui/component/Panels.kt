package com.nzd.antigravitypanel.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 卡片上的一行「小标题 + 大数字」。
 *
 * 结构和 HyperIsland 概览页的 `StatCard` 一致：标题 13sp 弱化、数值 24sp 半粗，
 * 内边距 14dp。照搬是因为这套比例在 MIUI/HyperOS 上是被验证过的。
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            // 数值会变长：「5 场」只有 3 个字符，「20 小时 50 分」有 9 个。
            // 固定 24sp 时长文案会被裁掉一个"分"字，所以让字号在 14–24sp 之间自适应，
            // 保证整串始终落在一行里。
            Text(
                text = value,
                modifier = Modifier.padding(top = 2.dp),
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 14.sp,
                    maxFontSize = 24.sp,
                    stepSize = 1.sp,
                ),
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }

    if (onClick == null) {
        Card(
            modifier = modifier,
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            pressFeedbackType = PressFeedbackType.Tilt,
            onClick = onClick,
        ) { content() }
    }
}

/**
 * 概览里的四行指标。标题弱化、内容醒目。
 *
 * 垂直留白交给外面的网格排，组件自己不带 bottom padding。
 */
@Composable
fun InfoText(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    // 不自带 bottom padding：唯一的调用方 InfoGrid 只在行与行之间加间距，
    // 每格再各带一份会在卡片底部堆出一段空白
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = content,
            modifier = Modifier.padding(top = 2.dp),
            style = MiuixTheme.textStyles.headline1,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 两列指标网格。概览的近五场统计、签到页三块的状态数字都用它。
 *
 * 行距只加在**行与行之间**：最后一行再垫一份 bottom，卡片底部就会多出一整块空白
 * （原来每格自己还带 16dp，等于末行下面堆了 28dp），看着像"多出来一块白色区域"。
 */
@Composable
fun InfoGrid(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val rows = items.chunked(2)
    Column(modifier = modifier) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = if (index != rows.lastIndex) {
                    Modifier.padding(bottom = 12.dp)
                } else {
                    Modifier
                },
            ) {
                for ((title, value) in row) {
                    InfoText(
                        title = title,
                        content = value,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 列表里的条目行：主标题 + 右侧摘要 + 可选尾图标。
 *
 * HyperIsland 的 `SettingsAction` 是 internal，这里按同样视觉重抄一份。
 */
@Composable
fun ActionRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    endIcon: androidx.compose.ui.graphics.vector.ImageVector? = MiuixIcons.Link,
    endIconSize: androidx.compose.ui.unit.Dp = 26.dp,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (endIcon != null) {
                Icon(
                    imageVector = endIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(endIconSize),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

/**
 * 一个「图标 + 文案」的空状态 / 提示块。
 */
@Composable
fun HintBlock(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = text,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 内容区统一外边距。四个 Tab 都用它，保证横向留白一致。 */
val ScreenContentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp)

/** 卡片圆角。Miuix 的 Card 默认圆角偏小，全站统一到 16dp 更贴 HyperOS。 */
val CardCornerRadius = 16.dp
