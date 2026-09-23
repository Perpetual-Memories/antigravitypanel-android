// 反重力数据面板 · Android 外壳模块
// 只负责 Activity / Manifest / 主题资源，业务与 UI 全部在 :shared

import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity)
}

android {
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37)
    }
    namespace = "com.nzd.antigravitypanel"
    defaultConfig {
        applicationId = "com.nzd.antigravitypanel"
        minSdk = 33
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }
    // 发布签名：凭据放 keystore.properties（和 *.jks 一样在 .gitignore 里）。
    // **文件不存在就退化成不签名**，而不是让构建直接失败——别人 clone 下来跑
    // assembleRelease 时不该因为缺密钥而编译不过。
    // 路径相对 rootProject：app/file() 是相对 app 模块的，会找错地方。
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = keystorePropsFile.takeIf { it.exists() }?.let { file ->
        Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
    }
    if (keystoreProps == null) {
        logger.warn(
            "未找到 keystore.properties，assembleRelease 将产出**未签名**的 APK，" +
                "不能直接对外发布。",
        )
    }
    signingConfigs {
        keystoreProps?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // v3 签名默认没开，apksigner 只会验出 v2。打开是为了将来能**轮换密钥**——
                // 想换证书又不让用户卸载重装，v3 的 lineage 是唯一的路。
                //
                // 注意 AGP 的行为：**开了 v3 就会把 v2 关掉**（实测 `enableV2Signing = true`
                // 写了也不生效，产物里只剩 v3 块）。minSdk 33 下这是安全的——
                // v3 从 Android 9 起被支持，而 API < 33 本来就被 minSdk 挡在门外了。
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            keystoreProps?.let { signingConfig = signingConfigs.getByName("release") }
            // 代码压缩 + 资源收缩。
            // 代码压缩 + 资源收缩。这是把包从 50 多 MB 降下来的唯一量级手段，
            // 不是锦上添花：debug 包里 DEX 占 41 MB（17 个 dex），全是没用到的
            // Compose / Ktor / Room / Coil / Tink 代码，摇树之后只剩真正会被执行到的那部分。
            // 实测 53.29 MB → 11.34 MB。规则都在 proguard-rules.pro 里逐条写了原因。
            isMinifyEnabled = true
            // 地图 / 武器那 491 张图全是 `painterResource(R.drawable.x)` 的静态引用，
            // 收缩是安全的（没有 getIdentifier 这种按名字查的路子，见 MapArt.android.kt 注释）。
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        jniLibs {
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
        resources {
            // OkHttp / Ktor / coroutines 带的许可证声明，运行时读不到
            excludes += "/META-INF/{AL2.0,LGPL2.1,LGPL3}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

// 只给 release 裁 ABI，产出一个**通用包**（arm64 + arm32 都在，一份 APK 通吃所有真机）。
//
// 为什么不用 `android { splits { abi { ... } } }`：splits 对**所有**变体生效，
// 一旦打开 debug 也只剩 arm 两个 ABI，x86_64 的模拟器直接装不上（实测踩过）。
// 而这里只对 release 精准出手，debug 保持四架构齐全。
//
// 为什么留两个 ARM 而不只留 arm64：省 1 MB 换来"个别 32 位老机装不上"不划算。
// 真要压到极限（每份 10.8 MB、但要发两个文件）时再开 splits。
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.jniLibs.excludes.addAll(
            listOf(
                "lib/x86/libsqliteJni.so",
                "lib/x86_64/libsqliteJni.so",
            ),
        )
    }
}
