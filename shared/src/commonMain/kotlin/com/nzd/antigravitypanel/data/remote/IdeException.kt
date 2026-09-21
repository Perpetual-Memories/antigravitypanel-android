package com.nzd.antigravitypanel.data.remote

/** 接口层所有异常的父类，UI 只需要 catch 这一个。 */
sealed class NzException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * `iRet != 0`：凭证失效或请求被拒。
 * 这是最常见的失败，UI 上要引导用户重新粘贴 cookie。
 */
class CookieExpiredException(val iRet: Int, val sMsg: String?) :
    NzException("凭证失效或请求被拒（iRet=$iRet${sMsg?.let { ", $it" } ?: ""}）")

/**
 * `code != 0`：业务层报错。
 * 注意 `iRet == 0` 但数据为空是另一种情况——用户没同意数据协议，
 * 这时候响应里 `data` 是 null，由调用方自己判空，不在这里抛。
 */
class ApiException(val code: Int, val msg: String?) :
    NzException("接口返回 code=$code${msg?.let { ", $it" } ?: ""}")

/** 网络不通、超时，或者响应结构不认识。 */
class ProtocolException(message: String, cause: Throwable? = null) : NzException(message, cause)

/** 还没设置 cookie 就发请求了。 */
class MissingCredentialException : NzException("还没有设置 cookie")
