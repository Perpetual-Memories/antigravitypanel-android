package com.nzd.antigravitypanel.ui.wiki

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.nzd.antigravitypanel.ui.component.openUrl
import kotlin.math.roundToInt

/**
 * WebView 版 wiki 浏览器。
 *
 * 几个坑值得写下来：
 *
 * - **JS 必须开**：站点是 Next.js 静态导出，路由跳转、搜索面板、命令面板全跑在
 *   客户端；关掉 JS 就只能看到首屏一张死图，返回/刷新按钮也全废。
 * - **`domStorageEnabled` 也要开**：侧边栏宽度之类的偏好存在 localStorage。
 * - **销毁必须自己来**，而且**只能在 `onRelease` 里**：`AndroidView` 的 `factory`
 *   不会因为页面退出就释放 WebView，不主动 `destroy()` 的话 Chromium 内核那几十 MB
 *   native 内存会一直挂在进程上 —— 而这个 app 的签到恰恰依赖冷启动能正常跑。
 *   时机见 [AndroidWikiBrowser.release]。
 */
private class AndroidWikiBrowser : WikiBrowser {
    private var web: WebView? = null

    override var currentUrl: String by mutableStateOf(WIKI_HOME_URL)
        private set

    override var currentTitle: String by mutableStateOf("")
        private set

    override var progress: Float by mutableFloatStateOf(0f)
        private set

    override var canGoBack: Boolean by mutableStateOf(false)
        private set

    /**
     * 换一颗新 WebView 的代号，一变 [WikiWebView] 就重建。
     *
     * 只有「渲染进程崩了」这一种情况会 ++：崩掉那颗 WebView 官方明确规定
     * "no other methods may be called on it"，只能整颗换掉。
     */
    var generation by mutableIntStateOf(0)
        private set

    fun bind(webView: WebView) {
        web = webView
    }

    /**
     * 回收一颗 WebView。
     *
     * ⚠️⚠️ 只能在**已经从视图树上摘下来之后**调 —— 也就是只能从 [WikiWebView]
     * 的 `onRelease` 里调，那里 Compose 已经先跑过 `AndroidViewHolder.onDeactivate()`
     * 的 `removeAllViewsInLayout()`。
     *
     * 还挂在树上就 `destroy()`，内核会硬杀 renderer 并打一条
     * `aw_browser_terminator.cc: Renderer process (N) crash detected (code -1)`。
     * 日志里那三条「崩溃」全是这么来的 —— 网页根本没崩，是我们自己拆早了。
     * （别再用 `DisposableEffect` 收尾：它记得比 `AndroidView` 早，会先跑。）
     */
    fun release(view: WebView) {
        if (web === view) web = null
        view.stopLoading()
        view.destroy()
    }

    /**
     * 渲染进程没了。返回 true = 宿主自己处理（不这么做内核会把整个 App 进程干掉）。
     */
    fun onRenderProcessGone(): Boolean {
        web = null
        canGoBack = false
        progress = 0f
        generation++
        return true
    }

    /**
     * 记下当前地址。
     *
     * ⚠️ 只认 http(s)：客户端路由中途会趟过 `about:blank` 之类的东西，
     * 跟着它走会让「复制链接」复制到一句废话。
     */
    fun onUrlChanged(url: String) {
        if (!url.startsWith("http")) return
        currentUrl = url
        // 顺手刷一次：客户端路由下这也是 canGoBack 唯一会变的时机
        canGoBack = web?.canGoBack() ?: false
    }

    fun onTitleChanged(title: String) {
        currentTitle = title
    }

    fun onProgressChanged(value: Int) {
        progress = value / 100f
        // 客户端路由（Next.js Link）不会重新走 onPageFinished，
        // 所以顺手在这儿刷一次 —— 进度回调是唯一稳定的"页面动过"信号。
        canGoBack = web?.canGoBack() ?: false
    }

    override fun refresh() {
        web?.reload()
    }

    override fun goBack() {
        web?.goBack()
    }

    /**
     * 本地测试加载过的源（`scheme://host[:port]`），没有就是 null。
     *
     * ⚠️ 站内判定要把它算进去：本地测试打开 `http://192.168.1.5:3000` 之后，
     * 页面里点任何同源链接都该留在 WebView 里，而不是像外链一样甩给系统浏览器。
     */
    var localOrigin: String? = null
        private set

