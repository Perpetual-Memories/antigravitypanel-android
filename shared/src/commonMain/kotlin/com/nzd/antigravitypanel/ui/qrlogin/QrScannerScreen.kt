package com.nzd.antigravitypanel.ui.qrlogin

import androidx.compose.runtime.Composable

/**
 * 全屏扫码页：相机预览 + 二维码识别 + 顶部返回/相册。
 *
 * 界面照 lsfTB 的 `QRCodeScanner`（黑底全屏、四角扫码框、底部一行提示），
 * 但**职责收窄到只干一件事：把二维码里的原文交回来**。
 * 它不认识 Cookie、不知道什么是登录——认领扫码内容是
 * [com.nzd.antigravitypanel.data.qrlogin.parseQrLoginPayload] 的事。
 * 这样 PC 端那边的协议以后改成别的（比如放个地址让手机去 POST 凭证），
 * 这个文件都不用动。
 *
 * actual 在 androidMain：相机预览要 CameraX，那是纯 android 的库，进不了 commonMain。
 *
 * @param onScanned 扫到内容。同一段内容只回调一次（回调后自动停扫，
 *   否则相机会对着同一个码一秒回调几十次）。
 * @param onDismiss 返回。用户按返回键 / 点左上角关闭都走这里。
 */
@Composable
expect fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
)
