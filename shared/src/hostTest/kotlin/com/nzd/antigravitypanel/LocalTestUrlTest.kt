package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.ui.wiki.normalizeLocalTestUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 「本地测试」输入框的地址规范化。
 *
 * 这段决定两件事：一是本地 dev server 的 `http` 能不能直接用（不补 scheme 时
 * WebView 会把它当相对路径），二是别把 `javascript:` 之类的伪协议喂进 `loadUrl`。
 */
class LocalTestUrlTest {

    @Test
    fun privateHostsDefaultToHttp() {
        assertEquals("http://192.168.0.114:3000", normalizeLocalTestUrl("192.168.0.114:3000"))
        assertEquals("http://10.0.0.2", normalizeLocalTestUrl("10.0.0.2"))
        assertEquals("http://172.16.5.4:8080/wiki", normalizeLocalTestUrl("172.16.5.4:8080/wiki"))
        assertEquals("http://127.0.0.1:3000", normalizeLocalTestUrl("127.0.0.1:3000"))
        assertEquals("http://localhost:3000", normalizeLocalTestUrl("localhost:3000"))
        assertEquals("http://[::1]:3000", normalizeLocalTestUrl("[::1]:3000"))
        assertEquals("http://nzm.local", normalizeLocalTestUrl("nzm.local"))
    }

    @Test
    fun publicHostsDefaultToHttps() {
        assertEquals("https://nzm-wiki.com", normalizeLocalTestUrl("nzm-wiki.com"))
        // 172.32 不在私有段里
        assertEquals("https://172.32.0.1", normalizeLocalTestUrl("172.32.0.1"))
        // 显式端口也不改变"这不是本地地址"的事实
        assertEquals("https://example.com:8443/a", normalizeLocalTestUrl("example.com:8443/a"))
    }

    @Test
    fun explicitSchemeIsKept() {
        assertEquals("https://nzm-wiki.com/overlimit", normalizeLocalTestUrl("https://nzm-wiki.com/overlimit"))
        assertEquals("http://192.168.0.114:3000/", normalizeLocalTestUrl("http://192.168.0.114:3000/"))
        // 大小写不影响判定，原串原样保留
        assertEquals("HTTP://localhost:3000", normalizeLocalTestUrl("HTTP://localhost:3000"))
    }

    @Test
    fun rejectsUnusableInput() {
        assertNull(normalizeLocalTestUrl(""))
        assertNull(normalizeLocalTestUrl("   "))
        assertNull(normalizeLocalTestUrl("192.168.0.114:3000/a b"))
        assertNull(normalizeLocalTestUrl("/overlimit"))
        assertNull(normalizeLocalTestUrl("http://"))
    }

    @Test
    fun rejectsNonWebSchemes() {
        assertNull(normalizeLocalTestUrl("javascript:alert(1)"))
        assertNull(normalizeLocalTestUrl("data:text/html;base64,PHNjcmlwdD4="))
        assertNull(normalizeLocalTestUrl("file:///sdcard/a.html"))
        assertNull(normalizeLocalTestUrl("ftp://example.com/a"))
    }
}
