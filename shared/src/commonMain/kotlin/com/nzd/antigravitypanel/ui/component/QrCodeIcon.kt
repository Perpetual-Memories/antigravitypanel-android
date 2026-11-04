package com.nzd.antigravitypanel.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material Icons 的 `qr_code`（filled，24dp 网格）。
 *
 * 扫码页「应用宝电脑版渠道扫码登录」那颗按钮用它：那个动作本质是"扫别人的码"，
 * 而 Miuix 那 156 个图标里没有任何二维码图形（只有 `Scan` 那个取景框，
 * 放在这里会被读成"打开相机"而不是"这是一个二维码入口"）。
 *
 * ⚠️ **为什么手抄**：为一个图标拖进整套 `material-icons-extended` 不划算
 * （同 `KeyboardCommandIcon` / `AutoStoriesIcon` 的理由），这里只抄 path。
 * 素材来自 Google Material Icons（Apache-2.0），版权归原作者。
 *
 * ⚠️ 三个大方块的"空心"靠**同一条 path 内部的非零环绕**：外圈顺时针、内圈逆时针，
 * 因此不能拆成多个 `.path {}`（拆了就是一坨实心黑方块）。
 */
val QrCodeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "qr_code",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(QR_CODE_PATH),
        // 具体颜色由 Icon 的 tint 覆盖，这里只需要一个"有值"的填充
        fill = SolidColor(Color.Black),
    ).build()
}

private const val QR_CODE_PATH =
    "M3 11h8V3H3v8zm2-6h4v4H5V5zM3 21h8v-8H3v8zm2-6h4v4H5v-4z" +
        "M13 3v8h8V3h-8zm6 6h-4V5h4v4z" +
        "M13 13h2v2h-2zM15 15h2v2h-2zM13 17h2v2h-2zM17 17h2v2h-2z" +
        "M19 15h2v2h-2zM17 13h2v2h-2zM19 19h2v2h-2z"
