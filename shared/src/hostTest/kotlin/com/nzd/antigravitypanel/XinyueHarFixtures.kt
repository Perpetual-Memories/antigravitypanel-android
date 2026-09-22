package com.nzd.antigravitypanel

/**
 * 心悦俱乐部（`agw.xinyue.qq.com`）的抓包夹具。2026-07-29 抓心悦 App 悦享卡页拿到。
 *
 * 单独一个文件、不塞进 `HarFixtures.kt`，理由见那份文件的说明：
 * 那份已经 80 KB，再往里加几 KB 的三引号长行，Kotlin 解析器会从字符串中间
 * 某处开始报 `Unexpected tokens`，报错位置和真正原因对不上。
 *
 * 已剔除：卡片展示配置（`data.cards` 那棵大树，含大段 HTML 活动规则）、
 * 图片 URL、黑曜卡的道具清单。留下的全是判定逻辑真正要读的字段。
 */

/**
 * MyCardList：领取**之前**。gift_status=0（今天可领），gift_got_num=15。
 */
internal const val RESPONSE_MY_CARD_LIST = """{"ret":0,"msg":"","data":{"my_user_info":[{"card_type":"month","card_id":"yxk188","card_group":"yxk188","gid":1471,"role":{"game_open_id":"EAAA14DF8334D3511A38FCEC7AB9A146","game_app_id":"1110484610","area_id":1,"plat_id":1,"partition_id":1,"partition_name":"6buY6K6k5pyN5Yqh5Zmo","role_id":"419175704459784","role_name":"5oiR5aW96I-c55qE5Za1","device":"android","flag":0},"record_id":"11737452","page_common_info":{"pay_text":"\u9886\u53d6","pay_desc":"","status":0,"unpay_text":"","unpay_jump_url":"","color":0,"is_ios_use_coupon":false},"month":{"base_info":{"card_status":101,"start_time":1783519924,"end_time":1786031999,"gift_status":0,"gift_got_num":15,"gift_toget_num":8,"gift_total_num":30,"x_day_card_id":"11737452","gift_type":["reopen","daily"]},"h5_auto_pay_info":{"entrance_status":0,"button_status":0,"expire_time":"0","desc":"","check_tips_title":"","check_tips_desc":""},"pre_sell_info":{"entrance_status":1,"tips_for_order":"","id":0,"effective_time":"1786032000","msg":"\u60a8\u7684\u60a6\u4eab\u5361\u6743\u76ca\u5373\u5c068\u5929\u540e\u8fc7\u671f"}},"hmc":null,"once":null,"cp_week":null,"group_buy":null,"red_packet":null,"premium":null,"game_month":null,"game_time":null,"game_week":null},{"card_type":"hmc","card_id":"1411","card_group":"","gid":1471,"role":{"game_open_id":"EAAA14DF8334D3511A38FCEC7AB9A146","game_app_id":"1110484610","area_id":1,"plat_id":1,"partition_id":1,"partition_name":"6buY6K6k5pyN5Yqh5Zmo","role_id":"419175704459784","role_name":"5oiR5aW96I-c55qE5Za1","device":"android","flag":0},"record_id":"","page_common_info":{"pay_text":"\u5df2\u8fbe\u8d2d\u4e70\u4e0a\u9650","pay_desc":"\u5df2\u8fbe\u8d2d\u4e70\u4e0a\u9650","status":3,"unpay_text":"","unpay_jump_url":"","color":0,"is_ios_use_coupon":false},"month":null,"hmc":null,"once":null,"cp_week":null,"group_buy":null,"red_packet":null,"premium":null,"game_month":null,"game_time":null,"game_week":null}],"expire_user_info":[]}}"""

/**
 * MyCardList：领取**之后**。gift_status=1（今天已领），gift_got_num=16。
 *  *
 *  * 这两个数字是同一天前后两次抓包对出来的，所以 gift_status 就是「今天领没领」。
 */
