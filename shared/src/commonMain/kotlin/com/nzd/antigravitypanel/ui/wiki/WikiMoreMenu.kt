package com.nzd.antigravitypanel.ui.wiki

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.nzd.antigravitypanel.ui.component.copyToClipboard
import com.nzd.antigravitypanel.ui.component.openUrl
import com.nzd.antigravitypanel.ui.component.shareText
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup

/** 「更多」菜单里的五项，声明顺序即菜单里的顺序。 */
enum class WikiMoreAction(
    val label: String,
) {
    OPEN_IN_BROWSER("在浏览器中打开"),
    COPY_LINK("复制链接"),
    SHARE_LINK("分享链接"),
    OPEN_GITHUB("访问 GitHub"),
    LOCAL_TEST("本地测试"),
}

/**
 * 「更多」下拉菜单。
 *
 * ⚠️ 用**下拉**而不是弹窗：这些项都是从"当前这一页"派生的动作，
 * 贴着按钮弹出来才有一对一的对应关系；弹窗盖在屏幕中间，读起来像另一件事。
 * 唯一例外是最后那项「本地测试」—— 它要填地址，点完由调用方另弹一个输入框。
 *
 * ⚠️⚠️ `OverlayDropdownPopup` 锚的是**调用处的父布局**（javap：
 * `PopupPositionProvider.Align.End`，全文没有 `positionInRoot` / `onGloballyPositioned`），
 * 所以它必须和「更多」那颗按钮**待在同一个父布局里** —— 见 [WikiScreen] 里那个 `Box`。
 * 摆到外面（比如直接放 `WikiScreen` 顶层）它就会锚到整块玻璃胶囊或者整页上去。
 *
 * ⚠️ 动作在**点击时**才去读 `browser` 的当前地址/标题，所以这份 `entries`
 * 不随 URL 变化重建 —— 翻页不会把菜单里的回调换掉。
 */
@Composable
internal fun WikiMoreMenu(
    browser: WikiBrowser,
    show: Boolean,
    onDismiss: () -> Unit,
    onLocalTest: () -> Unit,
) {
    val entries = listOf(
        DropdownEntry(
            items = WikiMoreAction.entries.map { action ->
                DropdownItem(
                    text = action.label,
                    onClick = {
                        when (action) {
                            WikiMoreAction.OPEN_IN_BROWSER -> openUrl(browser.currentUrl)
                            WikiMoreAction.COPY_LINK ->
                                copyToClipboard(browser.currentUrl, "Wiki 链接")
                            WikiMoreAction.SHARE_LINK ->
                                shareText(browser.currentUrl, browser.currentTitle)
                            WikiMoreAction.OPEN_GITHUB -> openUrl(WIKI_REPO_URL)
                            // 菜单收起与输入框弹出同一帧发生：下拉没有遮罩，
                            // 两层同时在场也不会叠出一块变暗的背景
                            WikiMoreAction.LOCAL_TEST -> onLocalTest()
                        }
                        onDismiss()
                    },
                )
            },
        ),
    )
    OverlayDropdownPopup(
        entries = entries,
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        maxHeight = 320.dp,
        dropdownColors = DropdownDefaults.dropdownColors(),
        renderInRootScaffold = true,
    )
}
