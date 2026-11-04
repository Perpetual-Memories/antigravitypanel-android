package com.nzd.antigravitypanel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabSelectionTest {
    @Test
    fun `跨页动画中点回上一稳定页不能丢点击`() {
        val targetIndex = 0
        val settledPage = 0
        val currentPage = 1

        // 这是旧判断 index != settledPage 会错误过滤点击的状态。
        assertTrue(targetIndex == settledPage)
        assertTrue(
            shouldRequestTabScroll(
                detailVisible = false,
                targetIndex = targetIndex,
                currentPage = currentPage,
                currentPageOffsetFraction = 0.2f,
            ),
        )
    }

    @Test
    fun `动画中点击当前页也要取消原目标`() {
        assertTrue(
            shouldRequestTabScroll(
                detailVisible = false,
                targetIndex = 1,
                currentPage = 1,
                currentPageOffsetFraction = -0.2f,
            ),
        )
    }

    @Test
    fun `已经停在目标页时不重复启动动画`() {
        assertFalse(
            shouldRequestTabScroll(
                detailVisible = false,
                targetIndex = 2,
                currentPage = 2,
                currentPageOffsetFraction = 0f,
            ),
        )
    }

    @Test
    fun `二级页盖住底栏时不响应切换`() {
        assertFalse(
            shouldRequestTabScroll(
                detailVisible = true,
                targetIndex = 3,
                currentPage = 0,
                currentPageOffsetFraction = 0f,
            ),
        )
    }
}
