package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.nzd.antigravitypanel.shared.R

/**
 * 地图底图 / 掉落物品质框的 Android 实现。
 *
 * 走 `R.drawable` 的显式 when 而不是 `resources.getIdentifier(name, ...)`：
 * 名字拼错、文件漏打包这类问题在**编译期**就报出来，而不是等到某个地图翻到正面才发现
 * 少一张图。`getIdentifier` 还有反射开销，且在 R8 下容易和资源收缩打架。
 *
 * 素材在 `shared/src/androidMain/res/drawable-nodpi/`，命名 `map_<mapId>.webp`。
 * nodpi 是为了不让系统按屏幕密度再缩一遍 —— 统一由 Compose 的 ContentScale 缩放。
 */
@Composable
actual fun mapArtPainter(mapId: Int): Painter? {
    val id = when (mapId) {
        1000 -> R.drawable.map_1000
        1001 -> R.drawable.map_1001
        1002 -> R.drawable.map_1002
        12 -> R.drawable.map_12
        13 -> R.drawable.map_13
        14 -> R.drawable.map_14
        15 -> R.drawable.map_15
        16 -> R.drawable.map_16
        17 -> R.drawable.map_17
        18 -> R.drawable.map_18
        19 -> R.drawable.map_19
        21 -> R.drawable.map_21
        30 -> R.drawable.map_30
        112 -> R.drawable.map_112
        114 -> R.drawable.map_114
        115 -> R.drawable.map_115
        132 -> R.drawable.map_132
        135 -> R.drawable.map_135
        300 -> R.drawable.map_300
        304 -> R.drawable.map_304
        306 -> R.drawable.map_306
        308 -> R.drawable.map_308
        309 -> R.drawable.map_309
        310 -> R.drawable.map_310
        321 -> R.drawable.map_321
        322 -> R.drawable.map_322
        323 -> R.drawable.map_323
        324 -> R.drawable.map_324
        else -> return null
    }
    return painterResource(id = id)
}

@Composable
actual fun dropFramePainter(frame: DropFrame): Painter {
    val id = when (frame) {
        DropFrame.LEGENDARY -> R.drawable.drop_frame_legendary
        DropFrame.EPIC -> R.drawable.drop_frame_epic
        DropFrame.LOCK -> R.drawable.drop_lock
    }
    return painterResource(id = id)
}
