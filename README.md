# 反重力数据面板 Android 版

> PC 端《逆战：未来》战绩查询工具「反重力数据面板」的 Android 移植

![平台](https://img.shields.io/badge/platform-Android%2013%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.12.0-2E86FF)
![License](https://img.shields.io/badge/license-MIT-green)

把 PC 端「反重力数据面板」的核心能力搬到手机上：**填写 Cookie，查战绩、看统计**。
砍掉了 PC 端的部分功能（抓包、QQ 绑定、达达签、万象）。

本项目与腾讯、天美工作室群**无任何隶属或合作关系**。所有数据来自官方微信小程序公开接口，
仅供个人战绩查询与统计用途。

---

## 开始用

Release 页：**<https://github.com/Perpetual-Memories/antigravitypanel-android/releases>**

应用没有内置获取cookie功能，请自行获取。
- **手机 root 了**：装 [Reqable](https://reqable.com/) 抓微信小程序的 HTTPS 流量，
  在请求头里复制整条 `Cookie`。
- **手机没有 root**：用 PC 端「反重力数据面板」抓取，再把 Cookie 粘贴到手机上。

### 关于隐私

- **Cookie 只存在本机**，文件是 `EncryptedSharedPreferences`（密钥 AES256-GCM、
  值 AES256-GCM、主密钥 AES256_GCM），绝对不会上传到除腾讯官方接口以外的任何地方。
- 代码里没有埋点、没有统计 SDK、没有第三方上报。
- 所以这个仓库里**不存在任何用户凭据**，推上来的只有源码本身。

提问题请登入GitHub并使用Issues，有想法请使用Pull requests。

---

## 致谢

开发过程中参考或应用了以下开源项目的接口、设计与实现，在此一并致谢：

| 项目 | 说明 |
| :-- | :--- |
| [NZM](https://github.com/HaMan412/NZM) — 哈曼 @Haman412 | 一切的起点。|
| [Miuix](https://github.com/compose-miuix-ui/miuix/) — @YuKongA | UI 组件库 |
| [HyperIsland](https://github.com/1812z/HyperIsland) — 芥子 @1812z | 参考甚至直接使用了其极为优秀的 UI 实现 |

液态玻璃底栏同样来自 Miuix example，源头是 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Apache-2.0）。

---

## License

[MIT](LICENSE)

游戏素材（名称、图标、地图与武器数据等）版权均归《逆战：未来》运营方所有；
本应用免费、非商业，不含广告与推广，仅读取官方已开放的战绩接口，不涉及图像识别、
内存注入或任何游戏数据修改。
PC 端「反重力数据面板」是另一个独立程序，请前往B站关注@哈曼曼曼曼曼https://space.bilibili.com/322071443了解更多有关PC端反重力数据面板信息。
本仓库只包含 Android 端。
