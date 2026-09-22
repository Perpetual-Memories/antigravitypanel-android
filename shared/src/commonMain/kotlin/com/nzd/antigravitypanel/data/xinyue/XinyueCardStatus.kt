package com.nzd.antigravitypanel.data.xinyue

import com.nzd.antigravitypanel.data.xinyue.dto.XyMyCardListDto
import com.nzd.antigravitypanel.data.xinyue.dto.XyReceiveGiftDto
import com.nzd.antigravitypanel.data.xinyue.dto.XyUserCardDto
import com.nzd.antigravitypanel.util.serverDateKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 悦享卡每日礼包的展示状态。
 *
 * 三种"没有可领的"必须分开，UI 上给的话完全不一样：
 * - [hasCard] = false：这个心悦账号**没开过**悦享卡（或者开的是别的游戏的）
 * - [expired] = true：开过，但**已经过期**——抓的那张 2026-08-06 就到期了，
 *   所以这在实际使用中会是常态，不是异常
 * - 其余情况看 [canClaim]：今天领没领
 */
data class XinyueCardStatus(
    /** 有没有找到过悦享卡（生效中或已过期的都算）。 */
    val hasCard: Boolean = false,
    /** 卡已过期：到期时间已过，或服务端把它挪进了 `expire_user_info`。 */
    val expired: Boolean = false,
    /** 今天还能不能领。只有 [hasCard] 且未 [expired] 时这个字段才有意义。 */
    val canClaim: Boolean = false,
    /** 已经领了几次 / 一共能领几次。 */
    val gotNum: Int = 0,
    val totalNum: Int = 0,
    /** 发往哪个角色（已解 base64）。让用户确认领的是不是自己的号。 */
    val roleName: String = "",
    val partitionName: String = "",
    /** 到期日，形如 `2026-08-06`。 */
    val endDate: String = "",
    /** 这次领到的东西，形如 `NZ点×200`。**只有领取之后才有值**。 */
    val rewardText: String = "",
) {
    val hasData: Boolean get() = hasCard
}

/** 逆战未来在心悦的 gid（`MyCardList` 返回的卡上带的那个数字，不是字符串）。 */
internal const val GID_NZ_FUTURE_INT = 1471

/** 悦享卡。`hmc` 是黑曜卡，别混。 */
internal const val CARD_TYPE_MONTH = "month"

/**
 * 从 `MyCardList` 的成果里挑逆战未来的悦享卡。
 *
 * 先按 gid 精确匹配，匹配不到就退到"第一张月卡"——
 * 万一哪天 gid 不返回了，宁可认错一张卡也别整块变空白。
 */
internal fun XyMyCardListDto.pickYuexiangCard(): XyUserCardDto? {
    val months = myUserInfo.filter { it.cardType == CARD_TYPE_MONTH }
    return months.firstOrNull { it.gid == GID_NZ_FUTURE_INT } ?: months.firstOrNull()
}

internal fun XyMyCardListDto.yuexiangStatus(
    nowSec: Long,
    rewardText: String = "",
): XinyueCardStatus {
    val active = pickYuexiangCard()
    if (active != null) return active.toStatus(nowSec = nowSec, rewardText = rewardText)

    // 生效列表里没有：可能是压根没开过，也可能是过期被挪走了。这两种要分开说。
    val gone = expireUserInfo.firstOrNull { it.cardType == CARD_TYPE_MONTH }
        ?: return XinyueCardStatus()
    return gone.toStatus(nowSec = nowSec, rewardText = rewardText, forceExpired = true)
}

internal fun XyUserCardDto.toStatus(
    nowSec: Long,
    rewardText: String = "",
    forceExpired: Boolean = false,
): XinyueCardStatus {
    val base = month?.baseInfo
    val end = base?.endTime ?: 0
    return XinyueCardStatus(
        hasCard = true,
        // end_time 是 0 时（字段缺失）不判过期：那是"不知道"，不是"过期了"
        expired = forceExpired || (end > 0 && nowSec > end),
        canClaim = base?.giftStatus == 0,
        gotNum = base?.gotNum ?: 0,
        totalNum = base?.totalNum ?: 0,
        roleName = decodeRoleText(role?.roleName.orEmpty()),
        partitionName = decodeRoleText(role?.partitionName.orEmpty()),
        endDate = dateText(end),
        rewardText = rewardText,
    )
}

/** 领取响应 → 一句人能读的奖励文案。没有道具就退回礼包标题。 */
internal fun XyReceiveGiftDto.rewardText(): String {
    val items = giftInfo.flatMap { it.items }
        .map { item ->
            if (item.quantity > 0) "${item.name}×${item.quantity}" else item.name
        }
        .filter { it.isNotBlank() }
    if (items.isNotEmpty()) return items.joinToString("、")
    return giftInfo.map { it.title }.filter { it.isNotBlank() }.joinToString("、")
}

/**
 * 解心悦那两个 base64 的字段（`role_name` / `partition_name`）。
 *
 * 两个不规矩的地方，都在抓包里坐实了：
 * 1. **用的是 URL-safe 字母表**——角色名里出现 `-`（`5oiR5aW96I-c55qE5Za1`），
 *    按标准 base64 解会直接抛异常；
 * 2. **没有 padding**——`我好菜的喵` 是 15 字节，正好 20 个字符，不带 `=`。
 *    所以这里自己补齐，不能直接丢给解码器。
 *
 * 解不动就**原样返回**：服务端哪天改回明文，或者给了一段不是 base64 的东西，
 * 与其显示一片乱码不如显示原文。
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeRoleText(text: String): String {
    val raw = text.trim()
    if (raw.isEmpty()) return ""
    // 两种字母表都吃：`-`/`_` 换成 `+`/`/` 之后就是标准 base64
    val normalized = raw.replace('-', '+').replace('_', '/')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    val bytes = runCatching { Base64.decode(padded) }.getOrNull() ?: return raw
    val decoded = runCatching { bytes.decodeToString() }.getOrNull() ?: return raw
    if (decoded.isBlank()) return raw
    // U+FFFD 说明这段字节根本不是 UTF-8；控制字符说明解出来的多半是随机字节。
    // 两种情况都判定"这段不是 base64 编码的文本"，退回原文。
    if (decoded.any { it == '\uFFFD' || it.code < 0x20 }) return raw
    return decoded
}

/** epoch 秒 → `2026-08-06`。按北京时间切，和服务端口径一致。 */
private fun dateText(epochSec: Long): String {
    if (epochSec <= 0) return ""
    val key = serverDateKey(epochSec)
    return "${key.substring(0, 4)}-${key.substring(4, 6)}-${key.substring(6, 8)}"
}
