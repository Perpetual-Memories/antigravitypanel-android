package com.nzd.antigravitypanel.ui.component

/**
 * 关于页需要的两个平台动作：打开外链、复制文本。
 *
 * 不做成通用工具是刻意的——只有关于页用到，泛化成"平台能力层"只会多一层没人用的抽象。
 */
expect fun openUrl(url: String)

/** 复制文本到剪贴板。QQ 群号这种东西用户是要去别处粘贴的，只能靠复制。 */
expect fun copyToClipboard(text: String, label: String)

/**
 * 弹一条短暂的提示。
 *
 * 用在"已经替你做了某件事，但那件事不值得弹窗"的场合：比如检查更新前把提取码
 * 复制好——复制本身没有 UI 反馈，不说一声用户不知道剪贴板里已经有东西了，
 * 可它又不至于重要到要用户点一下"知道了"。
 */
expect fun showToast(text: String)

/** 当前安装包版本名，用于关于页显示。 */
expect fun appVersionName(): String
