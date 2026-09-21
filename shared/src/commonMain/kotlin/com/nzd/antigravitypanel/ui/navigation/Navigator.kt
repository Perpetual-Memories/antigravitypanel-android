package com.nzd.antigravitypanel.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * 极简导航器：只持有返回栈，push / replace / pop / popUntil。
 *
 * 不需要 result 回传（本项目没有"选择后返回"的页面），保持最小实现。
 */
class Navigator(
    val backStack: MutableList<NavKey>,
) {
    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun current(): NavKey? = backStack.lastOrNull()

    fun backStackSize(): Int = backStack.size
}
