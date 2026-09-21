# 反重力数据面板 · R8 / ProGuard 规则
#
# 背景：debug 包 53 MB，其中 **DEX 就有 41 MB**（17 个 dex）。原因是项目从来没开过
# 代码压缩，Compose、Ktor、Room、Coil、Vico/materialkolor、OkHttp、Miuix、
# Tink(androidx.security.crypto) 全量打进去了，用不到的那部分一分没少。
# 开 minify 之后 R8 摇树把这些剔掉，是唯一的量级级优化手段。
#
# 下面每一条都写了"为什么要保留"，方便以后排查只有发布版才崩的问题。
#
# 排障提示：想知道某个类为什么没被剔掉 / 被剔掉了吗，去看
#   app/build/outputs/mapping/release/mapping.txt   （混淆前后对照）
# 想要"哪些类被删了"的清单，临时在下面加一行：
#   -printusage app/build/outputs/mapping/release/usage.txt

# ---------------- kotlinx.serialization ----------------
# @Serializable 的 serializer 虽然主要是编译期生成的，但只要有一处走反射
# （Json.decodeFromString<T>() 的 reified、多态鉴别名的查找、Companion.serializer()），
# 缺这几条就会在发布版抛 SerializationException，而 debug 版完全正常。
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.nzd.antigravitypanel.** {
    kotlinx.serialization.KSerializer serializer(...);
    *** Companion;
}
-keep,includedescriptorclasses class com.nzd.antigravitypanel.**$$serializer { *; }
-keepclasseswithmembers class com.nzd.antigravitypanel.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 可序列化数据类的成员整体保留：它们既是 JSON 的字段来源，也是崩溃日志里
# 最需要看清的部分；只占几 KB，换一个"发布版也能读的堆栈"很划算。
# 注意 `implements` 是 ProGuard 的关键字，别写成 Kotlin 的 `:`
-keepclassmembers class com.nzd.antigravitypanel.** implements kotlinx.serialization.Serializable { *; }

# ---------------- Room ----------------
# room-runtime 自带 consumer 规则，这里补的是容易被新版本漏掉的两条：
# _Impl 是靠 Class.forName 反射.newInstance() 出来的，不是直接 new 的。
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclasseswithmembers class * {
    @androidx.room.Dao <methods>;
}
-dontwarn androidx.sqlite.driver.bundled.**
-dontwarn androidx.room.paging.**

# ---------------- Compose ----------------
# Composable 函数是"被 Composer 回调"的，本身不一定有显式调用点。
# -keepclassmembers 只在类被保留时生效，不会把整个库拖住。
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ---------------- Ktor / OkHttp / Coil ----------------
# OkHttp 与 Coil 自带 consumer 规则。Ktor 的引擎是显式引用的（HttpClient(OkHttp)），
# 但它的 channel / platform 分支里有大量可选依赖，编译期不在 classpath 上，
# 不 dontwarn 会让 R8 直接报错而不是警告。
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn okhttp3.coroutines.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.spongycastle.**
-dontwarn org.slf4j.**

# ---------------- Tink（来自 androidx.security.crypto）----------------
# 只用到 EncryptedSharedPreferences + AES256_GCM，KeysetManager / KMS /
# protobuf 那一大套（共 1600 多个类）全是死代码，交给摇树。
# gson 是 Tink 的 JsonKeysetReader 拉进来的，同样用不到。
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.gson.**

# ---------------- JNI ----------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------------- 通用 ----------------
# Signature 丢了泛型类型信息，ViewModel 的 StateFlow<List<X>> 在 R8 内联后
# _cast 的地方会 ClassCastException；Annotation 丢了 Room / Serialization 全崩。
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
