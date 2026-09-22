package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.update.isNewerVersion
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 版本高低的判定。
 *
 * 这些用例守的是「别拿字符串去比版本号」—— 字符串比较会得出 `0.10.0 < 0.9.0`
 * 这种结论，而发版到两位数小版本那天才会发现，那时候已经晚了。
 */
class UpdateVersionTest {

    @Test
    fun 小版本更新算新版() {
        assertTrue(isNewerVersion("0.3.0", "0.2.0"))
        assertTrue(isNewerVersion("1.0.0", "0.9.9"))
    }

    @Test
    fun 同版本和旧版本都不算() {
        assertFalse(isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(isNewerVersion("0.1.9", "0.2.0"))
        assertFalse(isNewerVersion("0.2.0", "0.3.0"))
    }

    @Test
    fun 按数字比而不是按字符串() {
        // 字符串比较在这里会判成 0.10.0 < 0.9.0
        assertTrue(isNewerVersion("0.10.0", "0.9.0"))
    }

    @Test
    fun v前缀不影响判定() {
        // 两边都带、单边带、都不带，结论要一致
        assertTrue(isNewerVersion("v0.3.0", "v0.2.0"))
        assertTrue(isNewerVersion("v0.3.0", "0.2.0"))
        assertTrue(isNewerVersion("0.3.0", "v0.2.0"))
    }

    @Test
    fun 段数不齐时缺的算0() {
        assertTrue(isNewerVersion("0.2.1", "0.2"))
        assertFalse(isNewerVersion("0.2", "0.2.0"))
        assertTrue(isNewerVersion("1", "0.9.9"))
    }

    @Test
    fun 预发布后缀不参与比较() {
        // 只认数字前缀：0.2.0-beta 会被当成 0.2.0，
        // 否则装了 0.2.0 的人会被提醒"有新版 0.2.0-beta"，而它其实更早
        assertFalse(isNewerVersion("0.2.0-beta", "0.2.0"))
        assertTrue(isNewerVersion("0.2.1-beta", "0.2.0"))
    }

    @Test
    fun 空的和乱写的都不算新版() {
        assertFalse(isNewerVersion("", "0.2.0"))
        assertFalse(isNewerVersion("abc", "0.2.0"))
        // 当前版本取不到时不提示更新：空串会被解析成 0.0.0，
        // 于是任何版本都"比它新"，用户每次打开都会被弹一次
        assertFalse(isNewerVersion("0.2.0", ""))
        assertFalse(isNewerVersion("0.2.0", "unknown"))
    }
}
