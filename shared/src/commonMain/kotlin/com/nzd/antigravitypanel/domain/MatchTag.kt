package com.nzd.antigravitypanel.domain

/**
 * 历史战绩行上的标签。
 *
 * 只有"自己"这一侧的数据（列表接口给的那些字段）算不出它们 —— 两个判据都要看
 * 队友的 Boss 伤害，而它只存在于逐局详情接口里，所以能不能显示取决于
 * `data.db.MatchBossStatEntity` 有没有这一局的缓存。
 */
enum class MatchTag(val label: String) {
    /** 实力局：Boss 伤害占全队 40% 以上。 */
    STRONG("实力局"),

    /** 带飞局：Boss 伤害是第二名的两倍。 */
    CARRY("带飞局"),
}

/**
 * 标签判定。
 *
 * 两条规则是用户定的，**先后顺序也是**：
 * - 带飞局：自己的 Boss 伤害 ≥ 第二名的两倍
 * - 实力局：自己的 Boss 伤害占全队 ≥ 40%
 * - 两条都中只显示带飞局（带飞局是更强的那个结论，两个角标并排反而看不出哪个是真的）
 *
 * 两个边界要说清楚：
 * - 第二名必须**真的存在且有伤害**（[rivalBossDamage] > 0）。单刷局、或者队友全是 0 时，
 *   `自己 ≥ 0 × 2` 恒真，不判这一条会满屏"带飞局"。
 * - 分母为 0（没有 Boss 口径的模式）一律不给标签，宁可不显示也别编一个结论。
 *
 * 占比用整数乘法比较（`self * 100 >= team * 40`），不走浮点 —— 浮点在边界上会出现
 * "算出 39.999% 于是没标签"这种说不清的情况。
 */
fun matchTagOf(
    selfBossDamage: Long,
    teamBossDamage: Long,
    rivalBossDamage: Long,
): MatchTag? {
    if (selfBossDamage <= 0) return null
    if (rivalBossDamage > 0 && selfBossDamage >= rivalBossDamage * 2) return MatchTag.CARRY
    if (teamBossDamage > 0 && selfBossDamage * 100 >= teamBossDamage * 40) return MatchTag.STRONG
    return null
}
