package com.nzd.antigravitypanel.data.xinyue

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.util.currentEpochSeconds

/**
 * 悦享卡领取结果。
 *
 * 和另两块签到一样分成两支：UI 要区分"这次真的领到了"和"本来就不能领"。
 * 混在一起的话自动领取会天天弹一个"领取成功"，那是噪音。
 */
sealed interface XinyueClaimOutcome {
    val status: XinyueCardStatus

    /** 这次调了领取接口。 */
    data class Claimed(override val status: XinyueCardStatus) : XinyueClaimOutcome

    /** 没卡 / 已过期 / 今天领过了，没调领取接口。 */
    data class NothingToClaim(override val status: XinyueCardStatus) : XinyueClaimOutcome
}

/**
 * 心悦悦享卡的数据源。
 *
 * 顺序照抓包来：**先 MyCardList → 再 ReceiveGift → 领完再查一次**。
 * 领完再查是因为已领次数是服务端算的，只有查询接口给；
 * 而且奖励内容只在领取响应里，得把它带进新的状态里。
 *
 * 判断"今天领没领"用响应里的 `gift_status`，**不用设备时钟**——
 * 心悦那边的日切时刻我们不掌握。
 */
class XinyueRepository(
    private val api: XinyueClubApi,
    private val store: KeyValueStore,
) {
    /** 读状态。失败直接抛，由调用方决定显示什么。 */
    suspend fun load(credential: XinyueCredential): XinyueCardStatus =
        api.myCardList(credential).yuexiangStatus(currentEpochSeconds())

    /**
     * 领当天的每日礼包。不能领就直接返回，不打 [XinyueClubApi.receiveGift]。
     *
     * ⚠️ 那个接口**真的会往角色上发货**（抓包里领到的是 NZ点 x200）。
     * 和游戏中心那个批量接口不同，它只领传进去的这张卡，所以自动领取默认开。
     */
    suspend fun claim(credential: XinyueCredential): XinyueClaimOutcome {
        val list = api.myCardList(credential)
        val card = list.pickYuexiangCard()
        val before = list.yuexiangStatus(currentEpochSeconds())
        if (card == null || before.expired || !before.canClaim) {
            return XinyueClaimOutcome.NothingToClaim(before)
        }

        val reward = api.receiveGift(credential, card).rewardText()
        val after = runCatching {
            api.myCardList(credential).yuexiangStatus(currentEpochSeconds(), reward)
        }.getOrDefault(before.copy(canClaim = false, rewardText = reward))
        return XinyueClaimOutcome.Claimed(after)
    }

    suspend fun readCached(): XinyueCardCache? =
        runCatching { store.read(StoreKey.XINYUE_CARD_CACHE) }.getOrNull()
            ?.let { XinyueCardCacheCodec.decode(it) }

    /** 只在读取**成功**时调用：拉失败要留着上一份。 */
    suspend fun writeCache(status: XinyueCardStatus) {
        runCatching {
            store.write(
                StoreKey.XINYUE_CARD_CACHE,
                XinyueCardCacheCodec.encode(status.toCache(currentEpochSeconds())),
            )
        }
    }

    suspend fun readCredential(): XinyueCredential? =
        runCatching { store.read(StoreKey.XINYUE_CREDENTIAL_RAW) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { parseXinyueCredential(it) }.getOrNull() }

    suspend fun saveCredential(raw: String): XinyueCredential {
        val credential = parseXinyueCredential(raw)
        store.write(StoreKey.XINYUE_CREDENTIAL_RAW, credential.raw)
        return credential
    }

    suspend fun clearCredential() {
        runCatching { store.remove(StoreKey.XINYUE_CREDENTIAL_RAW) }
        runCatching { store.remove(StoreKey.XINYUE_CARD_CACHE) }
        runCatching { store.remove(StoreKey.XINYUE_AUTO_MARK) }
    }

    /** 自动领取的去重标记，值是 `日期|openid`。同一天同一个号只自动试一次。 */
    suspend fun autoMark(): String? =
        runCatching { store.read(StoreKey.XINYUE_AUTO_MARK) }.getOrNull()

    suspend fun markAutoDone(mark: String) {
        runCatching { store.write(StoreKey.XINYUE_AUTO_MARK, mark) }
    }
}
