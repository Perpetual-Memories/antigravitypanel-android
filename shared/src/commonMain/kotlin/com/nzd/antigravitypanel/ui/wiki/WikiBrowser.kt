package com.nzd.antigravitypanel.ui.wiki

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * wiki 首页。
 *
 * ⚠️ 主站部署在**腾讯云 EdgeOne**，按来源 IP 地域放行：大陆多数地区正常，
 * 少部分地区/运营商调度不到节点 → 打不开或很慢。所以才需要「代理模式」那个开关
 * （见 `data/settings/UserSettings.proxyMode`）—— 用户挂代理时，app 自己的
 * 官方请求必须能主动掐断。
 */
const val WIKI_HOME_URL = "https://nzm-wiki.com/"

/** wiki 仓库地址，「更多 → 访问 GitHub」用。 */
const val WIKI_REPO_URL = "https://github.com/lostlightll/nzm-wiki"

/**
 * 客户端身份脚本（宿主 ⇄ 站点的「客户端契约」）。
 *
 * 站点的 `data-client` 分支会：把 `--site-header-height` 归零、不渲染站内顶栏、
 * 按 `--app-chrome-top` 给内容让位 —— 也就是说**让位和藏顶栏这些事搬回站点自己做了**，
 * 这边只负责声明"我是谁"，不再往页面里塞 `<style>`、也不再按 aria-label 去改 DOM。
 *
 * ⚠️ 必须在**文档创建时**执行（`WebViewCompat.addDocumentStartJavaScript`），
 * 否则属性晚到，页面会先按"浏览器"渲染一遍再跳一下。
 *
 * ⚠️ 单位：`INSET` 是 **dp**，和网页的 CSS px 1:1（站点 viewport 是 width=device-width）。
 * 传设备像素会放大 density 倍。
 */
fun clientMarkerJs(insetDp: Int, version: String): String = """
(function () {
  var INSET = $insetDp;
  var VERSION = '$version';
  function mark() {
    var root = document.documentElement;
    if (!root) return false;
    root.setAttribute('data-client', 'antigravity');
    root.setAttribute('data-client-platform', 'android');
    root.setAttribute('data-client-version', VERSION);
    root.style.setProperty('--app-chrome-top', INSET + 'px');
    var client = window.__NZM_CLIENT__ || (window.__NZM_CLIENT__ = {});
    client.name = 'antigravity';
    client.platform = 'android';
    client.version = VERSION;
    client.protocol = 1;
    return true;
  }
  if (!mark()) {
    // 文档还没建好：等 <html> 出现再打标记
    var observer = new MutationObserver(function () {
      if (mark()) observer.disconnect();
    });
    observer.observe(document, { childList: true });
  }
})();
"""

/**
 * 只改宿主顶栏高度（转屏/分屏时重压一次），身份标记在文档创建时已经打过了。
 */
fun clientInsetJs(insetDp: Int): String = """
(function () {
  try {
    document.documentElement.style.setProperty('--app-chrome-top', '$insetDp' + 'px');
  } catch (e) {}
})();
"""

/**
 * 三个动作：**优先走站点暴露的 `__NZM_CLIENT__.actions`**，没有才回落到老办法
 * （点 aria-label 按钮 / 合成快捷键）—— 老站点、老版本过渡期都还能用。
 */
val CLIENT_TOGGLE_SIDEBAR_JS: String = """
(function () {
  var client = window.__NZM_CLIENT__;
  if (client && client.actions && typeof client.actions.toggleSidebar === 'function') {
    client.actions.toggleSidebar();
    return true;
  }
  var toggle = document.querySelector("button[aria-label='打开导航']");
  if (!toggle) { return false; }
  if (toggle.getAttribute('aria-expanded') === 'true') {
    var close = document.querySelector("button[aria-label='关闭导航']");
    if (close) { close.click(); return true; }
    return false;
  }
  toggle.click();
  return true;
})();
"""

val CLIENT_OPEN_SEARCH_JS: String = """
(function () {
  var client = window.__NZM_CLIENT__;
  if (client && client.actions && typeof client.actions.openSearch === 'function') {
    client.actions.openSearch();
    return true;
  }
  document.dispatchEvent(new KeyboardEvent('keydown', {
    key: 'p', ctrlKey: true, shiftKey: false, bubbles: true
  }));
  return false;
})();
"""

