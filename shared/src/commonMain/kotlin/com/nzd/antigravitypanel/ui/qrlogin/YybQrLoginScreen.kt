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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.yyb.YybLoginStage
import com.nzd.antigravitypanel.data.yyb.YybLoginUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val YYB_QR_CARD_SIZE = 248.dp

/** 应用宝电脑版渠道：本机出示微信授权二维码，不使用本机相机。 */
@Composable
fun YybQrLoginScreen(
    state: YybLoginUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
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
                text = "应用宝渠道扫码登录",
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val qr = state.qr
            if (qr != null && state.stage in setOf(
                    YybLoginStage.WAITING_SCAN,
                    YybLoginStage.SCANNED,
                    YybLoginStage.AUTHORIZING,
                    YybLoginStage.EXCHANGING,
                    YybLoginStage.SUCCESS,
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(YYB_QR_CARD_SIZE)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    YybQrImage(
                        imageBytes = state.qr.imageBytes,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            Text(
                text = when (state.stage) {
                    YybLoginStage.LOADING_QR -> "正在获取微信二维码…"
                    YybLoginStage.WAITING_SCAN -> "使用另一台设备的微信扫描二维码"
                    YybLoginStage.SCANNED -> "已扫描，请在微信中确认授权"
                    YybLoginStage.AUTHORIZING -> "正在建立应用宝授权会话…"
                    YybLoginStage.EXCHANGING -> "正在获取登录凭证…"
                    YybLoginStage.SUCCESS -> "登录成功"
                    YybLoginStage.EXPIRED -> "二维码已过期，请重新获取"
                    YybLoginStage.CANCELLED -> "已取消扫码登录"
                    YybLoginStage.FAILED -> state.error ?: "登录失败，请重试"
                    YybLoginStage.IDLE -> "正在准备…"
                },
                color = if (state.stage == YybLoginStage.FAILED) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onBackground
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "授权仅用于本次登录，请勿把二维码截图或转发给他人。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            if (state.stage in setOf(
                    YybLoginStage.EXPIRED,
                    YybLoginStage.CANCELLED,
                    YybLoginStage.FAILED,
                )
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    onClick = onRetry,
                ) {
                    Text("重新获取二维码")
                }
            }
        }
    }
}