    override fun loadUrl(url: String) {
        localOrigin = originOf(url)
        // 页面还没建好（理论上这一页一定建好了）：记下地址即可，
        // [WikiWebView] 的 factory 会用 currentUrl 加载。
        val view = web
        if (view == null) {
            if (url.startsWith("http")) currentUrl = url
            return
        }
        // ⚠️ 本地测试必须走 LOAD_DEFAULT。默认的 LOAD_CACHE_ELSE_NETWORK 会**无视
        // Cache-Control** 直接拿缓存：dev server 换过代码之后，旧 HTML 配不上新 chunk，
        // 页面照样画得出来，但 JS 加载失败 → 不水合 → 侧边栏 / 搜索 / 计算器全哑，
        // 随手点个链接整页刷新一次又好了（用户报的"首次进入点不动"就是这个）。
        view.settings.cacheMode = if (url.startsWith("http://")) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
        // 本地测试换源时，给新源也挂上客户端标记（document-start 是按源生效的）
        attachClientMarker(view, localOrigin)
        view.loadUrl(url)
    }

    /** 同源判定：官方站 + 本次本地测试加载的那个源。 */
    fun isSameOrigin(url: String): Boolean {
        if (isInternal(url)) return true
        val origin = localOrigin ?: return false
        return url == origin || url.startsWith("$origin/")
    }

    override fun toggleSidebar() {
        web?.evaluateJavascript(CLIENT_TOGGLE_SIDEBAR_JS, null)
    }

    override fun toggleSearch() {
        web?.evaluateJavascript(CLIENT_OPEN_SEARCH_JS, null)
    }

    override fun toggleCommand() {
        web?.evaluateJavascript(CLIENT_OPEN_COMMAND_JS, null)
    }

    /**
     * 宿主顶栏高度（**CSS px == dp**），写进站点的 `--app-chrome-top`。
     *
     * 由 [WikiWebView] 从 `Scaffold` 的顶栏高度灌进来。默认 0 是安全值：
     * 还没量出来时最多是"没让开"，不会像上错单位那样把整页推下去一大截。
     */
    var contentInsetDp: Float = 0f

    /** 顶栏高度变了（转屏、分屏）再压一遍；身份标记在文档创建时就打过了。 */
    fun applyClientInset(view: WebView) {
        view.evaluateJavascript(clientInsetJs(contentInsetDp.roundToInt()), null)
    }

