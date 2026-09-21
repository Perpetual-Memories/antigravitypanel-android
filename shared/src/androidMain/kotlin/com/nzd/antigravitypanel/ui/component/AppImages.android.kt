package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.nzd.antigravitypanel.shared.R

@Composable
actual fun appIconPainter(): Painter = painterResource(id = R.drawable.ic_app)

@Composable
actual fun appMarkPainter(): Painter = painterResource(id = R.drawable.ic_app_mark)

@Composable
actual fun developerAvatarPainter(): Painter = painterResource(id = R.drawable.dev_avatar)

@Composable
actual fun statusGlyphPainter(glyph: StatusGlyph): Painter = painterResource(
    id = when (glyph) {
        StatusGlyph.CHECK -> R.drawable.ic_status_check
        StatusGlyph.ALERT -> R.drawable.ic_status_alert
    },
)
