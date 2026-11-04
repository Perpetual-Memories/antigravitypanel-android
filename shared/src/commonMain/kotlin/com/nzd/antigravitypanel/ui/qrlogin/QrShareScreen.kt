package com.nzd.antigravitypanel.ui.qrlogin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.credential.partitionLabelOf
import com.nzd.antigravitypanel.data.qrlogin.encodeQrSharePayload
import com.nzd.antigravitypanel.util.currentEpochSeconds
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 二维码那块白底的边长。屏幕宽 360dp 时两侧还能留出边距。 */
private val QR_CARD_SIZE = 248.dp

/**
 * 「反重力登录」：出示二维码，让另一台设备上的反重力数据面板直接登录。
 *
 * 内容和 PC 端「系统设置 → 查看二维码」是同一套（[encodeQrSharePayload] 逐字照抄），
 * 所以这张码给 PC 端、或者给另一台装了 Android 版的设备扫，都能直接用。
 *
 * 单独做成一屏而不是弹窗：二维码要够大才好扫，弹窗在窄屏上会把码压到 200dp 以下；
 * 而且这一屏是"给别人看屏幕"的场景，不该有半透明遮罩透出后面的相机画面。
 *
 * @param credential 当前账号。null = 还没登录，这时不画码（画个空码只会让人以为坏了）。
 */
@Composable
fun QrShareScreen(
    credential: MiniProgramCredential?,
    onBack: () -> Unit,
) {
    // 时间戳只在进这一屏时取一次：每次重组都取的话，二维码内容会跟着变，
    // 相机那边正对着扫就会一直"重新识别"。
    val payload = remember(credential) {
        credential?.let { encodeQrSharePayload(it, currentEpochSeconds()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        QrShareTopBar(onBack = onBack)

        if (payload == null) {
            EmptyCredentialHint(modifier = Modifier.weight(1f))
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 二维码永远是**白底黑码**，不跟主题走：深色模式下把码反过来（黑底白码）
            // 有一半扫码器认不出来，系统相机的识别率也明显更差。
            Box(
                modifier = Modifier
                    .size(QR_CARD_SIZE)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                QrCodeImage(
                    content = payload,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "用另一台设备上的《反重力数据面板》扫描",
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "当前账号：${partitionLabelOf(credential)}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "二维码里是当前账号的登录凭证，等同于密码，请勿截图或转发给他人。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 顶栏。照二级页的样子做：左边返回、标题居中偏左。
 *
 * 不用 `SmallTopAppBar`：那是给 Scaffold 里可滚动的页面用的，这一屏不滚，
 * 套一层 Scaffold 只会多出一堆 padding 计算。
 */
@Composable
private fun QrShareTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = "返回",
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "反重力登录",
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 没登录时的空态。二维码是"把凭证给别人"，没有凭证就无从出示。 */
@Composable
private fun EmptyCredentialHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "还没有可用的登录凭证",
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "先在账号详情里扫码或粘贴 Cookie，再回来出示二维码。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
