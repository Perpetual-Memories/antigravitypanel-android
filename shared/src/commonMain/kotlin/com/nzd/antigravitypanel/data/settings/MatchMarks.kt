package com.nzd.antigravitypanel.data.settings

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 对局的置顶 / 收藏标记。
 *
 * 没有给 `matches` 表加两列：这两个标记是**纯本地的 UI 偏好**，
 * 而 matches 表每行都是服务端下发的对局快照——往里塞本地状态会让"这条记录和服务端
 * 一致吗"这个问题变得没法回答。用一个逗号分隔的字符串存 id 集合足够，量级也就几十个。
 */
class MatchMarks(
    private val store: KeyValueStore,
) {
    private val _pinned = MutableStateFlow<Set<String>>(emptySet())
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    private val _favorite = MutableStateFlow<Set<String>>(emptySet())
    val favorite: StateFlow<Set<String>> = _favorite.asStateFlow()

    suspend fun restore() {
        _pinned.value = parse(store.read(StoreKey.PINNED_ROOMS))
        _favorite.value = parse(store.read(StoreKey.FAVORITE_ROOMS))
    }

    suspend fun togglePin(roomId: String) {
        _pinned.value = toggle(_pinned.value, roomId).also {
            store.write(StoreKey.PINNED_ROOMS, join(it))
        }
    }

    suspend fun toggleFavorite(roomId: String) {
        _favorite.value = toggle(_favorite.value, roomId).also {
            store.write(StoreKey.FAVORITE_ROOMS, join(it))
        }
    }

    private fun toggle(current: Set<String>, roomId: String): Set<String> =
        if (roomId in current) current - roomId else current + roomId

    private fun parse(raw: String?): Set<String> =
        raw?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    private fun join(values: Set<String>): String = values.joinToString(",")
}
