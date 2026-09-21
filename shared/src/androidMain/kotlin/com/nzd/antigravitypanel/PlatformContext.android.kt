package com.nzd.antigravitypanel

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

/**
 * 平台侧的 Context 入口。
 *
 * commonMain 拿不到 Context，但数据库路径、加密存储都要它，所以在 Application/Activity
 * 启动时存一份 applicationContext 供各 actual 取用。存 applicationContext 而不是
 * Activity，避免长生命周期对象持有界面引用。
 */
private var appContext: Context? = null

fun initPlatform(context: Context) {
    appContext = context.applicationContext
    SingletonImageLoader.setSafe(AppImageLoaderFactory)
}

internal fun requireContext(): Context =
    appContext ?: error("平台还没初始化：先调用 initPlatform(context)")

/**
 * Coil 的单例 ImageLoader。
 *
 * **Coil 3 默认不带网络取图器** —— 取图器被拆到了 `coil-network-*` 里，不显式加的话
 * 所有 `https://` 的图都会在 fetch 那一步静默失败：不崩、不报错，卡片就是一片空白。
 * 掉落物的图标全部来自官方 CDN 的绝对地址，所以这一步是必须的，不是优化项。
 */
private object AppImageLoaderFactory : SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            // 图标很小，淡入能让列表滚动时不那么"闪"
            .crossfade(true)
            .build()
}
