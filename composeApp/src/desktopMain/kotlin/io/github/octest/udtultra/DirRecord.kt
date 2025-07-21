package io.github.octest.udtultra

import io.github.octest.udtultra.logic.WorkStacker
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.UDTDatabase.DirTreeEntry
import io.github.octestx.basic.multiplatform.common.utils.gb
import io.github.octestx.basic.multiplatform.common.utils.mb
import io.klogging.noCoLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class DirRecorder(
    private val entry: DirTreeEntry
) {
    private val ologger = noCoLogger("DirRecorder-${entry.id}")
    suspend fun start() {
        withContext(Dispatchers.IO) {
            UDTDatabase.lockEntry(entry) {
                WorkStacker.putWork(
                    WorkStacker.Worker(
                        WorkStacker.WorkInfo(
                            "U盘复制中",
                            WorkStacker.WorkType.CopyFromSource,
                            WorkStacker.ProgressType.Running
                        )
                    ) {
                        traverseDirectory(entry.target) {
                            ologger.info { "Traverse: $it" }
                            setTitle("U盘复制中: $it")
                        }
                        println("DONE!")
                    },
                )
            }
        }
    }

    private suspend fun UDTDatabase.EntryWorker.traverseDirectory(file: File, throughFile: suspend (File) -> Unit) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
//                println("Traverse: $child")
                throughFile(child)
                seekFile(child)
                Config.seekPoint()
                if (child.isDirectory) {
                    traverseDirectory(child, throughFile)
                }
            }
        }
    }
}

private fun main() {
    val recorder = DirRecorder(
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