    /**
     * 打「客户端」标记。
     *
     * 优先用 `addDocumentStartJavaScript`（等价于桌面端的 `initialization_script`，
     * 在页面任何脚本之前执行，所以不会先按浏览器渲染再跳一下）；
     * 不支持这个特性的 WebView 退回到 [onPageStarted] 里注入 —— 时机稍晚，但还能用。
     */
    fun attachClientMarker(view: WebView, origin: String?) {
        val script = clientMarkerJs(contentInsetDp.roundToInt(), appVersion(view))
        if (origin == null) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(view, script, setOf(origin))
        }
    }

    /** 没有 document-start 能力时的兜底时机。重复执行是幂等的。 */
    fun injectClientMarker(view: WebView) {
        view.evaluateJavascript(
            clientMarkerJs(contentInsetDp.roundToInt(), appVersion(view)),
            null,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun rememberWikiBrowser(): WikiBrowser = remember { AndroidWikiBrowser() }

/**
 * 网页本体。
 *
 * ⚠️ 内容自己管滚动，外面不要套 `nestedScroll` —— 网页里的横向滑动、
 * 下拉刷新会和顶栏的折叠手势抢事件。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WikiWebView(
    browser: WikiBrowser,
    contentInsetDp: Float,
    modifier: Modifier,
) {
    val owner = browser as AndroidWikiBrowser
    // ⚠️ key(generation)：渲染进程崩掉那颗 WebView 已经废了，只能整颗换。
    // key 一变 AndroidView 就重建，旧的那颗走 onRelease 被 destroy。
    key(owner.generation) {
        // 上一次压进去的让位值。用数组而不是 `mutableStateOf`：这只是个
        // "做过没有"的记号，写成 state 会让每次压完都多触发一轮重组。
        val appliedInset = remember { floatArrayOf(contentInsetDp) }
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        // 代理环境下带宽更金贵：有缓存就用缓存，没有再去网络
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }
                    // 让位值由 Scaffold 的顶栏高度决定（commonMain 那边量好了传进来），
                    // 这里只管收下 —— onPageFinished 每次都会用它重压一遍。
                    owner.contentInsetDp = contentInsetDp
                    // 官方站常驻一份 document-start 标记；本地测试的源在 loadUrl 里补
                    owner.attachClientMarker(this, WIKI_ORIGIN)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            owner.onUrlChanged(url)
                            // 兜底：不支持 document-start 的 WebView 在这里补打标记。
                            // 正常情况下标记早已由 initialization script 打上，重复执行无害。
                            owner.injectClientMarker(view)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            owner.onUrlChanged(url)
                            // 顶栏高度可能变过（转屏/分屏），再压一次宿主顶栏高度
                            owner.applyClientInset(view)
                        }

                        /**
                         * ⚠️⚠️ 客户端路由（Next.js Link 底下的 `history.pushState`）
                         * **既不走 onPageStarted 也不走 onPageFinished**，但**会走这里**
                         * —— 这是「当前地址」唯一能跟上站内跳转的信号。
                         *
                         * 少了这一条，「复制链接 / 在浏览器中打开 / 分享链接」三件事
                         * 永远只能拿到首页地址，翻十页也还是 `https://nzm-wiki.com/`。
                         */
                        override fun doUpdateVisitedHistory(
                            view: WebView,
                            url: String,
                            isReload: Boolean,
                        ) {
                            if (isReload) return
                            owner.onUrlChanged(url)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val target = request.url?.toString().orEmpty()
                            // 站内链接（含本地测试加载的源）留在 WebView 里翻，
                            // 站外交给系统浏览器
                            if (owner.isSameOrigin(target)) return false
                            openUrl(target)
                            return true
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail,
                        ): Boolean = owner.onRenderProcessGone()
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            owner.onProgressChanged(newProgress)
                        }

                        override fun onReceivedTitle(view: WebView, title: String?) {
                            owner.onTitleChanged(title.orEmpty())
                        }
                    }
                    owner.bind(this)
                    // 重建（渲染进程崩过）时接着上一次的地址加载，别又跳回首页
                    loadUrl(owner.currentUrl)
                }
            },
            update = { view ->
                // 顶栏高度理论上这一页不会变；真变了（转屏、分屏）就重压一遍，
                // 否则网页会一直停在旧的内缩上。
                if (appliedInset[0] != contentInsetDp) {
                    appliedInset[0] = contentInsetDp
                    owner.contentInsetDp = contentInsetDp
                    owner.applyClientInset(view)
                }
            },
            onRelease = { view ->
                view.webViewClient = WebViewClient()
                view.webChromeClient = WebChromeClient()
                // ⚠️ 只有这里能 destroy：走到这一步 Compose 已经把 view 从树上摘掉了
                // （AndroidViewHolder.onDeactivate 里 removeAllViewsInLayout），
                // 早一步就会在内核里记一条假的 "Renderer process crash detected"。
                owner.release(view)
            },
    )
    }
}

/**
 * 判定"是不是站内链接"用的源。
 *
 * ⚠️ 只比对 scheme + host，**不带路径**：站点偶尔会把外链做成 302，
 * 带路径比对会在那种情况下把外链误判成站内。
 *
 * ⚠️⚠️ 但也**不能**只写 `startsWith(WIKI_ORIGIN)` —— 那样
 * `https://nzm-wiki.com.evil.com` 也会被认成站内，于是它既躲过了系统浏览器，
 * 又白拿了下面那两条注入脚本（那些脚本是照着本站 DOM 写的）。
 */
private const val WIKI_ORIGIN = "https://nzm-wiki.com"

private fun isInternal(url: String): Boolean =
    url == WIKI_ORIGIN || url.startsWith("$WIKI_ORIGIN/")

/** `https://a.b:8080/x?y` → `https://a.b:8080`；不是 http(s) 或没有主机时返回 null。 */
private fun originOf(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = url.substring(0, schemeEnd)
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
        return null
    }
    val authority = url.substring(schemeEnd + 3)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    if (authority.isEmpty()) return null
    return "$scheme://$authority"
}

/** 报给站点的版本号（`data-client-version`）；取不到就报 0。 */
private fun appVersion(view: WebView): String = runCatching {
    view.context.packageManager.getPackageInfo(view.context.packageName, 0).versionName
}.getOrNull() ?: "0"
