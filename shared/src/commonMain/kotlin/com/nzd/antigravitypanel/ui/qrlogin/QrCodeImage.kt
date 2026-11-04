package com.nzd.antigravitypanel.ui.qrlogin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 把一段文本画成二维码。
 *
 * 只负责**画**：内容怎么拼是 `data.qrlogin.encodeQrSharePayload` 的事，
 * 白底 / 卡片 / 圆角这些由调用方给（见 [QrShareScreen]）——二维码本身必须是
 * 纯黑白、无内边距的方块，外面套什么都行，但套图里再加装饰就会扫不出来。
 *
 * actual 在 androidMain：编码用 ZXing（`com.google.zxing:core` 本来就是扫码那套的依赖，
 * 不用为一个生成器再引库）。
 */
@Composable
expect fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
)

/** 直接显示微信服务端生成的二维码图片，不解码成文本后重新生成。 */
@Composable
expect fun YybQrImage(
    imageBytes: ByteArray,
    modifier: Modifier = Modifier,
)
