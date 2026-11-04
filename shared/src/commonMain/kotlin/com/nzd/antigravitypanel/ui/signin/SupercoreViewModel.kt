package com.nzd.antigravitypanel.ui.signin

import com.nzd.antigravitypanel.data.credential.HarCredentialScanner
import com.nzd.antigravitypanel.data.credential.SignInCredentialKind
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.supercore.SupercoreActivityException
import com.nzd.antigravitypanel.data.supercore.SupercoreApi
import com.nzd.antigravitypanel.data.supercore.SupercoreApiException
import com.nzd.antigravitypanel.data.supercore.SupercoreCacheCodec
import com.nzd.antigravitypanel.data.supercore.SupercoreCredential
import com.nzd.antigravitypanel.data.supercore.SupercoreCredentialException
import com.nzd.antigravitypanel.data.supercore.SupercoreGiftRecord
import com.nzd.antigravitypanel.data.supercore.SupercoreHold
import com.nzd.antigravitypanel.data.supercore.SupercorePlan
import com.nzd.antigravitypanel.data.supercore.SupercorePoints
import com.nzd.antigravitypanel.data.supercore.SupercoreRepository
import com.nzd.antigravitypanel.data.supercore.SupercoreRequestException
import com.nzd.antigravitypanel.data.supercore.SupercoreRole
import com.nzd.antigravitypanel.data.supercore.SupercoreStatus
import com.nzd.antigravitypanel.data.supercore.SupercoreTeam
import com.nzd.antigravitypanel.data.supercore.normalizeSupercoreUrl
import com.nzd.antigravitypanel.data.supercore.rewardTextOf
import com.nzd.antigravitypanel.data.supercore.string
import com.nzd.antigravitypanel.data.supercore.toStatus
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

data class SupercoreUiState(
    val status: SupercoreStatus = SupercoreStatus(),
    /** 从活动页配置里解析出来的 flow 计划。UI 用它显示活动名与「认没认出来」。 */
    val plan: SupercorePlan? = null,
    val url: String = "",
    /** 已保存的凭证原文，弹层里回显用。 */
    val credentialRaw: String = "",
    val bound: Boolean = false,
    /** 手上有一份能看的数字（本次拉到的或缓存）。 */
    val available: Boolean = false,
    val loading: Boolean = false,
    /** 正在领取 / 抽奖。和 [loading] 分开：前者会改服务端状态，要挡重复点击。 */
    val running: Boolean = false,
    val error: String? = null,
    /** 一次性结果提示（签到了什么、抽到了什么），显示完由 UI 调 [consumeNotice]。 */
    val notice: String? = null,
    /** 官方「领奖记录」，直接从服务端拉，不做本地记账。 */
    val records: List<SupercoreGiftRecord> = emptyList(),
    val recordsTotal: Int = 0,
    val recordsLoading: Boolean = false,
    val recordsError: String? = null,
    val roles: List<SupercoreRole> = emptyList(),
    /** 选中的角色 key。多角色账号才有意义。 */
    val roleKey: String = "",
    /**
     * 队伍还没成团，领取会被服务端拒掉。
     *
     * 只在**队长 + 满员**时为 true —— 那才有"确认成团"按钮可点；
     * 队员只显示"等队长确认"。
     */
    val needsTeamConfirm: Boolean = false,
    val teamLeader: Boolean = false,
) {
    val activityTitle: String get() = plan?.title.orEmpty()
    val configured: Boolean get() = url.isNotBlank() && bound
}

/**
 * 超核组队活动（`act.supercore.qq.com` + `bgw.xinyue.qq.com/amp2.WPESrv/WPEIndex`）。
 *
 * **第四块签到**，和另三块一样是独立凭证、独立接口、独立状态。
 *
 * 它和另三块最大的不同：**活动网址每期都换**，而活动号、按钮 flow id 全在活动页配置里。
 * 所以刷新时要先"读配置"（缓存 12 小时），再拿 flow id 去请求；不写死任何一期的活动号。
 */
