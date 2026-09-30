package com.nzd.antigravitypanel

/**
 * 福利站「积分余额 / 任务中心」的测试夹具。2026-09-29 抓小程序福利站。
 *
 * 单独一个文件而不塞进 HarFixtures.kt：那份已经 80 KB，再往里加长行
 * 会让整个文件没法读。这里只留解析要用到的字段（图片 URL、礼包明细、
 * 兑换上下限这些一律剔掉），结构保持原样。
 */
object WelfareFixtures {

    const val RESPONSE_SCORE_REDEEM_LIST = """{"ret":0,"iRet":0,"sMsg":"succ","jData":{"welfareStationData":{"code":0,"data":{"actID":3472,"tasks":[{"redeemID":219921,"affiliatedGroupID":0,"status":10,"giftName":"复活币*1","gift":[{"pkgID":"8030573","id":"45206010001","name":"复活币","num":"1","type":"1","uinType":"uin"}],"scoreList":[{"jfID":"234-A100406","jfName":"","totalScore":7800,"needScore":500}],"limit":{"mode":"total","myDayLimit":0,"myWeekLimit":0,"myMonthLimit":5,"myTotalLimit":60,"myDayRedeemed":0,"myWeekRedeemed":0,"myMonthRedeemed":0,"myTotalRedeemed":5}}],"milestone":{"totalAmount":0,"currentAmount":0,"items":null},"jfSummary":[{"jfID":"234-A100406","jfName":"","totalScore":7800,"value":7800}]},"message":""}},"sAmsSerial":"AMS-NZM-0929221912-ZyrSSk-924046-620697"}"""

    const val RESPONSE_TASK_LABEL = """{"ret":0,"iRet":0,"sMsg":"succ","jData":{"welfareStationData":{"code":0,"data":{"labelid":9983,"taskgroups":{"labelid":9983,"labelgrouptasks":[{"groupid":13299,"groupinfo":{"type":"0","typeinfo":"default task group","name":"任务中心","startdate":"2026-05-09 00:00:00","enddate":"2032-05-09 00:00:00","iconurl":""},"grouptasks":[{"taskid":65926,"taskinfo":{"type":"0","name":"订阅小程序","sort":2,"score":0},"taskdata":{"start":"-","end":"-","target":1,"progress":1,"isfinished":true,"isawarded":true,"ext":{"period":"long"},"status":1}},{"taskid":65928,"taskinfo":{"type":"0","name":"每日完成1局","sort":4,"score":0},"taskdata":{"start":"260929","end":"260929","target":1,"progress":1,"isfinished":true,"isawarded":false,"ext":{"period":"day"},"status":1}},{"taskid":65929,"taskinfo":{"type":"0","name":"每周对局5次","sort":5,"score":0},"taskdata":{"start":"260928","end":"261004","target":5,"progress":5,"isfinished":true,"isawarded":false,"ext":{"period":"week"},"status":1}}]},{"groupid":13331,"groupinfo":{"type":"0","typeinfo":"default task group","name":"累登任务","startdate":"2026-05-09 00:00:00","enddate":"2032-05-09 00:00:00","iconurl":""},"grouptasks":[{"taskid":66087,"taskinfo":{"type":"0","name":"累登3天领取","sort":1,"score":0},"taskdata":{"start":"260901","end":"260930","target":3,"progress":3,"isfinished":true,"isawarded":true,"ext":{"period":"month"},"status":1}},{"taskid":66088,"taskinfo":{"type":"0","name":"累登5天领取","sort":2,"score":0},"taskdata":{"start":"260901","end":"260930","target":5,"progress":5,"isfinished":true,"isawarded":true,"ext":{"period":"month"},"status":1}}]}],"labelgroupdata":{"redpoint":3,"tasknum":10,"finishedtasknum":9,"awardedtasknum":6}}},"message":""}},"sAmsSerial":"AMS-NZM-0929221912-ZyrSSk-924046-620697"}"""

    const val RESPONSE_TASK_REWARD = """{"ret":0,"iRet":0,"sMsg":"succ","jData":{"welfareStationData":{"code":0,"data":{"status":0,"msg":"success","res":[{"Ret":0,"H":{"amsmsg":"恭喜您获得了礼包： 500积分 ","amspackagegroupid":"4629892","amspackageid":"8036816","amspackagename":"500积分","amspackagenum":"1","amsret":"0","amsserial":"AMS-nzm-0929221917-4IDM9N-923738-68126","msg":"ams send success","taskID":"65928","taskid":"65928","taskname":"每日完成1局"}}]},"message":""}},"sAmsSerial":"AMS-NZM-0929221917-vZqa3d-924046-620697"}"""
}
