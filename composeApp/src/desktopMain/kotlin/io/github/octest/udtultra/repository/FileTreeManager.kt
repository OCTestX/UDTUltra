package io.github.octest.udtultra.repository

import io.github.octest.udtultra.Const
import io.github.octest.udtultra.repository.database.UDiskEntry
import java.io.File
import java.math.BigInteger

object FileTreeManager {
    val storageDir = File(Const.appDir, "storage")
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

    fun getFilePathHex16(root: File, file: File): List<String> {
        return toHex(getRelationPath(root, file))
    }

    fun getFilePathHex16(relationPath: String): List<String> {
        return toHex(relationPath)
    }

    private fun toHex(path: String): List<String> {
        val hexs = BigInteger(path.toByteArray()).toString(16).chunkedSequence(120).toList()
        return if (hexs.size > 1) {
            hexs.subList(0, hexs.size - 1).map { "Fold-$it" } + hexs.last()
        } else hexs
    }

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