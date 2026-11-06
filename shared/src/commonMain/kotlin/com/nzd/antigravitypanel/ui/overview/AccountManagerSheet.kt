package com.nzd.antigravitypanel.ui.overview

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.credential.SavedAccount
import com.nzd.antigravitypanel.data.credential.parseNzCookie
import com.nzd.antigravitypanel.data.credential.partitionLabelOf
import com.nzd.antigravitypanel.ui.component.MaterialGlyphs
import com.nzd.antigravitypanel.ui.component.rememberTextFilePicker
import com.nzd.antigravitypanel.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 本机账号列表与添加账号共用一张弹层。
 *
 * 列表和添加表单放在同一个带 [animateContentSize] 的容器里：切换内容时高度连续变化，
 * 而不是让 Miuix 的 wrapContentHeight 在新一帧突然跳到另一个尺寸。
 */
@Composable
fun AccountManagerSheet(
    show: Boolean,
    accounts: List<SavedAccount>,
    activeOpenId: String?,
    saving: Boolean = false,
    error: String? = null,
    onSwitch: (String) -> Unit,
    onSave: (String) -> Unit,
    onScanClick: () -> Unit,
    onImportHar: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    val cookieState = rememberTextFieldState()
    val pickHar = rememberTextFilePicker { text ->
        if (text != null) onImportHar(text)
    }

    LaunchedEffect(show) {
        if (show) adding = false
    }
    LaunchedEffect(show, adding) {
        if (show && adding && cookieState.text.isNotEmpty()) {
            cookieState.edit { replace(0, length, "") }
        }
    }

    OverlayBottomSheet(
        show = show,
        title = if (adding) "添加账号" else "多账号管理",
        allowDismiss = !saving,
        startAction = {
            if (adding) {
                IconButton(onClick = { pickHar() }, enabled = !saving) {
                    Icon(
                        imageVector = MiuixIcons.Import,
                        contentDescription = "从 HAR 文件导入 Cookie",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        endAction = {
            if (adding) {
                IconButton(onClick = onScanClick, enabled = !saving) {
                    Icon(
                        imageVector = MiuixIcons.Scan,
                        contentDescription = "扫码添加账号",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = FastOutSlowInEasing,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (adding) {
                AddAccountHint()
                TextField(
                    state = cookieState,
                    label = "Cookie",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                    trailingIcon = {
                        val input = cookieState.text.toString().trim()
                        IconButton(
                            onClick = { if (input.isNotBlank()) onSave(input) },
                            enabled = input.isNotBlank() && !saving,
                        ) {
                            if (saving) {
                                CircularProgressIndicator(progress = null, size = 20.dp, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = "保存并添加账号",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                )
                if (error != null) {
                    Text(text = error, color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
                }
            } else {
                if (accounts.isEmpty()) {
                    Text(
                        text = "还没有已保存的账号",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                    )
                } else {
                    accounts.forEach { account ->
                        SavedAccountRow(
                            account = account,
                            active = account.openid == activeOpenId,
                            enabled = !saving,
                            onClick = { onSwitch(account.openid) },
                        )
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    onClick = {
                        cookieState.edit { replace(0, length, "") }
                        adding = true
                    },
                ) {
                    Icon(
                        imageVector = MaterialGlyphs.PersonAddAlt1,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("添加账号")
                }
            }
        }
    }
}

@Composable
private fun SavedAccountRow(
    account: SavedAccount,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val credential = remember(account.cookieRaw) {
        runCatching { parseNzCookie(account.cookieRaw) }.getOrNull()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = if (active) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                MiuixTheme.colorScheme.surfaceContainer
            },
        ),
        showIndication = !active,
        onClick = if (active || !enabled) null else onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(url = account.avatarUrl, size = 46.dp)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = account.nickname.ifBlank { "未同步名称" },
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "LV.${account.seasonLevel ?: "—"} · " +
                        partitionLabelOf(credential).ifBlank { "未知分区" },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (active) {
                Text(
                    text = "当前",
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AddAccountHint() {
    val dark = isInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (dark) Color(0xFF3A2D12) else Color(0xFFFFF3D6),
        ),
    ) {
        Text(
            text = "添加账号会保存为新的本机账号，不会删除其他账号。" +
                "也可以从 HAR 导入或扫描 PC 端凭证二维码。",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            fontSize = 13.sp,
            color = if (dark) Color(0xFFFFD978) else Color(0xFF704D00),
        )
    }
}
