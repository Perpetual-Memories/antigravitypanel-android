# 反重力数据面板 Android 版

> PC 端《逆战：未来》战绩查询工具「反重力数据面板」的 Android 移植
> · 非官方 · MIT 开源 · 仅通过 GitHub Release 分发

![平台](https://img.shields.io/badge/platform-Android%2013%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.12.0-2E86FF)
![License](https://img.shields.io/badge/license-MIT-green)

把 PC 端「反重力数据面板」的核心能力搬到手机上：**填一条 Cookie，查战绩、看统计**。
不含 PC 端那些依赖桌面平台的周边功能（抓包、QQ 绑定、娱乐模块）。

本项目与腾讯、天美工作室群**无任何隶属或合作关系**。所有数据来自官方微信小程序公开接口，
仅供个人战绩查询与统计用途。

---

## 能做什么

四个 Tab，一级页用 `HorizontalPager` 横向切换；二级页不用路由，而是走
HyperIsland 那套 `PredictiveNavLayer` 图层（返回手势跟手位移 + 背景缩放虚化）。

| Tab | 内容 |
| :-- | :--- |
| **概览** | 凭证状态卡、模式切换（猎场 / 塔防 / 时空追猎）、场次与在线时长、近五场统计（MVP / 评分 / Boss 伤害 / 金币）、活动日历（只显示快截止的几条）、首胜宝箱 |
| **历史战绩** | 本地库全量列表，触底加载更多；五维级联筛选（模式 / 地图 / 难度 / 日期 / 胜负收藏）；长按置顶或收藏；点击进详情 |
| **地图分布** | 三个模式各一个子 Tab，卡片正面是场次 + 难度分布，翻面看掉落物解锁进度 |
| **设置** | 外观与主题、自动刷新、本地数据保留期、清空本地数据、实验性的「导入数据」、关于 |

### 为什么要有本地数据库

服务端只保留**滚动窗口**（近 30 天 / 最近 100 场），超出就永久拉不回来。
本地 Room 库做增量累积：拉取时按时间倒序，**遇到"本地已有且已完成"就停**，
对局变成历史的那一刻就被留住了。这也是「历史战绩」Tab 的唯一数据来源。

---

## 开始用

Release 页：**<https://github.com/Perpetual-Memories/antigravitypanel-android/releases>**

下载最新 APK 装上即可（单个通用包，arm64 + arm32 都在里面，约 11 MB；debug 包约 50 MB）。
首次打开需要填 Cookie，两种方式任选其一：

- **手机 root 了**：装 [Reqable](https://reqable.com/) 抓微信小程序的 HTTPS 流量，
  在请求头里复制整条 `Cookie`。
- **手机没有 root**：用 PC 端「反重力数据面板」抓取，再把 Cookie 粘到手机上。

App 内的「如何获取 Cookie」引导写的就是这两条。

### 关于隐私

- **Cookie 只存在本机**，文件是 `EncryptedSharedPreferences`（密钥 AES256-GCM、
  值 AES256-GCM、主密钥 AES256_GCM），绝对不会上传到除腾讯官方接口以外的任何地方。
- 代码里没有埋点、没有统计 SDK、没有第三方上报。
- 所以这个仓库里**不存在任何用户凭据**，推上来的只有源码本身。

有问题或想聊玩法：QQ 群 `1004721478`（App 的「讨论」里有复制按钮）。

---

## 技术栈

版本号以 [`gradle/libs.versions.toml`](gradle/libs.versions.toml) 为准。

| | |
| :-- | :-- |
| 语言 / 编译器 | Kotlin 2.4.0（KSP 2.3.12） |
| UI | Compose Multiplatform 1.12.0（当前**只有 android 目标**） |
| 组件库 | [Miuix](https://github.com/compose-miuix-ui/miuix) 0.9.4-rc01 —— `ui` / `preference` / `blur` / `icons` **必须同版本** |
| 图表 | [Vico](https://github.com/patrykandpatrick/vico) 3.3.1 |
| 导航 | androidx Navigation 3 + NavigationEvent（**不用** `miuix-navigation3-ui`——它只发到 0.9.3） |
| 网络 | Ktor 3.5.2（OkHttp 引擎） |
| 本地库 | Room 2.8.5 + bundled SQLite 2.7.1 |
| 图片 | Coil 3.6.3（掉落物图标是官方 CDN 上的 PNG，现拉现画） |
| 安全存储 | androidx.security.crypto 1.1.0 |
| Android | AGP 9.2.1 · minSdk 33 · targetSdk 36 · compileSdk 37 |

液态玻璃底栏的实现 vendored 在 `shared/.../ui/component/liquid/`，来自 Miuix example
（改编自 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)，Apache-2.0），
**不是 Miuix 的库 API**，已冻结，只在升级 Miuix 大版本时同步。

---

## 工程结构

```
app/      Android 外壳：Activity、Manifest、主题资源、启动图标
shared/   全部业务与 UI（commonMain + androidMain）
  data/remote     IdeClient / NzApi / Method —— 接口层
  data/db         Room：matches（对局快照）+ config_cache（配置原始 JSON）
  data/repo       MatchSyncer 增量同步、Repository 聚合
  data/credential cookie 解析与校验
  data/settings   UserSettings / MatchMarks
  data/imports    实验性「导入 JSON」
  domain/         与平台无关的一层纯 Kotlin 计算
  ui/             四 Tab + 二级页 + component（含 vendored liquid / animation / effect）
tools/    一次性脚本（图标生成、HAR 夹具生成），不参与构建
```

### 想改动的几个注意点

这接口有几个坑，踩过就明白为什么代码写成那样了：

- `map_mode` **不是可选参数**：不带它服务端只回猎场那一份，塔防和时空追猎永远同步不下来。
  取值必须是 `"猎场"`（不是 UI 上显示的"僵尸猎场"），三个模式要各翻一遍。
- 分页**没有 total**：只能靠"返回空页或不满足一页"判断到底了。请求出错也照样返回 200，
  得看响应体里的 `iRet` / `code`。
- 数字一律用字符串返回，缺值给空串（`iFinTime=""`），所以要走 `Lenient*` 系列反序列化。
- cookie 里的 `appid` 有服务端强校验，一律改写成 `1112451898`。
- 地图名 / 难度名 / 分区名只有在**有凭证**时才能从 `center.config.list` 拉到，
  所以 `domain/GameModes.kt` 内置了完整备份表。难度号段不是连续的
  （2-6 / 33 / 66-69 / 95 / 130-133 / 160），别只抄猎场那半张。
- **地图分布用的是官方 `center.user.map.stats`（通关数），不是本地库算的场次**，
  两者口径本来就不一样。小程序和官方 PC 端显示的也是前者。
- 服务端时间串不带时区，统一按 **UTC+8** 解析；保留期按北京日历月裁剪。
- `RemoteConfig` 里的 appid 是小程序自己的公开标识，不是密钥。

---

## 自己构建

需要 **JDK 21** 和 Android SDK（compileSdk 37）。

```bash
# Debug
./gradlew :app:assembleDebug

# Release（开了 R8 + 资源收缩，见 app/proguard-rules.pro，逐条写了保留原因）
./gradlew :app:assembleRelease

# 测试
./gradlew :shared:testAndroidHostTest
```

**Release 签名**：读取仓库根目录的 `keystore.properties`，它和 `*.jks` 都在
`.gitignore` 里 —— **缺了它们构建不会失败，只是产出未签名的 APK**。想签自己的 Release，
放一份过去就行：

```properties
storeFile=antigravitypanel.jks
storePassword=...
keyAlias=...
keyPassword=...
```

> 注意 keytool 会把 `-alias` 强制转成小写，所以 keyAlias 一律填小写。

相关的还有一条：本机 JDK 路径（`org.gradle.java.home`）**故意没有**写进 `gradle.properties` ——
那是机器相关的配置，写进去别人 clone 下来就会构建失败。请放进用户级
`~/.gradle/gradle.properties`。

Release 只对 `androidComponents` 的 release 变体裁 ABI 而不是用 `splits` —— `splits`
对所有变体生效，会导致 x86_64 模拟器装不上 debug 包。

---

## 测试

`shared/src/hostTest` 下 **129 个用例，全部通过**（截至 v0.1.0），覆盖的是**接口层最难肉眼验证的那部分**：
HAR 抓包原始数据怎么拆包（`IdeResponseTest`）、三模式分页与增量同步怎么停
（`MatchSyncTest`）、UTC+8 时间解析、筛选条件怎么匹配笼统档位 vs 分级名、
cookie 解析容错与 JSON 导入的三种历史格式。

其中 `HarFixtures.kt` 是 `tools/gen_har_fixtures.py` 从真实抓包导出的夹具，不是测试数据臆造的。

---

## 致谢

开发过程中参考或应用了以下开源项目的接口、设计与实现，在此一并致谢：

| 项目 | 说明 |
| :-- | :--- |
| [NZM](https://github.com/HaMan412/NZM) — 哈曼 @Haman412 | 一切的起点。接口与统计口径的参照，已保留其 MIT 声明里的赞赏请求 |
| [Miuix](https://github.com/compose-miuix-ui/miuix/) — @YuKongA | UI 组件库，也是 `AboutScreen` 上半部分版式的来源 |
| [HyperIsland](https://github.com/1812z/HyperIsland) — 芥子 @1812z | 参考甚至直接使用了其极为优秀的 UI 实现 |

液态玻璃底栏同样来自 Miuix example，源头是 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Apache-2.0）。

---

## License

[MIT](LICENSE)，版权归 Perpetual-Memories 所有。

游戏素材（名称、图标、地图与武器数据等）版权均归《逆战：未来》运营方所有；
本应用免费、非商业，不含广告与推广，仅读取官方已开放的战绩接口，不涉及图像识别、
内存注入或任何游戏数据修改。PC 端「反重力数据面板」是另一个独立程序，
本仓库只包含 Android 端。
