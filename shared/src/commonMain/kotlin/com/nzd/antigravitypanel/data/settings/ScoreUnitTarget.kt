package com.nzd.antigravitypanel.data.settings

/**
 * 「积分显示单位」管到哪几个页面。
 *
 * 积分（`iScore`）在三个地方出现，量级差得很远：概览的近五场场均、历史列表的每局、
 * 详情页的单局与分人。**每个页面各留一个开关**，因为它们的使用场景不一样：
 * 历史列表是"扫一眼谁高谁低"，六位数字挤在行尾很难比；
 * 详情页是"我要精确数字"，这时候 4.5万 反而丢了信息。
 *
 * ⚠️ 这是**多选**（集合），不是单选：用户完全可以只让历史战绩用万。
 */
enum class ScoreUnitTarget(val label: String) {
    /** 对局详情：数据总览里的积分、每张玩家卡上的积分。 */
    MATCH_DETAIL("对局详情"),

    /** 历史战绩列表每一行行尾的积分。 */
    HISTORY("历史战绩"),

    /** 概览页「近五场数据统计」里的场均积分。 */
    OVERVIEW_RECENT("概览页近五场"),
    ;

    companion object {
        /**
         * 默认值：历史战绩与概览近五场**用万**，详情页保留完整数字。
         *
         * 前两个是"扫读"场景，数字本来就只是比大小；详情页是用户主动点进来看的，
         * 那里该给完整数字（他多半是来核对某一局的）。
         */
        val DEFAULT: Set<ScoreUnitTarget> = setOf(HISTORY, OVERVIEW_RECENT)

        /** 存盘用的形式：`HISTORY,OVERVIEW_RECENT`。空集合存成空串。 */
        fun encode(targets: Set<ScoreUnitTarget>): String =
            ScoreUnitTarget.entries.filter { it in targets }.joinToString(",") { it.name }

        /**
         * 读盘：认不出来的名字直接丢掉（改名 / 删过枚举值之后老数据不该炸）。
         *
         * ⚠️ `stored == null` 和 `stored == ""` 必须分开：前者是"从没设置过" → 默认值，
         * 后者是"用户把所有勾都取消了" → 空集合。都当默认的话用户关不掉。
         */
        fun decode(stored: String?): Set<ScoreUnitTarget> {
            if (stored == null) return DEFAULT
            return stored.split(',')
                .mapNotNull { name -> entries.firstOrNull { it.name == name } }
                .toSet()
        }
    }
}