internal const val RESPONSE_MY_CARD_LIST_CLAIMED = """{"ret":0,"msg":"","data":{"my_user_info":[{"card_type":"month","card_id":"yxk188","card_group":"yxk188","gid":1471,"role":{"game_open_id":"EAAA14DF8334D3511A38FCEC7AB9A146","game_app_id":"1110484610","area_id":1,"plat_id":1,"partition_id":1,"partition_name":"6buY6K6k5pyN5Yqh5Zmo","role_id":"419175704459784","role_name":"5oiR5aW96I-c55qE5Za1","device":"android","flag":0},"record_id":"11737452","page_common_info":{"pay_text":"\u9886\u53d6","pay_desc":"","status":0,"unpay_text":"","unpay_jump_url":"","color":0,"is_ios_use_coupon":false},"month":{"base_info":{"card_status":101,"start_time":1783519924,"end_time":1786031999,"gift_status":1,"gift_got_num":16,"gift_toget_num":8,"gift_total_num":30,"x_day_card_id":"11737452","gift_type":["reopen","daily"]},"h5_auto_pay_info":{"entrance_status":0,"button_status":0,"expire_time":"0","desc":"","check_tips_title":"","check_tips_desc":""},"pre_sell_info":{"entrance_status":1,"tips_for_order":"","id":0,"effective_time":"1786032000","msg":"\u60a8\u7684\u60a6\u4eab\u5361\u6743\u76ca\u5373\u5c068\u5929\u540e\u8fc7\u671f"}},"hmc":null,"once":null,"cp_week":null,"group_buy":null,"red_packet":null,"premium":null,"game_month":null,"game_time":null,"game_week":null},{"card_type":"hmc","card_id":"1411","card_group":"","gid":1471,"role":{"game_open_id":"EAAA14DF8334D3511A38FCEC7AB9A146","game_app_id":"1110484610","area_id":1,"plat_id":1,"partition_id":1,"partition_name":"6buY6K6k5pyN5Yqh5Zmo","role_id":"419175704459784","role_name":"5oiR5aW96I-c55qE5Za1","device":"android","flag":0},"record_id":"","page_common_info":{"pay_text":"\u5df2\u8fbe\u8d2d\u4e70\u4e0a\u9650","pay_desc":"\u5df2\u8fbe\u8d2d\u4e70\u4e0a\u9650","status":3,"unpay_text":"","unpay_jump_url":"","color":0,"is_ios_use_coupon":false},"month":null,"hmc":null,"once":null,"cp_week":null,"group_buy":null,"red_packet":null,"premium":null,"game_month":null,"game_time":null,"game_week":null}],"expire_user_info":[]}}"""

/**
 * MyCardList：账号下**没有**悦享卡（只有黑曜卡）。这不是错误，是没开通。
 */
internal const val RESPONSE_MY_CARD_LIST_NO_CARD = """{"ret":0,"msg":"","data":{"my_user_info":[{"card_type":"hmc","card_id":"1411","card_group":"","gid":1471,"role":{"game_open_id":"EAAA14DF8334D3511A38FCEC7AB9A146","game_app_id":"1110484610","area_id":1,"plat_id":1,"partition_id":1,"partition_name":"6buY6K6k5pyN5Yqh5Zmo","role_id":"419175704459784","role_name":"5oiR5aW96I-c55qE5Za1","device":"android","flag":0},"record_id":"","page_common_info":{"pay_text":"\u5df2\u8fbe\u8d2d\u4e70\u4e0a\u9650","pay_desc":"\u5df2\u8fbe\u8d2d\u4e70\u4e0a\u9650","status":3,"unpay_text":"","unpay_jump_url":"","color":0,"is_ios_use_coupon":false},"month":null,"hmc":null,"once":null,"cp_week":null,"group_buy":null,"red_packet":null,"premium":null,"game_month":null,"game_time":null,"game_week":null}],"expire_user_info":[]}}"""

/**
 * MyCardList：悦享卡已经过期，服务端把它挪进 `expire_user_info`。
 *  *
 *  * 抓的那张卡 2026-08-06 就到期了，所以「过期」是**常态**而不是异常，
 *  * 必须当成一等状态处理。
 */
internal const val RESPONSE_MY_CARD_LIST_EXPIRED = """{"ret":0,"msg":"","data":{"my_user_info":[{"card_type":"hmc","card_id":"1411","card_group":"","gid":1471,"role":{"game_open_id":"EAAA14DF8334D3511A38FCEC7AB9A146","game_app_id":"1110484610","area_id":1,"plat_id":1,"partition_id":1,"partition_name":"6buY6K6k5pyN5Yqh5Zmo","role_id":"419175704459784","role_name":"5oiR5aW96I-c55qE5Za1","device":"android","flag":0},"record_id":"","page_common_info":{"pay_text":"\u5df2\u8fbe\u8d2d\u4e70\u4e0a\u9650","pay_desc":"\u5df2\u8fbe\u8d2d\u4e70\u4e0a\u9650","status":3,"unpay_text":"","unpay_jump_url":"","color":0,"is_ios_use_coupon":false},"month":null,"hmc":null,"once":null,"cp_week":null,"group_buy":null,"red_packet":null,"premium":null,"game_month":null,"game_time":null,"game_week":null}],"expire_user_info":[{"card_type":"month","card_id":"yxk188","card_group":"yxk188","gid":1471,"role":{"game_open_id":"EAAA14DF8334D3511A38FCEC7AB9A146","game_app_id":"1110484610","area_id":1,"plat_id":1,"partition_id":1,"partition_name":"6buY6K6k5pyN5Yqh5Zmo","role_id":"419175704459784","role_name":"5oiR5aW96I-c55qE5Za1","device":"android","flag":0},"record_id":"11737452","page_common_info":{"pay_text":"\u9886\u53d6","pay_desc":"","status":0,"unpay_text":"","unpay_jump_url":"","color":0,"is_ios_use_coupon":false},"month":{"base_info":{"card_status":102,"start_time":1783519924,"end_time":1786031999,"gift_status":0,"gift_got_num":15,"gift_toget_num":8,"gift_total_num":30,"x_day_card_id":"11737452","gift_type":["reopen","daily"]},"h5_auto_pay_info":{"entrance_status":0,"button_status":0,"expire_time":"0","desc":"","check_tips_title":"","check_tips_desc":""},"pre_sell_info":{"entrance_status":1,"tips_for_order":"","id":0,"effective_time":"1786032000","msg":"\u60a8\u7684\u60a6\u4eab\u5361\u6743\u76ca\u5373\u5c068\u5929\u540e\u8fc7\u671f"}},"hmc":null,"once":null,"cp_week":null,"group_buy":null,"red_packet":null,"premium":null,"game_month":null,"game_time":null,"game_week":null}]}}"""

