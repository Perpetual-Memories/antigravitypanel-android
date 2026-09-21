package com.nzd.antigravitypanel.ui.component

/**
 * 关于页需要的两个平台动作：打开外链、复制文本。
 *
 * 不做成通用工具是刻意的——只有关于页用到，泛化成"平台能力层"只会多一层没人用的抽象。
 */
expect fun openUrl(url: String)

/** 复制文本到剪贴板。QQ 群号这种东西用户是要去别处粘贴的，只能靠复制。 */
expect fun copyToClipboard(text: String, label: String)

/** 当前安装包版本名，用于关于页显示。 */
expect fun appVersionName(): String