class SupercoreViewModel(
    store: KeyValueStore,
    private val api: SupercoreApi = SupercoreApi(),
    /** 冷启动同步读到的状态缓存。 */
    cachedSeed: String? = null,
    /** 冷启动同步读到的凭证原文。少了它第一帧会先闪一句"要配置"。 */
    credentialSeed: String? = null,
    urlSeed: String? = null,
) {
    private val repository = SupercoreRepository(store, api)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val seededStatus = cachedSeed?.let { SupercoreCacheCodec.decode(it) }
        ?.toStatus(currentEpochSeconds())
    private val seededBound = !credentialSeed.isNullOrBlank()

    private val _state = MutableStateFlow(
        SupercoreUiState(
            status = seededStatus ?: SupercoreStatus(),
            url = urlSeed.orEmpty(),
            credentialRaw = credentialSeed.orEmpty(),
            bound = seededBound,
            available = seededStatus != null,
        ),
    )
    val state: StateFlow<SupercoreUiState> = _state.asStateFlow()

    /** 同步那一路没读到的兜底。排在首刷之前。 */
    suspend fun restore() {
        val url = repository.readUrl()
        val credential = repository.readCredential()
        val cached = repository.readCachedStatus()
        val roleKey = repository.readRoleKey()
        val plan = if (url.isNotBlank()) {
            repository.readPlan(url, maxAgeSec = Long.MAX_VALUE)
        } else {
            null
        }
        _state.value = _state.value.copy(
            url = url,
            credentialRaw = credential?.raw ?: _state.value.credentialRaw,
            bound = credential != null,
            plan = plan ?: _state.value.plan,
            status = cached ?: _state.value.status,
            available = cached != null || _state.value.available,
            roleKey = roleKey,
        )
    }

    /**
     * 拉状态；[autoRun] 为真时顺手领任务积分、领签到奖励并抽奖。
     *
     * 自动跑**不做按天去重**：领取接口本身按 remain 卡着（已领的 flow 是 0），
     * 而积分是白天打完一局才到账的 —— 按天去重会让"下午打完再刷新"永远领不到。
     */
    fun refresh(autoRun: Boolean) {
        if (_state.value.running || _state.value.loading) return
        val url = _state.value.url
        if (url.isBlank() || !_state.value.bound) {
            _state.value = _state.value.copy(loading = false, error = null)
            return
        }

        scope.launch {
            _state.value = _state.value.copy(
                loading = true,
                running = autoRun,
                error = null,
            )
            try {
                val credential = repository.readCredential()
                    ?: throw SupercoreCredentialException("还没填凭证")
                val plan = repository.loadPlan(url)
                val roles = runCatching { api.roles(credential, plan) }.getOrDefault(emptyList())
                val role = pickRole(roles)
                if (role != null && _state.value.roleKey.isBlank()) {
                    repository.saveRoleKey(role.key)
                }

                // 先看队伍：**没成团时服务端对领取只回一句"网络繁忙"**，看不出原因。
                // 这里提前拦住，队长还能拿到一颗「确认成团」按钮。
                val teamResult = runCatching { api.team(credential, plan, role) }
                val team = teamResult.getOrNull()
                val teamBlock = teamBlockOf(team)
                // 队伍 flow 存在却读不到：把原因说出来，别让它伪装成领取时的"网络繁忙"
                val teamFailure = if (plan.teamInfoFlow != null && team == null) {
                    teamResult.exceptionOrNull()?.let { describeSupercoreError(it) }
                        ?: "队伍状态没读到，稍后再试"
                } else {
                    null
                }
                if (teamBlock != null || teamFailure != null) {
                    val holds = runCatching { api.holds(credential, plan) }.getOrDefault(emptyMap())
                    val points = runCatching { api.points(credential, plan, role) }
                        .getOrDefault(SupercorePoints(null, null))
                    apply(
                        plan = plan,
                        status = buildStatus(
                            plan = plan,
                            holds = holds,
                            points = points,
                            signedToday = _state.value.status.signedToday && !_state.value.status.stale,
                            notStarted = false,
                            message = teamBlock?.message ?: teamFailure.orEmpty(),
                        ),
                        roles = roles,
                        notice = null,
                        needsTeamConfirm = teamBlock?.needsConfirm == true,
                        teamLeader = teamBlock?.leader == true,
                    )
                    return@launch
                }

                if (!autoRun) {
                    // holds 失败**不能吞**：凭证过期时它会抛 7001，吞掉就只剩缓存里那句
                    // 旧的"网络繁忙"挂在卡片上，用户根本看不出是登录态坏了。
                    val holds = api.holds(credential, plan)
                    val points = runCatching { api.points(credential, plan, role) }
                        .getOrDefault(SupercorePoints(null, null))
                    apply(
                        plan = plan,
                        status = buildStatus(
                            plan = plan,
                            holds = holds,
                            points = points,
                            signedToday = _state.value.status.signedToday && !_state.value.status.stale,
                            notStarted = _state.value.status.notStarted,
                            message = _state.value.status.message,
                        ),
                        roles = roles,
                        notice = null,
                    )
                    return@launch
                }

                val outcome = execute(credential, plan, role, team)
                apply(
                    plan = plan,
                    status = outcome.status,
                    roles = roles,
                    notice = outcome.notice,
                )
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    running = false,
                    error = describeSupercoreError(e),
                    // 清掉缓存里上一轮的服务端文案（比如"网络繁忙"），
                    // 否则红字报的是"凭证过期"，上面还挂着一句旧的"网络繁忙"，非常误导。
                    status = _state.value.status.copy(message = ""),
                )
            }
        }
    }

    // 「立即领取并抽奖」那颗按钮已经删了，手动入口改成**下拉签到二级页**
    // （那边的 onRefresh 直接调 refresh(autoRun = true)），所以这里不再留 runNow()。

    /**
     * 拉官方「领奖记录」。切到记录页时调用；每次打开都重新拉，避免看到旧列表。
     */
    fun loadRecords() {
        if (_state.value.recordsLoading) return
        val url = _state.value.url
        if (url.isBlank() || !_state.value.bound) return
        scope.launch {
            _state.value = _state.value.copy(recordsLoading = true, recordsError = null)
            try {
                val credential = repository.readCredential()
                    ?: throw SupercoreCredentialException("还没填凭证")
                val plan = _state.value.plan ?: repository.loadPlan(url)
                val page = api.giftRecords(credential, plan)
                _state.value = _state.value.copy(
                    records = page.records,
                    recordsTotal = page.total,
                    recordsLoading = false,
                    recordsError = null,
                )
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    recordsLoading = false,
                    recordsError = describeSupercoreError(e),
                )
            }
        }
    }

    /**
     * 队长确认成团。**不可逆**：确认后成员锁定，不能退出或换人。
     *
     * 只由 UI 上那颗带二次确认的按钮调用，自动跑**不会**替用户做这件事。
     */
    fun confirmTeam() {
        if (_state.value.running || _state.value.loading) return
        scope.launch {
            _state.value = _state.value.copy(loading = true, running = true, error = null)
            try {
                val credential = repository.readCredential()
                    ?: throw SupercoreCredentialException("还没填凭证")
                val plan = repository.loadPlan(_state.value.url)
                val roles = runCatching { api.roles(credential, plan) }.getOrDefault(emptyList())
                val role = pickRole(roles)
                api.confirmTeam(credential, plan, role)
                _state.value = _state.value.copy(
                    loading = false,
                    running = false,
                    needsTeamConfirm = false,
                    notice = "已确认成团，开始领取…",
                )
                refresh(autoRun = true)
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    running = false,
                    error = describeSupercoreError(e),
                    // 清掉缓存里上一轮的服务端文案（比如"网络繁忙"），
                    // 否则红字报的是"凭证过期"，上面还挂着一句旧的"网络繁忙"，非常误导。
                    status = _state.value.status.copy(message = ""),
                )
            }
        }
    }

    /** 保存请求头凭证并立刻拉一次。 */
    suspend fun saveCredential(raw: String): SupercoreCredential? {
        val credential = runCatching { repository.saveCredential(raw) }.getOrNull()
        if (credential == null) {
            _state.value = _state.value.copy(
                error = "没认出来，要一段含 T-OPENID 和 T-ACCESS-TOKEN 的请求头",
            )
            return null
        }
        _state.value = _state.value.copy(
            bound = true,
            credentialRaw = credential.raw,
            error = null,
        )
        refresh(autoRun = false)
        return credential
    }

    /**
     * 从 HAR 里一次补齐「活动网址 + 凭证」。
     *
     * 抓包本来就是用户要做的一步，能一次填完两个框就不该让他再抄一遍网址。
     * 三种失败分开说：不是 HAR / 抓错页 / 抓对了但请求头不全。
     */
    suspend fun importFromHar(text: String) {
        // 抓包动不动十几 MB，解析放 Default：这里是从 UI 的 scope 调过来的
        val scan = withContext(Dispatchers.Default) { HarCredentialScanner.scan(text) }
        if (!scan.looksLikeHar) {
            _state.value = _state.value.copy(error = "这个文件读不出 HAR 结构，换一份抓包文件试试")
            return
        }
        val url = scan.supercoreActivityUrl?.let { normalizeSupercoreUrl(it) }
        val raw = scan.raw(SignInCredentialKind.SUPERCORE)
        if (raw == null) {
            _state.value = _state.value.copy(
                error = "这份 HAR 里没有超核活动的请求，要在活动页里点一下领取再导出",
            )
            return
        }
        if (url != null) {
            val changed = url != _state.value.url
            repository.saveUrl(url)
            if (changed) repository.clearStatus()
            _state.value = _state.value.copy(
                url = url,
                plan = null,
                error = null,
                status = if (changed) SupercoreStatus() else _state.value.status,
                available = if (changed) false else _state.value.available,
            )
        }
        if (saveCredential(raw) == null) {
            _state.value = _state.value.copy(
                error = "HAR 里找到超核请求了，但 T-OPENID 或 T-ACCESS-TOKEN 少了一个",
            )
        }
    }

    suspend fun clearCredential() {
        repository.clearCredential()
        _state.value = _state.value.copy(
            bound = false,
            credentialRaw = "",
            status = SupercoreStatus(),
            available = false,
            roles = emptyList(),
            roleKey = "",
            records = emptyList(),
            recordsTotal = 0,
            recordsError = null,
            error = null,
        )
    }

    /** 换角色。多角色账号领奖前要确认发给谁。 */
    suspend fun selectRole(key: String) {
        repository.saveRoleKey(key)
        _state.value = _state.value.copy(roleKey = key)
    }

    fun consumeNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun close() {
        scope.cancel()
    }

    // ---------------- 内部 ----------------

    private suspend fun pickRole(roles: List<SupercoreRole>): SupercoreRole? {
        if (roles.isEmpty()) return null
        val key = _state.value.roleKey
        return roles.firstOrNull { it.key == key } ?: roles.first()
    }

    /**
     * 一次完整的「领任务积分 → 领签到奖励 → 抽奖」。
     *
     * 顺序不能反：任务积分要到账之后才够抽奖；签到奖励的领取本身会推进累计天数。
     * 每一步失败都只影响自己那一块，不把整轮打没（除了"活动没开"——那没必要再试后面的）。
     */
    /**
     * 领奖 / 签到 / 抽奖的写请求。
     *
     * 服务端在并发或短时间连续写入时会回「网络繁忙，请稍后再试」（官方页是用户一下下点的，
     * 不会连着打；我们是循环发）。碰到这一句就等 900ms 重试一次，其余错误原样抛。
     */
    private suspend fun runWrite(
        credential: SupercoreCredential,
        plan: SupercorePlan,
        flow: Int,
        role: SupercoreRole?,
        userAttach: String,
    ): JsonObject {
        try {
            return api.runFlow(credential, plan, flow, role, userAttach = userAttach)
        } catch (e: SupercoreApiException) {
            if (!e.msg.orEmpty().contains("网络繁忙")) throw e
            delay(900)
            return api.runFlow(credential, plan, flow, role, userAttach = userAttach)
        }
    }

    private suspend fun execute(
        credential: SupercoreCredential,
        plan: SupercorePlan,
        role: SupercoreRole?,
        team: SupercoreTeam?,
    ): RunOutcome {
        // 官方页每条领取都带昵称头像；从队伍成员 self 取，拿不到就空着
        val userAttach = team?.userAttach.orEmpty()
        var notStarted = false
        var message = _state.value.status.message
        // 「为什么这一轮什么都没领到」：没成团 / 任务没完成 / 积分不足。
        // 服务端这类拒绝常常只是一个 msg，不抛 ret，所以要单独记下来给卡片显示。
        var hint = ""
        var signedToday = _state.value.status.signedToday && !_state.value.status.stale
        val notes = ArrayList<String>()

        // 同样不吞：读不到 holdList 时后面的 remain 全是 0，会静默什么都不领
        val holds0 = api.holds(credential, plan)
        // 1) 任务积分。两条任务都是"完成了才给领"，服务端用 remain 卡着。
        for (flow in plan.taskFlows) {
            if ((holds0[flow]?.remain ?: 0) <= 0) continue
            try {
                // 返回体只有"领到了什么"，积分去向由后面的 points flow 读，不用这里记
                val data = runWrite(credential, plan, flow, role, userAttach)
                val refusal = refusalMessage(data)
                if (refusal != null) {
                    if (refusal.contains("开启") || refusal.contains("未开始")) {
                        notStarted = true
                        message = refusal
                        return finish(
                            plan = plan,
                            credential = credential,
                            role = role,
                            signedToday = signedToday,
                            notes = notes,
                            notStarted = true,
                            message = message,
                        )
                    }
                    if (hint.isBlank()) hint = refusal
                } else {
                    notStarted = false
                    hint = ""
                }
            } catch (e: SupercoreApiException) {
                if (e.notStarted) {
                    notStarted = true
                    message = e.msg.orEmpty()
                    // 活动没开：签到和抽奖的 flow 只会回同一句，不用再打
                    return finish(
                        plan = plan,
                        credential = credential,
                        role = role,
                        signedToday = signedToday,
                        notes = notes,
                        notStarted = true,
                        message = message,
                    )
                }
                // 已经领过 / 没完成：记下原因，跳过这一条，继续下一条
                if (hint.isBlank()) hint = e.msg.orEmpty()
            } catch (t: Throwable) {
                // 网络抖动：只跳过这一条
            }
        }

        // 2) 每日签到。按 1..n 天顺序领，领到"还没到时间"就停 ——
        //    后面的天只会更没到，继续打没有意义。
        val claimedSignBefore = plan.signFlows.count { (holds0[it]?.remain ?: 1) <= 0 }
        for (flow in plan.signFlows) {
            if ((holds0[flow]?.remain ?: 0) <= 0) continue
            try {
                val data = runWrite(credential, plan, flow, role, userAttach)
                val refusal = refusalMessage(data)
                if (refusal != null) {
                    // 服务端有几种失败是内层 ret=0 + msg 表达的，不能当成"领到了"
                    val locked = refusal.contains("时间未到") ||
                        refusal.contains("未满足任务条件")
                    if (refusal.contains("已签到") || refusal.contains("已领取") ||
                        refusal.contains("明日") || refusal.contains("明天") ||
                        // 已经领过若干天、下一天又"没到时间/条件不满足" ⇒ 今天这一次是在别处签的。
                        // 签到是累计天数解锁的，第 N 天的奖励要到第 N 天才可领。
                        (locked && claimedSignBefore > 0)
                    ) {
                        signedToday = true
                    } else if (hint.isBlank()) {
                        hint = refusal
                    }
                    break
                }
                signedToday = true
                notStarted = false
                hint = ""
                val reward = rewardTextOf(data)
                if (reward.isNotBlank() && reward != "抽奖成功") notes += "签到：$reward"
            } catch (e: SupercoreApiException) {
                if (e.notStarted) {
                    notStarted = true
                    message = e.msg.orEmpty()
                } else if (e.alreadyDone) {
                    signedToday = true
                } else if (claimedSignBefore > 0 && (
                        e.msg.orEmpty().contains("时间未到") ||
                            e.msg.orEmpty().contains("未满足任务条件")
                        )
                ) {
                    // 今天已经签过（在官方页或别的设备），下一天的奖励还没解锁
                    signedToday = true
                } else if (hint.isBlank()) {
                    hint = e.msg.orEmpty()
                }
                break
            } catch (t: Throwable) {
                break
            }
        }

        // 3) 抽奖。领完积分再读一次，别拿旧余额去算能抽几次。
        var draws = 0
        var lastError: String? = null
        val pointsBefore = runCatching { api.points(credential, plan, role) }
            .getOrDefault(SupercorePoints(null, null))
        val holdsBefore = runCatching { api.holds(credential, plan) }.getOrDefault(holds0)
        var left = pointsBefore.remain ?: 0
        var drawsLeft = plan.lotteryFlow?.let { holdsBefore[it]?.remain } ?: 0
        val lotteryFlow = plan.lotteryFlow

        while (lotteryFlow != null &&
            left >= plan.drawCost &&
            drawsLeft > 0 &&
            draws < MAX_DRAWS_PER_RUN
        ) {
            try {
                val data = runWrite(credential, plan, lotteryFlow, role, userAttach)
                val refusal = refusalMessage(data)
                if (refusal != null) {
                    lastError = refusal
                    break
                }
                val reward = rewardTextOf(data)
                notes += reward
                draws++
                left -= plan.drawCost
                drawsLeft--
            } catch (e: SupercoreApiException) {
                lastError = e.msg
                break
            } catch (t: Throwable) {
                lastError = t.message
                break
            }
        }

        // 抽完再读一次真实余额 / 次数；没抽就沿用刚才那份
        val holdsFinal = if (draws > 0) {
            runCatching { api.holds(credential, plan) }.getOrDefault(holdsBefore)
        } else {
            holdsBefore
        }
        val pointsFinal = if (draws > 0) {
            runCatching { api.points(credential, plan, role) }.getOrDefault(pointsBefore)
        } else {
            pointsBefore
        }

        val status = buildStatus(
            plan = plan,
            holds = holdsFinal,
            points = pointsFinal,
            signedToday = signedToday,
            notStarted = notStarted,
            // 优先说"这一轮为什么没办成"：没开活动 > 抽奖被拒 > 签到/任务被拒。
            // 都为空时把上一轮的话清掉，否则换成能领的状态后还挂着旧的"请先成团"。
            message = when {
                notStarted -> message
                !lastError.orEmpty().isBlank() -> lastError.orEmpty()
                else -> hint
            },
        )
        repository.writeStatus(status)

        val notice = buildNotice(notes, draws)
        return RunOutcome(status = status, notice = notice)
    }

    /** 活动没开时提前收尾：不再打签到/抽奖，但状态要照常落盘。 */
    private suspend fun finish(
        plan: SupercorePlan,
        credential: SupercoreCredential,
        role: SupercoreRole?,
        signedToday: Boolean,
        notes: List<String>,
        notStarted: Boolean,
        message: String,
    ): RunOutcome {
        val holds = runCatching { api.holds(credential, plan) }.getOrDefault(emptyMap())
        val points = runCatching { api.points(credential, plan, role) }
            .getOrDefault(SupercorePoints(null, null))
        val status = buildStatus(
            plan = plan,
            holds = holds,
            points = points,
            signedToday = signedToday,
            notStarted = notStarted,
            message = message,
        )
        repository.writeStatus(status)
        return RunOutcome(status = status, notice = buildNotice(notes, 0))
    }

    private suspend fun buildNotice(
        notes: List<String>,
        draws: Int,
    ): String? {
        if (notes.isEmpty()) return null
        val signNotes = notes.filter { it.startsWith("签到：") }
        val drawNotes = notes.filterNot { it.startsWith("签到：") }
        return buildString {
            if (signNotes.isNotEmpty()) {
                append("已签到：")
                append(signNotes.joinToString("、") { it.removePrefix("签到：") })
            }
            if (drawNotes.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append("抽奖 $draws 次：")
                append(drawNotes.take(5).joinToString("、"))
                if (drawNotes.size > 5) append(" 等")
            }
        }.ifBlank { null }
    }

    private fun buildStatus(
        plan: SupercorePlan,
        holds: Map<Int, SupercoreHold>,
        points: SupercorePoints,
        signedToday: Boolean,
        notStarted: Boolean,
        message: String,
    ): SupercoreStatus {
        val signClaimed = plan.signFlows.count { (holds[it]?.remain ?: 1) <= 0 }
        val taskClaimed = plan.taskFlows.count { (holds[it]?.remain ?: 1) <= 0 }
        val lottery = plan.lotteryFlow?.let { holds[it] }
        return SupercoreStatus(
            points = points.remain,
            pointsTotal = points.total,
            signClaimed = signClaimed,
            signTotal = plan.signFlows.size,
            tasksClaimed = taskClaimed,
            tasksTotal = plan.taskFlows.size,
            drawsRemain = lottery?.remain,
            drawsTotal = lottery?.total,
            signedToday = signedToday,
            notStarted = notStarted,
            message = message,
            savedAtSec = currentEpochSeconds(),
        )
    }

    private suspend fun apply(
        plan: SupercorePlan,
        status: SupercoreStatus,
        roles: List<SupercoreRole>,
        notice: String?,
        needsTeamConfirm: Boolean = false,
        teamLeader: Boolean = false,
    ) {
        // 只读刷新也要落盘：下一次冷启动才有"今天"的数字可摆，不用等自动跑完
        repository.writeStatus(status)
        _state.value = _state.value.copy(
            plan = plan,
            status = status,
            available = true,
            roles = roles,
            // 角色接口偶尔抽风时别把用户选过的那个清掉
            roleKey = if (roles.isEmpty()) {
                _state.value.roleKey
            } else {
                roles.firstOrNull { it.key == _state.value.roleKey }?.key
                    ?: roles.first().key
            },
            loading = false,
            running = false,
            error = null,
            notice = notice ?: _state.value.notice,
            needsTeamConfirm = needsTeamConfirm,
            teamLeader = teamLeader,
        )
    }

    private data class RunOutcome(val status: SupercoreStatus, val notice: String?)

    private data class TeamBlock(
        val message: String,
        val needsConfirm: Boolean,
        val leader: Boolean,
    )

    /**
     * 队伍状态 → 阻断信息。null = 已成团 / 读不到队伍（读不到就别拦，让领取自己报错）。
     */
    private fun teamBlockOf(team: SupercoreTeam?): TeamBlock? {
        if (team == null || team.confirmed) return null
        if (!team.inTeam) {
            return TeamBlock("还没有加入队伍，请先在活动页组队", needsConfirm = false, leader = false)
        }
        val progress = if (team.limit > 0) "（${team.current}/${team.limit}）" else ""
        val message = when {
            !team.full -> "队伍还没满员$progress，满员后队长才能确认成团"
            team.isLeader -> "队伍还没确认成团$progress，确认后才能领任务和签到"
            else -> "队伍还没确认成团$progress，等队长在活动页点「确认成团」"
        }
        return TeamBlock(
            message = message,
            needsConfirm = team.isLeader && team.full,
            leader = team.isLeader,
        )
    }

    companion object {
        /** 单轮最多抽几次。服务端上限 27，留一倍余量防死循环。 */
        private const val MAX_DRAWS_PER_RUN = 60
    }
}

