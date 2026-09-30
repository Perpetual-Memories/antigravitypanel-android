package com.nzd.antigravitypanel.ui.mapdist

import androidx.compose.runtime.Composable
import com.nzd.antigravitypanel.domain.MapSeason
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu

/**
 * 地图分布的「按赛季筛选」：顶栏右上角图标 + 下拉，**可多选**。
 *
 * 和历史战绩那个筛选菜单是同一个组件（`OverlayIconCascadingDropdownMenu`），
 * 区别只在它只有一层：赛季就五项，再套一级子菜单是白白多点一下。
 *
 * @param seasons 当前模式的赛季（猎场 / 塔防各一份，新赛季在前）
 * @param selected 勾了哪些 key。**null = 全选**（一次都没设过），
 *   进来第一次打开时五个都必须显示成勾着
 * @param onToggle 点一项就切该项的勾选，菜单**不收起**（常常要连着勾几下）
 */
@Composable
fun MapSeasonFilterMenu(
    seasons: List<MapSeason>,
    selected: Set<String>?,
    onToggle: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val selectedKeys = selected ?: seasons.mapTo(mutableSetOf()) { it.key }

    OverlayIconCascadingDropdownMenu(
        entry = DropdownEntry(
            items = seasons.map { season ->
                DropdownItem(
                    text = season.title,
                    selected = season.key in selectedKeys,
                    onClick = { onToggle(season.key) },
                )
            },
        ),
        // 五个赛季常常要连着调整，选一个就收起的话得反复点开。
        // 关掉靠点周围空白（enableWindowDim 默认开）。
        collapseOnSelection = false,
        content = content,
    )
}