/**
 * ReceiveGift：返回的是这次真的发出去的东西（NZ点 x200）。
 *  *
 *  * ⚠️ 奖励内容**只在领取响应里有**，MyCardList 查不到今天发什么。
 */
internal const val RESPONSE_RECEIVE_GIFT = """{"ret":0,"msg":"","data":{"pop_info":null,"gift_info":[{"type":"daily","title":"\u6bcf\u65e5\u793c\u5305","group_id":["130027"],"sub_group_id":["8209809"],"items":[{"name":"NZ\u70b9","quantity":200,"unit_price":0,"image_url":"https://static.svip.game.qq.com/c64a1c14589a6965928f8df6f3b3704d.png","image_width":0,"image_height":0,"attached":{"border":"","discount":"","extend":"","gift_android_appid":"","gift_android_id":"","gift_appid":"","gift_common_appid":"","gift_common_id":"","gift_id":"","gift_ios_appid":"","gift_ios_id":"","gift_quantity":"","iAutoTypeId":"115746","iDeductItemCode":"","iDeductItemCount":"0","iItemCode":"45222010001","iItemCount":"200","iItemId":"10603277","iItemTime":"","image_label":"","individual_price":"","item_hide":"","jump_url":"","sGoodsPic":"","sGoodsPicBig":"","sGoodsPicMd5":"","sItemName":"NZ\u70b9","sItemType":"1","sItemValue":"1","sServiceType":"nzm","sValidPeriod":""},"image_label":""}],"num":1,"default_gift_info":null}]}}"""

/**
 * 凭证失效。ret != 0 一律当错误，msg 里带 token 字样时提示重新抓。
 */
internal const val RESPONSE_TOKEN_EXPIRED = """{"ret":100001,"msg":"access token expired","data":null}"""

/**
 * MyCardList：2026-09-22 重新抓的那份，**当前线上真实数据**。
 *
 * 和 07-29 那份的差异：旧的悦享卡已经过期并被清走，换了新的一张
 * （record_id 11737452 → 12910939，有效期 2026-09-06 ~ 2026-10-05），
 * 但**结构没变**——这次报「响应缺少 data 节点」跟协议升级无关，
 * 是域名从 agw 换成 bgw 之后老域名直接 404（见 [RESPONSE_GATEWAY_404]）。
 */
internal const val RESPONSE_MY_CARD_LIST_CURRENT = """{"ret":0,"msg":"","data":{"my_user_info":[{"card_type":"month","card_id":"yxk188","card_group":"yxk188","gid":1471,"role":{"game_open_id":"EAAA14DF8334D3511A38FCEC7AB9A146","game_app_id":"1110484610","area_id":1,"plat_id":1,"partition_id":1,"partition_name":"6buY6K6k5pyN5Yqh5Zmo","role_id":"419175704459784","role_name":"5oiR5aW96I-c55qE5Za1","device":"android","flag":0},"record_id":"12910939","page_common_info":{"pay_text":"\u793c\u5305\u81ea\u52a8\u53d1\u653e","pay_desc":"188\u5143\u60a6\u4eab\u5361\u7b7e\u7ea6\u7eed\u8d39\u751f\u6548\u4e2d\uff0c\u793c\u5305\u81ea\u52a8\u5230\u8d26","status":3,"unpay_text":"","unpay_jump_url":"","color":0,"is_ios_use_coupon":false},"month":{"base_info":{"card_status":102,"start_time":1788624005,"end_time":1791215999,"gift_status":0,"gift_got_num":16,"gift_toget_num":13,"gift_total_num":30,"x_day_card_id":"12910939","gift_type":["reopen","daily"]},"h5_auto_pay_info":{"entrance_status":2,"button_status":1,"expire_time":"1791216504","desc":"","check_tips_title":"","check_tips_desc":""},"pre_sell_info":{"entrance_status":0,"tips_for_order":"","id":0,"effective_time":"0","msg":""}},"hmc":null,"once":null,"cp_week":null,"group_buy":null,"red_packet":null,"premium":null,"game_month":null,"game_time":null,"game_week":null}],"expire_user_info":[]}}"""

/**
 * `agw.xinyue.qq.com` 在 2026-09-22 之后的返回。
 *
 * ⚠️ 它是**合法 JSON**，但既没有 `ret` 也没有 `data`——所以按 `{ret,msg,data}` 拆的
 * 那套会把落到「响应缺少 data 节点」，看着像接口升级了，其实是域名整个下掉。
 * 现在 cookie 层要认 `error_msg`：见到它就是网关没接住，该换域名重试。
 */
internal const val RESPONSE_GATEWAY_404 = """{"error_msg":"404 Route Not Found"}"""
