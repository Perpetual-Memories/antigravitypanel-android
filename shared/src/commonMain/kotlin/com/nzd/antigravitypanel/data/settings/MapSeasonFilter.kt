package com.nzd.antigravitypanel.data.settings

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.seasonOrderOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 地图分布页的「按赛季筛选」勾选。猎场和塔防**各存一份**（用户要求分别筛）。
 *
 * ## null 和空集合是两件事
 *
 * - **null = 一次都没设过**（第一次用）→ 调用方按**全选**处理
 * - **空集合 = 用户一格都没勾** → 就真的一张卡都不该显示
 *
 * 混成一谈的话，用户全勾上时存下来是 `S0,S1,S2,S3,S4`、全取消是 `""`，
 * 而"从没打开过筛选"也必须落在"全选"上 —— 只有 null 能表达它。
 *
 * 存成逗号分隔的字符串而不是给 `matches` 加列：这是纯 UI 偏好，和对局数据无关。
 */
class MapSeasonFilter(
    private val store: KeyValueStore,
) {
    private val _selected = MutableStateFlow<Map<GameMode, Set<String>>>(emptyMap())
    val selected: StateFlow<Map<GameMode, Set<String>>> = _selected.asStateFlow()

    suspend fun restore() {
        _selected.value = buildMap {
            for (mode in PERSISTED_MODES) {
                read(keyOf(mode))?.let { put(mode, it) }
            }
        }
    }

    /**
     * 这个模式勾了哪些赛季的 key。**没设过返回 null**（= 全选）。
     *
     * 不支持赛季筛选的模式（时空追猎）一律返回 null —— 那边本来就没有赛季，
     * 返回 null 让调用方走"全部显示"那条路。
     */
    fun selectionOf(mode: GameMode): Set<String>? = _selected.value[mode]

    /**
     * 勾 / 取消一个赛季。
     *
     * 第一次点（还没存过）时基准是**全选**：用户看到的明明是五个都勾着，
     * 点一下"S4"却变成"只剩 S4"会非常莫名其妙。
     */
    suspend fun toggle(mode: GameMode, key: String) {
        val all = seasonOrderOf(mode).mapTo(mutableSetOf()) { it.key }
        val current = _selected.value[mode] ?: all
        val next = if (key in current) current - key else current + key
        _selected.value = _selected.value + (mode to next)
        store.write(keyOf(mode), next.sorted().joinToString(","))
    }

    private suspend fun read(key: String): Set<String>? = store.read(key)?.let { raw ->
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun keyOf(mode: GameMode): String = when (mode) {
        GameMode.HUNT -> StoreKey.MAP_SEASON_FILTER_HUNT
        else -> StoreKey.MAP_SEASON_FILTER_TOWER
    }

    companion object {
        /** 只有这两个模式有赛季表，也就只需要存这两份。 */
        val PERSISTED_MODES: List<GameMode> = listOf(GameMode.HUNT, GameMode.TOWER)
    }
}
