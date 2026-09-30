package com.nzd.antigravitypanel.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nzd.antigravitypanel.ui.settings.SettingsItemMargin
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 二级页里「小程序活动中心签到」那张卡点开后的配置弹层。
 *
 * 和 QQ / 心悦那两块（[CredentialSheet]）不一样：**这里没有凭证要填**——
 * 小程序这套用的是 app 自己的登录凭证，在账号详情那张卡里配。
 * 所以这一层只剩两个开关，版式照它：纵向 14dp 间距、只补底部间距。
 *
 * 两个开关都是**默认开**的（见 `UserSettings`）：签到不消耗任何东西，
 * 任务中心的领取接口一次只领传进去的那一个任务，两者都等价于"每天顺手做掉"。
 */
@Composable
fun WelfareAutoSheet(
    show: Boolean,
    autoSign: Boolean,
    autoClaimTask: Boolean,
    onAutoSignChange: (Boolean) -> Unit,
    onAutoClaimTaskChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "小程序活动中心签到",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            ) {
                Column {
                    SwitchPreference(
                        checked = autoSign,
                        onCheckedChange = onAutoSignChange,
                        title = "打开APP自动签到",
                        summary = "每次打开 app 时，当天还没签到就自动签一次",
                        insideMargin = SettingsItemMargin,
                    )
                    SwitchPreference(
                        checked = autoClaimTask,
                        onCheckedChange = onAutoClaimTaskChange,
                        title = "打开APP自动领取任务中心奖励",
                        summary = "自动领取「每日完成1局」和「每周对局5次」已达成但还没领的积分",
                        insideMargin = SettingsItemMargin,
                    )
                }
            }

            HintCard(
                text = "任务中心的奖励要自己点一下才会发到手，达成后放着不领不会补发。" +
                    "自动领取只在服务端说「已完成且未领取」时才发请求，不会替你做任务。",
            )

            RiskNotice()
        }
    }
}
