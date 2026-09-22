package com.nzd.antigravitypanel.data.qrlogin

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * 相机预览**别给全分辨率**：分析帧不做缩放也不做降采样的话，1920×1080 的 YUV 每帧要搬
 * 两百多万字节再旋转一遍，中低端机上单线程分析器直接被吃满，预览跟着卡。
 * 扫的是屏幕上的二维码，720p 完全够认。
 */
val QR_ANALYSIS_SIZE: Size = Size(1280, 720)

/**
 * 用 ZXing 解二维码。
 *
 * 为什么不是 ML Kit：ML Kit 的 barcode 模块会把 GMS 的一串传递依赖拖进来
 * （国内没 GMS 的设备上还得赌它初始化成功），而这里只需要解一种、且是最规整的一种码。
 * ZXing 是纯 Java，几十 KB，离线可用。
 *
 * 只解 QR_CODE：[DecodeHintType.POSSIBLE_FORMATS] 收窄之后，一帧里能跳过的解码器分支更多。
 */
class QrDecoder {

    private val reader = MultiFormatReader()
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        // 稍微慢一点，换倾斜/低对比度下的识别率。屏幕上的码常常带摩尔纹，值得
        DecodeHintType.TRY_HARDER to true,
    )

    /**
     * 解一帧相机预览。
     *
     * @param proxy 必须在使用期间保持打开，解完由调用方 `close()`。
     */
    fun decode(proxy: ImageProxy): String? {
        if (proxy.format != ImageFormat.YUV_420_888) return null
        val y = extractYPlane(proxy) ?: return null
        val rotation = proxy.imageInfo.rotationDegrees
        val (data, width, height) = rotate(y, proxy.width, proxy.height, rotation)
        // 传整幅，不裁剪：扫码框只是给用户的视觉引导，实际识别范围取全画面更宽容，
        // 用户对不准框也能扫上
        return decode(
            PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        )
    }

    /** 解一张相册里的截图。PC 端的二维码截图传到手机上时走这条路。 */
    fun decode(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return decode(RGBLuminanceSource(width, height, pixels))
    }

    private fun decode(source: LuminanceSource): String? = runCatching {
        // reader 有内部状态（上一次的部分匹配结果），复用前必须 reset
        reader.reset()
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }.getOrNull()

    /**
     * 取 Y 平面。二维码只认明暗，UV（色度）用不上；
     * 顺带避开了 YUV→RGB 那道转换，这是整个解码里最省的一笔。
     *
     * `rowStride` 一般大于 width（有对齐padding），`pixelStride` 通常是 1；
     * 两个都不能假设，否则在部分设备上会读出斜着的画面。
     */
    private fun extractYPlane(proxy: ImageProxy): ByteArray? {
        val plane = proxy.planes.firstOrNull() ?: return null
        val width = proxy.width
        val height = proxy.height
        val out = ByteArray(width * height)
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (row in 0 until height) {
            val rowStart = row * rowStride
            if (pixelStride == 1) {
                val length = minOf(width, (buffer.limit() - rowStart).coerceAtLeast(0))
                if (length <= 0) break
                buffer.position(rowStart)
                buffer.get(out, row * width, length)
            } else {
                for (col in 0 until width) {
                    val index = rowStart + col * pixelStride
                    if (index >= buffer.limit()) break
                    out[row * width + col] = buffer.get(index)
                }
            }
        }
        return out
    }

    /**
     * 把图像转正。相机的 [ImageProxy] 尺寸是**传感器方向**的，
     * 要摆正得按 `rotationDegrees` 转一次，ZXing 自己不做这件事。
     */
    private fun rotate(
        src: ByteArray,
        width: Int,
        height: Int,
        degrees: Int,
    ): Triple<ByteArray, Int, Int> {
        val norm = ((degrees % 360) + 360) % 360
        val size = width * height
        return when (norm) {
            0 -> Triple(src, width, height)

            90 -> {
                val out = ByteArray(size)
                for (y in 0 until height) {
                    for (x in 0 until width) out[x * height + (height - 1 - y)] = src[y * width + x]
                }
                Triple(out, height, width)
            }

            180 -> {
                val out = ByteArray(size)
                for (y in 0 until height) {
                    for (x in 0 until width) out[(height - 1 - y) * width + (width - 1 - x)] =
                        src[y * width + x]
                }
                Triple(out, width, height)
            }

            270 -> {
                val out = ByteArray(size)
                for (y in 0 until height) {
                    for (x in 0 until width) out[(width - 1 - x) * height + y] = src[y * width + x]
                }
                Triple(out, height, width)
            }

            // 非 90 的整数倍不该出现（相机只给这几个值），原样丢过去，最差只是这一帧解不出
            else -> Triple(src, width, height)
        }
    }
}

/** 让调用方少写一个常量：分析用例的目标分辨率。 */
fun ImageAnalysis.Builder.setQrAnalysisSize(): ImageAnalysis.Builder =
    setTargetResolution(QR_ANALYSIS_SIZE)
