package com.nzd.antigravitypanel.ui.qrlogin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.ui.component.QrCodeIcon
import com.nzd.antigravitypanel.ui.component.appMarkPainter
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/**
 * 扫码页左下角那两颗胶囊：「应用宝电脑版渠道扫码登录」和「反重力登录」。
 *
 * 形状照微信「我的二维码」那张参考图：整颗是**全圆角**的胶囊，左边一个图标，
 * 右边两行——第一行名字、第二行小字说明。颜色不跟主题走，一律白字深色底：
 * 底下是相机画面，明暗完全不可控，跟主题走的话浅色主题在亮画面里会直接糊掉。
 *
 * 两颗**上下排列**而不是横排：宽度被钉在屏幕的一半以内，横排连名字都放不下。
 * 竖排的整块宽度都能给文字，代价是长名字 / 长描述会折成两三行 —— 这是设计上
 * 选过的（见 [ScannerCapsule] 的 `maxLines` 说明）。
 *
 * @param onShowYybQr 点「应用宝电脑版渠道扫码登录」，宿主先关相机再出示授权二维码。
 * @param onShowShareQr 点「反重力登录」。真正的换页由宿主做（要先把相机放下）。
 */
@Composable
fun ScannerActionCapsules(
    onShowYybQr: () -> Unit,
    onShowShareQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScannerCapsule(
            title = "应用宝电脑版渠道扫码登录",
            description = "使用另一台设备的微信扫描二维码",
            onClick = onShowYybQr,
            icon = {
                Icon(
                    imageVector = QrCodeIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        ScannerCapsule(
            title = "反重力登录",
            description = "出示二维码以供其他设备上的反重力数据面板登录",
            onClick = onShowShareQr,
            icon = {
                // 应用图标（去掉白底板的字形版）刷成纯白。ic_app 那张带白底的整图
                // 在这里会被缩成一枚白色圆角方块，看不出是什么。
                // ⚠️ 30dp 而不是 24dp：ic_app_mark 的字形只占画布的 ~71%，
                // 给 24dp 的话实际只有 ~17dp，和旁边那颗 24dp 的二维码图标一比就显小。
                Image(
                    painter = appMarkPainter(),
                    contentDescription = null,
                    // 矢量本身就是白色填充，这里显式 tint 一遍是为了**不跟主题走**：
                    // 万一以后换素材带了颜色，这颗图标也仍然保持白字配白图标。
                    colorFilter = ColorFilter.tint(Color.White),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(30.dp),
                )
            },
        )
    }
}

/**
 * 一颗胶囊。
 *
 * ⚠️ 标题和描述都**不设 maxLines**：这两段的长度是固定的中文，
 * 而胶囊宽度被限制在半个屏幕以内，它们本来就一定会折行 ——
 * 设成 1 行再配省略号等于把"这个按钮是干什么的"直接砍掉。
 */
@Composable
private fun ScannerCapsule(
    title: String,
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.55f))
            // 一圈很淡的白边：相机画面偶尔正好是白的，没有边就只剩一团模糊的黑影
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.10f), shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 固定一个图标位：二维码 24dp、应用图标 30dp（字形只占画布七成），
        // 不占位的话两行文字的起点会差好几 dp
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}