/**
 * 服务端把「拒绝」写进 `data.msg` 而不是 ret 的情况（实测 524501 就是这样）。
 *
 * 只用来判"这条到底成没成"，不拿来给用户看；"未中奖"是**正常抽奖结果**，要排除。
 */
private fun refusalMessage(data: JsonObject): String? {
    val msg = data.string("msg").orEmpty().trim()
    if (msg.isBlank() || msg.contains("未中奖")) return null
    val markers = listOf(
        "未", "不能", "失败", "限制", "超过", "上限", "不足", "重复",
        "已领取", "已签到", "明日", "明天", "开启", "结束",
    )
    return msg.takeIf { text -> markers.any { text.contains(it) } }
}

/** 错误文案。凭证问题要说清"去哪重新抓"，别只说"请求失败"。 */
internal fun describeSupercoreError(e: Throwable): String = when (e) {
    is SupercoreCredentialException -> "凭证不对：${e.message}"
    is SupercoreActivityException -> e.message ?: "活动页认不出来"
    is SupercoreApiException -> when {
        e.loginExpired -> "凭证过期或对不上这个活动，重新抓一次超核活动页的请求头（多半是 T-APPID 不对）"
        e.notStarted -> e.msg.orEmpty().ifBlank { "活动还没开始" }
        else -> "活动返回 ret=${e.ret}：${e.msg ?: "没有说明"}"
    }

    is SupercoreRequestException -> e.message ?: "网络请求失败"
    else -> e.message?.takeIf { it.isNotBlank() } ?: "没拿到超核活动的数据"
}
