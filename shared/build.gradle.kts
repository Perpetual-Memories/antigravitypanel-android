// 反重力数据面板 · 业务模块
// Compose Multiplatform 工程，当前只保留 android 目标（commonMain + androidMain）

plugins {
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        androidResources.enable = true
        buildToolsVersion = "37.0.0"
        compileSdk {
            version = release(37)
        }
        minSdk = 33
        namespace = "com.nzd.antigravitypanel.shared"

        // 新 Android-KMP 插件默认不开测试，必须显式 opt-in；
        // 开启后源码集叫 androidHostTest（不是老的 androidUnitTest）
        withHostTest { }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            dependencies {
                // Miuix：ui / preference / blur 三模块必须同版本号。
                // 导航走 androidx navigation3，不用 miuix-navigation3-ui（它也只发到 0.9.3）。
                api(libs.miuix.ui)
                api(libs.miuix.preference)
                api(libs.miuix.blur)
                implementation(libs.miuix.icons)

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.animation)
                implementation(libs.jetbrains.compose.window.size)
                implementation(libs.jetbrains.lifecycle.runtime.compose)

                implementation(libs.androidx.navigation3.runtime)
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.navigationevent)

                implementation(libs.vico.compose)

                // 掉落物图标是官方 CDN 上的 PNG，得现拉现画
                implementation(libs.coil.compose)

                // Ktor：引擎不放 commonMain，见下面的 androidMain
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.serialization.json)

                implementation(libs.kotlinx.coroutines.core)

                // Room：KMP 版，commonMain 直接可用，驱动用 bundled（跨平台一致）
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                // Coil 3 的网络取图器（OkHttp 引擎，和 Ktor 那边共用 OkHttp 依赖）
                implementation(libs.coil.network.okhttp)
                implementation(libs.androidx.security.crypto)
                // 实验性功能的「导入 JSON」要用系统的 SAF 文件选择器
                implementation(libs.androidx.activity)
            }
        }

        // 名字在不同 KGP/AGP 组合下可能是 androidHostTest 或 androidUnitTest，
        // 而且类型安全访问器未必生成，这里按名字找，两个都兜住
        for (name in listOf("androidHostTest", "androidUnitTest")) {
            findByName(name)?.apply {
                kotlin.srcDir("src/hostTest")
                dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.kotlinx.coroutines.core)
                }
            }
        }
    }
}

dependencies {
    // Room 的 KSP 在 KMP 下按目标生成配置。本项目只有 android 一个目标，
    // 所以挂 kspAndroid 就够；加 iOS/桌面目标时，每个目标都要各挂一次。
    add("kspAndroid", libs.androidx.room.compiler)
}
