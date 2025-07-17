package io.github.octest.udtultra.repository

import io.github.octest.udtultra.Config
import java.io.File
import java.math.BigInteger

object FileTreeManager {
    val storageDir = File(Config.appDir, "storage")
    fun getFile(entry: UDTDatabase.DirTreeEntry, paths: List<String>): File {
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

    fun getFilePathHex16(file: File): List<String> {
        return toHex(file.absolutePath)
    }

    fun getDirPathHex16(dir: File): List<String> {
        return toHex(dir.absolutePath)
    }

    private fun toHex(path: String): List<String> {
        return BigInteger(path.toByteArray()).toString(16).chunkedSequence(120).toList()
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

}

//private fun main() {
//    println(FileTreeManager.getFilePathHex16(File("/home/octest/Desktop/UDTUltra/test/SkyFactory 4-4.2.21.zip")))
//}