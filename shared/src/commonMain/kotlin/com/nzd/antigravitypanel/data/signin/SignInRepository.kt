package com.nzd.antigravitypanel.data.signin

import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
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
 * 福利站签到 / 积分 / 任务中心的数据源。
 *
 * 签到那一路照小程序抓包的顺序：**先 list 再 do，签完再 list 一次**。
 * 直接盲打 `signin/do` 是错的——重复签到会怎样服务端没明说，
 * 而且那样也拿不到"这次发到手的是什么"。
 *
 * 积分与任务中心是**另一路**，和签不签到无关：没签到的日子任务奖励照样能领，
 * 领不到也不该把签到看板一起打没。所以它们走 [enrich]，失败只退回上一轮的值。
 */
class SignInRepository(
    private val api: NzApi,
    private val store: KeyValueStore,
) {
    /**
     * 拉看板（含积分与每日任务）。签到看板失败直接抛，由调用方决定显示什么。
     *
     * @param fallback 上一轮的值。积分 / 任务那两路失败时用它兜底，
     *   免得一次抖动把积分显示成"没拿到"。
     */
    suspend fun load(
        cookie: MiniProgramCredential,
        fallback: SignInStatus = SignInStatus(),
    ): SignInStatus {
        // 凭证默认挂在 api 单例上，但那是个"别人什么时候设过"的隐式前提。
        // 每个入口自己先设一遍，免得哪天调用顺序一变就拿到空凭证。
        api.updateCookie(cookie)
        return enrich(api.signInList().toSignInStatus(), fallback)
    }

    /**
     * 补上积分与任务中心那两条。
     *
     * 这两路是**附属**的，挂了不能把签到看板一起打没，所以失败时退回 [fallback]。
     */
    private suspend fun enrich(base: SignInStatus, fallback: SignInStatus): SignInStatus {
        val score = runCatching { api.scoreRedeemList().totalScore() }.getOrNull()
        val daily = runCatching { api.taskLabel().dailyTask() }.getOrNull()
        return base.copy(
            totalScore = score ?: fallback.totalScore,
            dailyTask = daily ?: fallback.dailyTask,
        )
    }

    /**
     * 签到。已经签过就直接返回，不重复打 [NzApi.signInDo]。
     *
     * 签完再拉一次看板：连续天数、累计天数是服务端算的，
     * 只用 do 返回的奖励拼一份 status 会缺掉这些数字。
     */
    suspend fun sign(cookie: MiniProgramCredential): SignInOutcome {
        api.updateCookie(cookie)
        val before = api.signInList().toSignInStatus()
        if (before.signedToday) return SignInOutcome.AlreadySigned(enrich(before, SignInStatus()))

        val result = api.signInDo()
        val after = runCatching { load(cookie, before) }.getOrDefault(before)
        return SignInOutcome.Signed(
            status = after.copy(signedToday = true),
            reward = result.rewardText(),
        )
    }

    /**
     * 领取任务中心里已达成、还没领的每日 / 每周奖励。
     *
     * **一次只领一个任务**（接口就是这么设计的），逐个发请求，坏掉的那一个
     * 跳过继续下一个——不能因为第一个失败就把后面能领的也丢了。
     *
     * @param skip 这一天已经领过的 taskId，领过的不再重复发。
     * @return 领到的 `taskId -> 奖励文案`。空列表表示这一轮一个都没领到
     *   （要么没有可领的，要么全失败了）。
     */
    suspend fun claimTaskRewards(
        cookie: MiniProgramCredential,
        skip: Set<Int> = emptySet(),
    ): Map<Int, String> {
        api.updateCookie(cookie)
        val claimed = LinkedHashMap<Int, String>()
        for (task in api.taskLabel().claimableTasks()) {
            if (task.taskId in skip) continue
            val result = runCatching { api.taskReward(task.groupId, task.taskId) }.getOrNull()
            val text = result?.rewardText().orEmpty()
            // Ret != 0 时 rewardText 也会给一个兜底文案，那种情况不算领到，不记标记，
            // 下次开 app 还要再试一次
            if (result?.res?.firstOrNull()?.Ret == 0) {
                claimed[task.taskId] = text.ifBlank { task.name }
            }
        }
        return claimed
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
        runCatching { store.remove(StoreKey.WELFARE_TASK_AUTO_MARK) }
    }

    /**
     * 自动签到的去重标记。值是 `日期|openid`：
     * 同一天同一个号只自动试一次，换号（登出再登另一个）要重新试。
     */
    suspend fun autoMark(): String? = runCatching { store.read(StoreKey.SIGNIN_AUTO_MARK) }.getOrNull()

    suspend fun markAutoDone(mark: String) {
        runCatching { store.write(StoreKey.SIGNIN_AUTO_MARK, mark) }
    }

    /**
     * 这一天已经自动领过的任务。值形如 `日期|openid|65928,65929`，
     * 日期或 openid 对不上就当**一个都没领过**——换号、换天都要重新试。
     */
    suspend fun claimedTaskIds(mark: String): Set<Int> {
        val raw = runCatching { store.read(StoreKey.WELFARE_TASK_AUTO_MARK) }.getOrNull()
            ?: return emptySet()
        val parts = raw.split("|")
        if (parts.size < 3) return emptySet()
        if ("${parts[0]}|${parts[1]}" != mark) return emptySet()
        return parts[2].split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    /**
     * 记下这一天领过哪些。**只记真正领到的**（见 [claimTaskRewards]），
     * 没领到的留着下次再试。
     */
    suspend fun markTaskClaimed(mark: String, ids: Set<Int>) {
        if (ids.isEmpty()) return
        val merged = claimedTaskIds(mark) + ids
        runCatching {
            store.write(StoreKey.WELFARE_TASK_AUTO_MARK, "$mark|${merged.sorted().joinToString(",")}")
        }
    }
}
