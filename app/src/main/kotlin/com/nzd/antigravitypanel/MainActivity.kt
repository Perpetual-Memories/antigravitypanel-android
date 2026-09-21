package com.nzd.antigravitypanel

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nzd.antigravitypanel.data.store.primeKeyValueStore
import com.nzd.antigravitypanel.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 数据库路径和加密存储都要 Context，任何界面出现之前就得备好
        initPlatform(applicationContext)
        // 加密存储预热处理：概览的状态卡要在**第一帧**就显示上次缓存的凭证状态，
        // 等不了 suspend 读取。这一下要读磁盘，趁启动画面还盖着的时候付掉。
        primeKeyValueStore()

        setContent {
            var colorMode by remember { mutableIntStateOf(com.nzd.antigravitypanel.ui.theme.ColorMode.SYSTEM) }
            val darkMode = resolveDarkTheme(colorMode, isSystemInDarkTheme())

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 小米机型上不关掉会导致导航栏出现强制对比度底色
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }

            App(onColorModeChange = { colorMode = it })
        }
    }
}
