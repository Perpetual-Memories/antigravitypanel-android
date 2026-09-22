package com.nzd.antigravitypanel.data.config

import com.nzd.antigravitypanel.data.remote.IdeChart
import kotlinx.serialization.Serializable

/**
 * 协议参数。这些值官方每次升级都可能变，所以一律不硬编码在调用处。
 *
 * 已经变过一次的就是 Referer 里的 page-frame 版本：早期源码是 `/13/`，
 * 2026-09 实测已经是 `/51/`。写死必然失效，这也是整个 config 机制存在的原因。
 */
@Serializable
data class RemoteConfig(
    /**
     * 当前赛季号。**每次赛季更新都要跟着改**（S3 = 3、S4 = 4）。
     *
     * 只靠它还不够：新赛季刚开的那几天，玩家在当赛季可能一场都没打，
     * 服务端按 `seasonID` 查会返回**空数组**，于是整页数字变 0。
     * 官方前端的做法是顺着 [fallbackSeasonIDs] 往回退（它自己写的是 `Us = [4, 3]`），
     * 退到有数据的那一个赛季为止 —— 见 [com.nzd.antigravitypanel.data.remote.NzApi]。
     */
    val seasonID: Int = 4,
    /** 当前赛季没战绩时依次回退的赛季号，`seasonID` 本身不用重复写进来。 */
    val fallbackSeasonIDs: List<Int> = listOf(3),
    /** 战绩那组活动的 iChartId。福利站（签到）是另一组，见 [welfareIChartId]。 */
    val iChartId: String = "430662",
    val sIdeToken: String = "NoOapI",
    /**
     * 福利站（签到）那组活动的 iChartId / sIdeToken。
     *
     * 2026-09-21 抓小程序签到页拿到的真实值。它和战绩那组是**两套活动**，
     * 混用会直接被服务端拒掉，所以必须分开下发而不是复用 [iChartId]。
     */
    val welfareIChartId: String = "541709",
    val welfareSIdeToken: String = "VAs3zJ",
    /** Referer 里 `/51/page-frame.html` 的那个 51。 */
    val pageFrameVersion: Int = 51,
    val miniProgramAppId: String = "wx4e8cbe4fb0eca54c",
    val userAgent: String = DEFAULT_USER_AGENT,
    /**
     * 远程覆盖地址（GitHub raw）。留空表示只用安装包内置的那份，不发请求。
     * 建好独立配置仓库后填进来即可，失败会自动退回内置值。
     */
    val configUrl: String = "",
    /** 每页条数，实测服务端按 limit=10 返回。 */
    val pageSize: Int = 10,
    /**
     * 最多翻多少页。服务端只保留滚动窗口内的对局，实测 page=11 返回空数组，
     * 也就是封顶 100 场。翻到空页会提前停，这里只是兜底防止死循环。
     */
    val maxPages: Int = 10,
) {
    val referer: String
        get() = "https://servicewechat.com/$miniProgramAppId/$pageFrameVersion/page-frame.html"

    /** 按活动组取 iChartId。表单里的 iChartId 与 iSubChartId 用的是同一个值。 */
    fun iChartIdOf(chart: IdeChart): String = when (chart) {
        IdeChart.Main -> iChartId
        IdeChart.Welfare -> welfareIChartId
    }

    fun sIdeTokenOf(chart: IdeChart): String = when (chart) {
        IdeChart.Main -> sIdeToken
        IdeChart.Welfare -> welfareSIdeToken
    }
}

/**
 * 内置兜底配置。与抓包实测一致，改动前先确认接口现状。
 *
 * 之所以写成常量而不是塞进 assets：这份配置一共就十几行，走一次资源读取
 * （还要处理 CMP 资源 API 的版本差异）不划算；而且它要被远程 JSON 逐字段覆盖，
 * 放在代码里做合并更直观。
 */
internal const val BUILTIN_CONFIG_JSON = """
{
  "seasonID": 4,
  "fallbackSeasonIDs": [3],
  "iChartId": "430662",
  "sIdeToken": "NoOapI",
  "welfareIChartId": "541709",
  "welfareSIdeToken": "VAs3zJ",
  "pageFrameVersion": 51,
  "miniProgramAppId": "wx4e8cbe4fb0eca54c",
  "userAgent": "Mozilla/5.0 (Linux; Android 16; Pixel 8 Build/BP2A.250605.031.A3; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/146.0.0.0 XWEB/1460249 MMWEBSDK/20260502 MicroMessenger/8.0.72.3085(0x28004845) WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64 MiniProgramEnv/android",
  "configUrl": "",
  "pageSize": 10,
  "maxPages": 10
}
"""

private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 16; Pixel 8 Build/BP2A.250605.031.A3; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/146.0.0.0 " +
        "XWEB/1460249 MMWEBSDK/20260502 MicroMessenger/8.0.72.3085(0x28004845) " +
        "WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64 MiniProgramEnv/android"
