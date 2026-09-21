package com.nzd.antigravitypanel.data.imports

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "有没有导入过 JSON"这个状态。
 *
 * 单独一个小类而不是塞进 [com.nzd.antigravitypanel.data.settings.UserSettings]：
 * 它不是用户能选的设置项，是导入动作留下的痕迹，而且概览页和设置页都要读它。
 *
 * 存的是摘要文案（形如「已导入 2226 场」），删掉它等于"清除导入痕迹"——
 * 概览页的凭证卡会退回「未输入」，不用再维护一个同步的布尔量。
 */
class JsonImportState(
    private val store: KeyValueStore,
) {
    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    private val _imported = MutableStateFlow(false)
    val imported: StateFlow<Boolean> = _imported.asStateFlow()

    suspend fun restore() {
        val saved = store.read(StoreKey.JSON_IMPORT_SUMMARY)?.takeIf { it.isNotBlank() }
        _summary.value = saved
        _imported.value = saved != null
    }

    /** 记一次成功的导入。 */
    suspend fun mark(summary: String) {
        store.write(StoreKey.JSON_IMPORT_SUMMARY, summary)
        _summary.value = summary
        _imported.value = true
    }

    /** 清除导入痕迹。清空本地数据时一起调，否则凭证卡会一直显示"已导入"。 */
    suspend fun clear() {
        store.remove(StoreKey.JSON_IMPORT_SUMMARY)
        _summary.value = null
        _imported.value = false
    }
}
