package com.nzd.antigravitypanel.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * [packZip] 的 Android 实现。
 *
 * 用 `ZipOutputStream` 的默认压缩级别（deflate）：数据包里最大的一块是对局 JSON，
 * 重复的字段名能压掉一大半，而这几千行压起来也就几十毫秒，不值得再调。
 * 不写时间戳 —— 包内容才是要比较的东西，时间戳只会让同一份数据每次都"不一样"。
 */
actual fun packZip(files: List<ZipOutEntry>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for (file in files) {
            zip.putNextEntry(ZipEntry(file.name))
            zip.write(file.bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * [unpackZip] 的 Android 实现。
 *
 * 两个细节：
 * - `readBytes()` 在 `ZipInputStream` 上读到的是**当前这一个 entry**（到条目末尾会返回 -1），
 *   所以不用自己算 length；
 * - 只认包内的**文件名**（`substringAfterLast('/')`）：用户手动重打包时可能套一层目录，
 *   按完整路径找会找不到文件，而我们要的就是那七份固定名字的文件。
 *
 * 超过体积上限抛 `IllegalArgumentException`，由导入侧转成一句人话。
 */
actual fun unpackZip(bytes: ByteArray): Map<String, ByteArray> {
    val result = LinkedHashMap<String, ByteArray>()
    var total = 0L
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            if (result.size >= MAX_BACKUP_ENTRIES) {
                throw IllegalArgumentException("压缩包里文件太多，不是本应用导出的数据包")
            }
            val data = zip.readBytes()
            total += data.size
            if (total > MAX_BACKUP_BYTES) {
                throw IllegalArgumentException("压缩包解压后体积过大，已中止")
            }
            val name = entry.name.substringAfterLast('/')
            if (name.isNotEmpty()) result[name] = data
        }
    }
    if (result.isEmpty()) throw IllegalArgumentException("压缩包是空的")
    return result
}
