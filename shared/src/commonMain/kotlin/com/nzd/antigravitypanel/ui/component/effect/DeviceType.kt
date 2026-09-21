package com.nzd.antigravitypanel.ui.component.effect

/**
 * 设备档位。miuix 示例在宽屏（分栏）时换一套背景参数，我们只有直板一种形态，
 * 所以这个枚举目前恒为 [PHONE] —— 保留它是为了和上游的 `BgEffectConfig` 对得上。
 */
internal enum class DeviceType {
    PHONE,
    PAD,
}
