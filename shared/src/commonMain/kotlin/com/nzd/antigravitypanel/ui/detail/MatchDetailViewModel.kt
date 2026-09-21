package com.nzd.antigravitypanel.ui.detail

import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.ui.home.describeSyncError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MatchDetailUiState(
    val loading: Boolean = true,
    val match: MatchEntity? = null,
    val detail: GameDetailDto? = null,
    /**
     * 同地图 / 同难度的上一局，用来给「数据总览」做更快 / 更慢的对比。
     * 查不到（这是该图该难度的第一局）就是 null，UI 那边就不显示对比。
     */
    val previous: MatchEntity? = null,
    val error: String? = null,
)

/**
 * 对局详情。
 *
 * 详情接口**每次都现拉**：单局的 Boss 伤害 / 配装只在这里有，本地库里存的是列表接口的快照，
 * 缓存下来会一直停在开完那一秒的样子。
 */
class MatchDetailViewModel(
    private val api: NzApi,
    private val dao: MatchDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(MatchDetailUiState())
    val state: StateFlow<MatchDetailUiState> = _state.asStateFlow()

    fun load(roomId: String) {
        scope.launch {
            _state.value = MatchDetailUiState(loading = true)
            val match = dao.find(roomId)
            val previous = match?.let { dao.previousOf(it.mapId, it.subModeType, it.eventTimeSec) }
            try {
                val detail = api.gameDetail(roomId)
                _state.value = MatchDetailUiState(
                    loading = false,
                    match = match,
                    detail = detail,
                    previous = previous,
                )
            } catch (e: Throwable) {
                _state.value = MatchDetailUiState(
                    loading = false,
                    match = match,
                    error = describeSyncError(e),
                )
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
