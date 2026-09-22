package com.nzd.antigravitypanel.data.signin

import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.util.currentEpochSeconds

/**
 * 签到结果。
 *
 * 分成两支而不是只返回一个 status：UI 要区分"这次真的签到了，给你奖励"
 * 和"早就签过了，什么都没发生"。混在一起的话，自动签到每次开 app 都会弹
 * 一句"签到成功"，那是噪音。
 */
sealed interface SignInOutcome {
    val status: SignInStatus

    /** 这次调用真的打了签到接口并发到了东西。 */
    data class Signed(override val status: SignInStatus, val reward: String) : SignInOutcome

    /** 服务端说今天已经签过了，没有打签到接口。 */
    data class AlreadySigned(override val status: SignInStatus) : SignInOutcome
}

/**
 * 福利站签到的数据源。
 *
 * 三个接口调用的顺序照小程序抓包来：**先 list 再 do，签完再 list 一次**。
 * 直接盲打 `signin/do` 是错的——重复签到会怎样服务端没明说，
 * 而且那样也拿不到"这次发到手的是什么"。
 */
class SignInRepository(
    private val api: NzApi,
    private val store: KeyValueStore,
) {
    /** 拉看板。失败直接抛，由调用方决定显示什么。 */
    suspend fun load(cookie: NzCookie): SignInStatus {
        // 凭证默认挂在 api 单例上，但那是个"别人什么时候设过"的隐式前提。
        // 每个入口自己先设一遍，免得哪天调用顺序一变就拿到空凭证。
        api.updateCookie(cookie)
        return api.signInList().toSignInStatus()
    }

    /**
     * 签到。已经签过就直接返回，不重复打 [NzApi.signInDo]。
     *
     * 签完再拉一次看板：连续天数、累计天数是服务端算的，
     * 只用 do 返回的奖励拼一份 status 会缺掉这些数字。
     */
    suspend fun sign(cookie: NzCookie): SignInOutcome {
        api.updateCookie(cookie)
        val before = api.signInList().toSignInStatus()
        if (before.signedToday) return SignInOutcome.AlreadySigned(before)

        val result = api.signInDo()
        val after = runCatching { api.signInList().toSignInStatus() }.getOrDefault(before)
        return SignInOutcome.Signed(
            status = after.copy(signedToday = true),
            reward = result.rewardText(),
        )
    }

    suspend fun readCached(): SignInCache? =
        runCatching { store.read(StoreKey.SIGNIN_CACHE) }.getOrNull()
            ?.let { SignInCacheCodec.decode(it) }

    /** 只在拉取成功时调用——拉失败要留着上一份，别把缓存写成空。 */
    suspend fun writeCache(status: SignInStatus) {
        runCatching {
            store.write(StoreKey.SIGNIN_CACHE, SignInCacheCodec.encode(status.toCache(currentEpochSeconds())))
        }
    }

    suspend fun clearCache() {
        runCatching { store.remove(StoreKey.SIGNIN_CACHE) }
        runCatching { store.remove(StoreKey.SIGNIN_AUTO_MARK) }
    }

    /**
     * 自动签到的去重标记。值是 `日期|openid`：
     * 同一天同一个号只自动试一次，换号（登出再登另一个）要重新试。
     */
    suspend fun autoMark(): String? = runCatching { store.read(StoreKey.SIGNIN_AUTO_MARK) }.getOrNull()

    suspend fun markAutoDone(mark: String) {
        runCatching { store.write(StoreKey.SIGNIN_AUTO_MARK, mark) }
    }
}
