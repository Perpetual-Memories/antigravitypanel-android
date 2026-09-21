package com.nzd.antigravitypanel.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.domain.DateRange
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.MatchFilter
import com.nzd.antigravitypanel.domain.Outcome
import com.nzd.antigravitypanel.domain.availableMaps
import com.nzd.antigravitypanel.domain.difficultyOptionsForMode
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu

/** 模式下拉：机甲排位不计入统计口径，所以不列出来。 */
private val MODE_OPTIONS = listOf(GameMode.HUNT, GameMode.TOWER, GameMode.TIME_HUNT)

/**
 * 战绩筛选：顶栏右上角图标 + 级联下拉，五个维度（模式 / 地图 / 难度 / 日期 / 场次）
 * 各占一个子菜单 —— 和 HyperIsland 应用页右上角那个省略号菜单是同一套组件
 * （`OverlayIconCascadingDropdownMenu`），不再走 bottom sheet。
 *
 * 地图和难度的选项来自**数据本身和配置下发**，不是写死的：官方随时会加地图、调难度表。
 *
 * 两个实现上的注意点：
 * - 子菜单的展开状态是按**位置**（第几个 entry 的第几个 item）记的，不是按对象身份，
 *   所以每次重组重建 `DropdownItem` 没问题——但**顺序必须稳定**，别让表长在菜单开着的时候变。
 * - [collapseOnSelection] 给 false：五个维度常常要连着调，选完一个就收起来的话得反复点开。
 *   关掉靠点周围空白（`enableWindowDim` 默认开）。
 */
@Composable
fun HistoryFilterMenu(
    filter: MatchFilter,
    allMatches: List<MatchEntity>,
    config: GameConfigDto,
    onFilterChange: (MatchFilter) -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val mapOptions = remember(allMatches, config, filter.mode) {
        listOf(null to "全部地图") +
            availableMaps(allMatches, config, filter.mode).map { (id, name) -> id to name }
    }
    // 难度档位**按模式给**：塔防没有超限但有练习 / 新手关 / 训练场，追猎反过来。
    // 先选了模式再开难度菜单时，列表必须跟着变，否则会出现一个该模式下不存在的选项。
    val difficulties = remember(config, filter.mode) {
        difficultyOptionsForMode(filter.mode, config)
    }

    val entry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = "模式",
                summary = filter.mode?.label ?: "全部模式",
                children = buildList {
                    add(
                        DropdownItem(
                            text = "全部模式",
                            selected = filter.mode == null,
                            onClick = { onFilterChange(filter.copy(mode = null, mapId = null)) },
                        ),
                    )
                    MODE_OPTIONS.forEach { mode ->
                        add(
                            DropdownItem(
                                text = mode.label,
                                selected = filter.mode == mode,
                                // 换了模式之后旧的地图选择已经没意义，跟着重置
                                onClick = { onFilterChange(filter.copy(mode = mode, mapId = null)) },
                            ),
                        )
                    }
                },
            ),
            DropdownItem(
                text = "地图",
                summary = mapOptions.firstOrNull { it.first == filter.mapId }?.second ?: "全部地图",
                children = mapOptions.map { (id, name) ->
                    DropdownItem(
                        text = name,
                        selected = filter.mapId == id,
                        onClick = { onFilterChange(filter.copy(mapId = id)) },
                    )
                },
            ),
            DropdownItem(
                text = "难度",
                summary = filter.difficulty ?: "全部难度",
                children = buildList {
                    add(
                        DropdownItem(
                            text = "全部难度",
                            selected = filter.difficulty == null,
                            onClick = { onFilterChange(filter.copy(difficulty = null)) },
                        ),
                    )
                    difficulties.forEach { name ->
                        add(
                            DropdownItem(
                                text = name,
                                selected = filter.difficulty == name,
                                onClick = { onFilterChange(filter.copy(difficulty = name)) },
                            ),
                        )
                    }
                },
            ),
            DropdownItem(
                text = "日期",
                summary = filter.range.label,
                children = DateRange.entries.map { range ->
                    DropdownItem(
                        text = range.label,
                        selected = filter.range == range,
                        onClick = { onFilterChange(filter.copy(range = range)) },
                    )
                },
            ),
            DropdownItem(
                text = "场次",
                summary = filter.outcome.label,
                children = Outcome.entries.map { outcome ->
                    DropdownItem(
                        text = outcome.label,
                        selected = filter.outcome == outcome,
                        onClick = { onFilterChange(filter.copy(outcome = outcome)) },
                    )
                },
            ),
        ),
    )

    OverlayIconCascadingDropdownMenu(
        entry = entry,
        enabled = enabled,
        collapseOnSelection = false,
        content = content,
    )
}
