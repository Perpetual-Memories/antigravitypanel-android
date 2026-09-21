package com.nzd.antigravitypanel.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModel
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统计大盘（P0 骨架，只负责内容区）。
 *
 * 用 Vico 3 的饼图验证图表链路：Vico 编于 CMP 1.11.0、Miuix 依赖 CMP 1.11.1，
 * 同属 1.11.x，Gradle 统一解析到 1.11.1。真数据由 domain/stats 聚合后接入（P2/P4）。
 */
@Composable
fun StatsScreen() {
    val sliceColors = listOf(
        MiuixTheme.colorScheme.primary,
        MiuixTheme.colorScheme.secondary,
        MiuixTheme.colorScheme.tertiaryContainer,
        MiuixTheme.colorScheme.surfaceContainerHigh,
    )

    val chart = rememberPieChart(
        sliceProvider = PieChart.SliceProvider.series(
            sliceColors.map { color -> PieChart.Slice(fill = Fill(color)) },
        ),
        spacing = 2.dp,
        innerSize = PieSize.Inner.fixed(64.dp),
    )

    // 占位数据：模式分布
    val model = remember { PieChartModel.build(12f, 7f, 4f, 2f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "模式分布",
            color = MiuixTheme.colorScheme.onSurface,
        )
        PieChartHost(
            chart = chart,
            model = model,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(vertical = 8.dp),
        )
    }
}
