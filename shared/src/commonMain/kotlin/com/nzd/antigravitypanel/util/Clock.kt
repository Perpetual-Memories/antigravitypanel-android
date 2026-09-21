package com.nzd.antigravitypanel.util

/**
 * 当前时间（epoch 秒）。
 *
 * 所有"读时钟"的地方都必须走这里，不要直接 `System.currentTimeMillis()`：
 * 同步、保留期裁剪、筛选的时间窗都在 commonMain 里，集中到一个 expect 上，
 * 测试要固定时间时只改一处。
 */
expect fun currentEpochSeconds(): Long
