package io.github.octest.udtultra.repository

import io.github.octest.udtultra.Const
import io.github.octest.udtultra.repository.database.UDiskEntry
import java.io.File
import java.math.BigInteger

/**
 * 文件树管理器，用于管理本地存储目录中的文件结构。
 *
 * 该对象提供了一系列方法来处理文件路径的映射、链接和查找操作，
 * 并支持将长路径转换为十六进制分段存储以避免文件系统路径长度限制。
 */
object FileTreeManager {
    /**
     * 存储根目录，基于应用目录下的 "storage" 文件夹。
     * 如果目录不存在会自动创建。
     */
    val storageDir = File(Const.appDir, "storage").apply {
        mkdirs()
    }

    /**
     * 根据给定的 UDiskEntry 和路径列表构建一个本地文件路径。
     *
     * @param entry 表示远程或虚拟文件的入口信息
     * @param paths 路径片段列表，表示文件在逻辑结构中的相对路径
     * @return 构建完成的本地 File 对象
     */
    fun linkFile(entry: UDiskEntry, paths: List<String>): File {
        var file = File(storageDir, entry.id)
        for (path in paths) {
            file = File(file, path)
        }
        val dir = file.parentFile
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return file
    }

    /**
     * 查找指定 entry 和路径对应的本地文件是否存在。
     *
     * @param entry 表示远程或虚拟文件的入口信息
     * @param path 文件的逻辑路径
     * @return 如果文件存在则返回成功结果包含该文件，否则返回失败结果
     */
    fun getExitsFile(entry: UDiskEntry, path: String): Result<File> {
        var file = File(storageDir, entry.id)
        val hexs = getFilePathHex16(path)
        for (hex in hexs) {
            file = File(file, hex)
        }
        return if (file.exists()) {
            Result.success(file)
        } else {
            Result.failure(Exception("File not found"))
        }
    }

    /**
     * 获取从根目录到目标文件之间的相对路径，并将其转换为十六进制分段列表。
     *
     * @param root 根目录文件对象
     * @param file 目标文件对象
     * @return 十六进制编码后的路径分段列表
     */
    fun getFilePathHex16(root: File, file: File): List<String> {
        return toHex(getRelationPath(root, file))
    }

    /**
     * 将给定的相对路径字符串转换为十六进制分段列表。
     *
     * @param relationPath 相对路径字符串
     * @return 十六进制编码后的路径分段列表
     */
    fun getFilePathHex16(relationPath: String): List<String> {
        return toHex(relationPath)
    }

    /**
     * 将输入路径字符串转换为十六进制表示形式，并按长度进行分段处理。
     *
     * 路径首先被转换为字节数组，然后转为大整数并以16进制表示。
     * 若结果超过120字符，则会被分割成多个部分，前面的部分加上 "Fold-" 前缀。
     *
     * @param path 输入的路径字符串
     * @return 分段后的十六进制字符串列表
     */
    private fun toHex(path: String): List<String> {
        val hexs = BigInteger(path.toByteArray()).toString(16).chunkedSequence(120).toList()
        return if (hexs.size > 1) {
            hexs.subList(0, hexs.size - 1).map { "Fold-$it" } + hexs.last()
        } else hexs
    }

    /**
     * 将字符串按照指定大小进行分段，返回一个序列。
     *
     * @param size 每段的最大字符数
     * @return 分段后的字符串序列
     */
    fun String.chunkedSequence(size: Int): Sequence<String> {
        return object : Sequence<String> {
            override fun iterator(): Iterator<String> = object : Iterator<String> {
                var nextIndex = 0
                override fun hasNext(): Boolean = nextIndex < this@chunkedSequence.length
                override fun next(): String {
                    val end = minOf(nextIndex + size, this@chunkedSequence.length)
                    val chunk = this@chunkedSequence.substring(nextIndex, end)
                    nextIndex = end
                    return chunk
                }
            }
        }
    }

    /**
     * 计算目标文件相对于根目录的相对路径。
     *
     * @param root 根目录文件对象
     * @param file 目标文件对象
     * @return 从根目录到目标文件的相对路径字符串
     */
    fun getRelationPath(root: File, file: File): String {
        if (root.absolutePath == file.absolutePath) return ""
        return try {
            val path = file.absolutePath.substring(root.absolutePath.length + 1)
            path
        } catch (e: Throwable) {
            println("ERROR: $file - $root")
            throw e
        }
    }
}

//private fun main() {
//    println(FileTreeManager.getFilePathHex16(File("/home/octest/Desktop/UDTUltra/test/SkyFactory 4-4.2.21.zip")))
//}
