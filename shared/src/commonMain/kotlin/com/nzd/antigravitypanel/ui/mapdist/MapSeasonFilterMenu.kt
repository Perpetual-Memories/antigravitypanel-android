package com.nzd.antigravitypanel.ui.mapdist

import androidx.compose.runtime.Composable
import com.nzd.antigravitypanel.domain.MapSeason
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu

/**
 * 地图分布的筛选菜单：顶栏右上角图标 + 下拉。
 *
 * 两组，中间由组件自己插一条分割线（`OverlayIconCascadingDropdownMenu` 的 `entries`
 * 重载会在组与组之间画 `HorizontalDivider`，不用自己塞一条假 item ——
 * 假 item 既会被当成可点的行，也会被 `DropdownItem` 的选中态逻辑带上勾）：
 *
 * 1. **赛季**（猎场 / 塔防各一份，新赛季在前），可多选。
 * 2. **仅显示未毕业的地图**（掉落物没全部解锁的才摆出来），单选式开关。
 *
 * 为什么赛季不跟这个开关并成一列：一个是"看哪几季"，一个是"看哪些还没齐"，
 * 混在一起会让"全勾"和"这个开关开着"看起来是同一种状态。
 *
 * @param seasons 当前模式的赛季
 * @param selected 勾了哪些 key。**null = 全选**（一次都没设过），
 *   进来第一次打开时五个都必须显示成勾着
 * @param onToggle 点一项就切该项的勾选
 * @param hideCompleted 「仅显示未毕业的地图」当前是否开着
 * @param onToggleHideCompleted 点那一项时切开关
 */
@Composable
fun MapSeasonFilterMenu(
    seasons: List<MapSeason>,
    selected: Set<String>?,
    onToggle: (String) -> Unit,
    hideCompleted: Boolean,
    onToggleHideCompleted: () -> Unit,
    content: @Composable () -> Unit,
) {
    val selectedKeys = selected ?: seasons.mapTo(mutableSetOf()) { it.key }

    val entries = listOf(
        DropdownEntry(
            items = seasons.map { season ->
                DropdownItem(
                    text = season.title,
                    selected = season.key in selectedKeys,
                    onClick = { onToggle(season.key) },
                )
            },
        ),
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = "仅显示未毕业的地图",
                    selected = hideCompleted,
                    onClick = onToggleHideCompleted,
                ),
            ),
        ),
    )

    OverlayIconCascadingDropdownMenu(
        entries = entries,
        // 五个赛季常常要连着调整，选一个就收起的话得反复点开。
        // 关掉靠点周围空白（enableWindowDim 默认开）。
        collapseOnSelection = false,
        content = content,
    )
}
