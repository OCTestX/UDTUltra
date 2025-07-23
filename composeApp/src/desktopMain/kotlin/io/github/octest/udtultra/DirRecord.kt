package io.github.octest.udtultra

import io.github.octest.udtultra.logic.WorkStacker
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.klogging.noCoLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DirRecorder(
    private val entry: UDiskEntry
) {
    private val ologger = noCoLogger("DirRecorder-${entry.id}")
    suspend fun start() {
        withContext(Dispatchers.IO) {
            UDTDatabase.runInEntry(entry) {
                WorkStacker.putWork(
                    WorkStacker.Worker(
                        WorkStacker.WorkInfo(
                            "U盘复制中",
                            WorkStacker.WorkType.CopyFromSource,
                            WorkStacker.ProgressType.Running
                        )
                    ) {
                        ologger.info { "UDiskRoot: ${entry.target}" }
                        try {
                            traverseDirectory(entry.target) {
                                ologger.info { "Traverse: $it" }
                                setTitle("U盘复制中: $it")
                            }
                            ologger.info { "DONE!" }
                        } catch (e: Throwable) {
                            setTitle("U盘复制失败")
                            ologger.error(e) { "U盘复制失败" }
                            throwErrorAndCancel(e)
                        }
                    },
                )
            }
        }
    }

    private suspend fun UDTDatabase.EntryWorker.traverseDirectory(file: File, throughFile: suspend (File) -> Unit) {
        ologger.debug { "traversingDirectory: $file" }
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
//                println("Traverse: $child")
                ologger.debug { "traversingDirectoryChild: $child" }
                try {
                    throughFile(child)
                    seekFile(child)
                    Const.seekPoint()
                    if (child.isDirectory) {
                        traverseDirectory(child, throughFile)
                    }
                } catch (e: Throwable) {
                    ologger.error(e) { "traverseDirectoryFail: $file" }
                    throw e
                }
            }
        }
    }
}

//private fun main() {
//    val recorder = DirRecorder(
//        entry = UDiskEntry(
//            name = "test",
//            target = File("/home/octest/Desktop/UDTUltra/test"),
//            id = "testId",
//            totalSpace = 2.gb,
//            freeSpace = 323.mb,
//
//        )
//    )
//    runBlocking {
//        recorder.start()
//    }
//}
