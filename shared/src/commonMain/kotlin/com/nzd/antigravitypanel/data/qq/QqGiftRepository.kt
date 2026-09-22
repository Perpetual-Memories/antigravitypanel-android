package com.nzd.antigravitypanel.data.qq

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.util.currentEpochSeconds

/**
 * 周签到礼包的领取结果。
 *
 * 和小程序签到那套一样分成两支：UI 要区分"这次真的领到了"和"本来就不能领"。
 * 混在一起的话自动领取会天天弹一个"领取成功"，那是噪音。
 */
sealed interface QqClaimOutcome {
    val status: QqWeeklySignIn

    /** 这次调了领取接口，签到那格的状态变了。 */
    data class Claimed(override val status: QqWeeklySignIn) : QqClaimOutcome

    /** 服务端说现在没有可领的，没调领取接口。 */
    data class NothingToClaim(override val status: QqWeeklySignIn) : QqClaimOutcome
}

/**
 * 游戏中心周签到的数据源。
 *
 * 顺序照抓包来：**先查角色 → 再读首页 → 最后领 → 领完再读一次**。
 * 领取必须带 `areaInfo`（角色），所以第一步躲不掉；
 * 领完再读一次是因为"第几天 / 明天领什么"只有查询接口给。
 */
class QqGiftRepository(
    private val api: QqGameCenterApi,
    private val store: KeyValueStore,
) {
    /** 读状态。失败直接抛，由调用方决定显示什么。 */
    suspend fun load(credential: QqCredential): QqWeeklySignIn {
        val status = api.firstScreen(credential).weeklySignIn()
        // 角色名顺带拿一下：让用户确认领的是不是自己的号。
        // 这一步失败不影响签到状态本身，所以它挂了就算了。
        val role = runCatching { api.gameUserInfo(credential) }.getOrNull()
        return status.copy(roleName = role?.roleName.orEmpty())
    }

    /**
     * 领取。已经不能领就直接返回，不调 [QqGameCenterApi.exchangeAllGifts]。
     *
     * ⚠️ 那个接口是**批量**的，会把服务端认为能领的礼包一起领掉，
     * 不只签到这一个。调用方（UI）必须让用户知情。
     */
    suspend fun claim(credential: QqCredential): QqClaimOutcome {
        val before = api.firstScreen(credential).weeklySignIn()
        if (!before.canClaim) return QqClaimOutcome.NothingToClaim(before)

        val area = api.gameUserInfo(credential)
        api.exchangeAllGifts(credential, area)
        val after = runCatching { api.firstScreen(credential).weeklySignIn() }.getOrDefault(before)
        return QqClaimOutcome.Claimed(after.copy(roleName = area.roleName))
    }

    suspend fun readCached(): QqGiftCache? =
        runCatching { store.read(StoreKey.QQ_GIFT_CACHE) }.getOrNull()
            ?.let { QqGiftCacheCodec.decode(it) }

    /** 只在读取**成功**时调用：拉失败要留着上一份。 */
    suspend fun writeCache(status: QqWeeklySignIn) {
        runCatching {
            store.write(
                StoreKey.QQ_GIFT_CACHE,
                QqGiftCacheCodec.encode(status.toCache(currentEpochSeconds())),
            )
        }
    }

    suspend fun readCredential(): QqCredential? =
        runCatching { store.read(StoreKey.QQ_COOKIE_RAW) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { parseQqCredential(it) }.getOrNull() }

    suspend fun saveCredential(raw: String): QqCredential {
        val credential = parseQqCredential(raw)
        store.write(StoreKey.QQ_COOKIE_RAW, credential.raw)
        return credential
    }

    suspend fun clearCredential() {
        runCatching { store.remove(StoreKey.QQ_COOKIE_RAW) }
        runCatching { store.remove(StoreKey.QQ_GIFT_CACHE) }
        runCatching { store.remove(StoreKey.QQ_GIFT_AUTO_MARK) }
    }

    /**
     * 自动领取的去重标记，值是 `日期|uin`。
     * 同一天同一个 QQ 只自动试一次，换号要重新试。
     */
    suspend fun autoMark(): String? =
        runCatching { store.read(StoreKey.QQ_GIFT_AUTO_MARK) }.getOrNull()

    suspend fun markAutoDone(mark: String) {
        runCatching { store.write(StoreKey.QQ_GIFT_AUTO_MARK, mark) }
    }
}
