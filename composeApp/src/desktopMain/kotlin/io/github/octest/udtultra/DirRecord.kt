package io.github.octest.udtultra

import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.UDTDatabase.DirTreeEntry
import io.github.octestx.basic.multiplatform.common.utils.gb
import io.github.octestx.basic.multiplatform.common.utils.mb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class DirRecord(
    private val entry: DirTreeEntry
) {
    suspend fun start() {
        withContext(Dispatchers.IO) {
            UDTDatabase.lockEntry(entry) {
                traverseDirectory(entry.target)
                println("DONE!")
            }
        }
    }

    private suspend fun UDTDatabase.EntryWorker.traverseDirectory(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                println("Traverse: $child")
                seekFile(child)
                Config.seekPoint()
                if (child.isDirectory) {
                    traverseDirectory(child)
                }
            }
        }
    }
}

private fun main() {
    val recorder = DirRecord(
        entry = DirTreeEntry(
            name = "test",
            target = File("/home/octest/Desktop/UDTUltra/test"),
            id = "testId",
            totalSpace = 2.gb,
            freeSpace = 323.mb,
        )
    )
    runBlocking {
        recorder.start()
    }
}
