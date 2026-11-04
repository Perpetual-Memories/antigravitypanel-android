package com.nzd.antigravitypanel.ui.qrlogin

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 生成尺寸（像素）。屏幕上是缩放显示的，给足一点余量，缩小时边缘更干净。 */
private const val QR_BITMAP_SIZE = 640

/**
 * [QrCodeImage] 的 Android 实现：ZXing `QRCodeWriter` → BitMatrix → Bitmap。
 *
 * 两个参数是**照着 PC 端抄的**，不是随手选的：
 * - 纠错等级 `M`（PC 端 `DO(json, "M")`）。等级越高码越密，屏幕对着扫时反而更难对焦；
 * - `MARGIN = 0`：留白由外层卡片给（白底 padding），这里再加一圈会让码变小。
 *
 * `FilterQuality.None` 是必须的：二维码放大时做双线性插值会把黑白边界糊成灰，
 * 有些扫码器会因此判不出模块边界。宁可看到锯齿，也不要糊。
 */
@Composable
actual fun QrCodeImage(
    content: String,
    modifier: Modifier,
) {
    val bitmap = remember(content) {
        runCatching { encodeQr(content).asImageBitmap() }.getOrNull()
    }
    if (bitmap == null) return

    Image(
        bitmap = bitmap,
        contentDescription = "登录二维码",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

private fun encodeQr(content: String): Bitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        QR_BITMAP_SIZE,
        QR_BITMAP_SIZE,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            // cookie 全是 ASCII，但写死 UTF-8 更稳：以后要是塞了中文昵称也不会变乱码
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 0,
        ),
    )
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            pixels[row + x] = if (matrix.get(x, y)) BLACK else WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

@Composable
actual fun YybQrImage(
    imageBytes: ByteArray,
    modifier: Modifier,
) {
    val bitmap = remember(imageBytes) {
        runCatching {
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                .asImageBitmap()
        }.getOrNull()
    }
    if (bitmap == null) return
    Image(
        bitmap = bitmap,
        contentDescription = "应用宝微信登录二维码",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
