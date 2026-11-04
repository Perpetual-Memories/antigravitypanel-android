package com.nzd.antigravitypanel.data.backup

/** zip 里的一个文件。名字用包内相对路径，本项目只用平铺的文件名。 */
data class ZipOutEntry(
    val name: String,
    val bytes: ByteArray,
)

/**
 * 打包成 zip（deflate 压缩）。
 *
 * 为什么是 expect/actual 而不是引一个 KMP 压缩库：这里只需要"打一个平铺的 zip"和
 * "把 zip 读成 名字 -> 字节"，平台自带的 `java.util.zip` 就够，为两个函数多拉一个
 * 第三方依赖不划算（也少一处版本风险）。
 */
expect fun packZip(files: List<ZipOutEntry>): ByteArray

/**
 * 解开 zip，返回 `包内文件名 -> 内容`。
 *
 * 只做内存解包、不落盘：数据包是一份要**先校验再写库**的东西，落到临时目录再多一步清理。
 * 超过 [MAX_BACKUP_BYTES] 直接抛异常，别让压缩炸弹把手机内存吃干净。
 *
 * @throws IllegalArgumentException 不是合法的 zip，或者解压后体积过大
 */
expect fun unpackZip(bytes: ByteArray): Map<String, ByteArray>
