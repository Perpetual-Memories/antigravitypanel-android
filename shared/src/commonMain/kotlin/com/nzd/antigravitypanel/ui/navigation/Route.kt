package com.nzd.antigravitypanel.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation3 路由键。
 *
 * **只剩一级页**。二级详情页（活动日历全量列表 / 战绩详情 / 关于）不走路由——
 * 它们照 HyperIsland 的做法是一级页之上的一层图层（`PredictiveNavLayer`），
 * 由「哪个页 + 是否可见」两个 state 驱动，见 `AppContent` 的 `AppDetail`。
 * 原因是返回手势要能带着页面跟手位移、背景同步缩放虚化，这些跨页面的合成上下文
 * 路由框架给不了；而且两套并存会抢同一个返回键。
 */
@Serializable
sealed interface Route : NavKey {

    /** 主页面：底栏四个 Tab（概览 / 历史战绩 / 地图分布 / 应用设置） */
    @Serializable
    data object Main : Route
}