val CLIENT_OPEN_COMMAND_JS: String = """
(function () {
  var client = window.__NZM_CLIENT__;
  if (client && client.actions && typeof client.actions.openCommandPalette === 'function') {
    client.actions.openCommandPalette();
    return true;
  }
  document.dispatchEvent(new KeyboardEvent('keydown', {
    key: 'p', ctrlKey: true, shiftKey: true, bubbles: true
  }));
  return false;
})();
"""

/**
 * wiki 浏览器句柄。
 *
 * 只有「把网页画出来」这一件事需要平台实现（Android 是 WebView）；
 * 上面那排玻璃按钮、更多弹窗全在 commonMain 里。
 *
 * 之所以不写成 `expect class`：接口比 expect/actual 好测——
 * 单测里塞一个假实现就能跑 UI 逻辑，不用等平台那一侧编译。
 */
interface WikiBrowser {
    /** 当前地址，用于「复制链接 / 分享链接」。 */
    val currentUrl: String

    /** 当前网页标题。空串表示还没拿到。 */
    val currentTitle: String

    /** 加载进度 0..1。 */
    val progress: Float

    /** 网页内部还能不能后退。为 false 时后退按钮置灰。 */
    val canGoBack: Boolean

    fun refresh()

    fun goBack()

    /**
     * 导航到一个指定地址（「更多 → 本地测试」用）。
     *
     * 传进来的地址必须已经规范化（见 [normalizeLocalTestUrl]），这里不再补 scheme。
     * 加载之后**当前会话的源**会跟着变 —— 站内判定、外链跳转都按新源算，
     * 否则本地测试页里点任意链接都会被当成外链甩给系统浏览器。
     */
    fun loadUrl(url: String)

    /** 开/关网页自带的侧边栏。已经开着就收起。 */
    fun toggleSidebar()

    /** 开/关网页自带的搜索面板。 */
    fun toggleSearch()

    /** 开/关网页自带的命令面板。 */
    fun toggleCommand()
}

/**
 * 建一个跟当前页面生命周期绑定的浏览器实例。
 *
 * ⚠️ 别跨页面复用：WebView 是重量级对象，内核那几十 MB native 内存不跟着
 * 组合销毁走，留在内存里比新建一次贵得多（见 androidMain 的 dispose）。
 */
@Composable
expect fun rememberWikiBrowser(): WikiBrowser

/**
 * 把「网页后退」这件事递给宿主的一座桥。
 *
 * 为什么要它：宿主（`AppContent`）接的是**系统返回键 / 返回手势**，而浏览器实例长在
 * [WikiScreen] 里 —— 宿主拿不到，也没法把它抬上来（抬上去就等于跨开关页复用一颗
 * WebView，见 [rememberWikiBrowser]）。于是让那一页把两样东西挂上来：
 * 网页还能不能退、以及让它退一页的那个动作。
 *
 * ⚠️ [goBack] 是**普通 var 不是 State**：它每次组合都是新的 lambda 实例，
 * 存进 State 会让宿主白白重组一轮；宿主只在真的按了返回时才调它，不需要观察。
 * [canGoBack] 才需要是 State —— 宿主要按它决定这次返回是"退一页"还是"退出"。
 */
class WikiBackBridge {
    /** 网页里还有没有历史。 */
    var canGoBack: Boolean by mutableStateOf(false)
        private set

    /** 让网页退一页。没挂上时（这一页不在场）是空操作。 */
    var goBack: () -> Unit = {}
        private set

    internal fun onCanGoBackChange(value: Boolean) {
        canGoBack = value
    }

    internal fun bindGoBack(action: () -> Unit) {
        goBack = action
    }

    internal fun unbindGoBack() {
        goBack = {}
        canGoBack = false
    }
}

/**
 * 渲染网页本体。commonMain 里只当它是一块"会自己滚动的内容"。
 *
 * @param contentInsetDp 宿主顶栏高度 —— 状态栏 + 按钮条，直接取自 `Scaffold` 给的
 *   `padding.calculateTopPadding()`。平台侧把它写成网页的 `--app-chrome-top`，
 *   **让位由站点自己做**（`data-client` 分支），这里不再往页面里塞 `<style>`。
 *   ⚠️ 单位是 **dp**：站点 `width=device-width`，1 CSS px == 1 dp。
 *   传设备像素会正好放大 density 倍。
 */
@Composable
expect fun WikiWebView(
    browser: WikiBrowser,
    contentInsetDp: Float,
    modifier: Modifier = Modifier,
)
