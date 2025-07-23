package io.github.octest.udtultra.logic

import io.github.octest.udtultra.Const
import io.github.octest.udtultra.repository.FileTreeManager
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.BanedDirRecord
import io.github.octest.udtultra.repository.database.BanedFileRecord
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
                        val banedFiles = UDTDatabase.getBanedFiles(entry)
                        val banedDirs = UDTDatabase.getBanedDirs(entry)
                        try {
                            traverseDirectory(entry.target, entry.target, banedFiles, banedDirs) {
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

    private suspend fun UDTDatabase.EntryWorker.traverseDirectory(
        root: File,
        file: File,
        banedFiles: List<BanedFileRecord>,
        banedDirs: List<BanedDirRecord>,
        throughFile: suspend (File) -> Unit
    ) {
        ologger.debug { "traversingDirectory: $file" }
        file.listFiles()?.forEach { child ->
//                println("Traverse: $child")
            ologger.debug { "traversingDirectoryChild: $child" }
            try {
                val tryToGetBanedFile = banedFiles.find {
                    it.entryId == entry.id && it.filePath == FileTreeManager.getRelationPath(
                        root,
                        child
                    )
                }
                val tryToGetBanedDir = banedDirs.find {
                    it.entryId == entry.id && it.dirPath == FileTreeManager.getRelationPath(
                        root,
                        child
                    )
                }
                if (
                    (child.isFile && tryToGetBanedFile == null) ||
                    (child.isDirectory && tryToGetBanedDir == null)
                ) {
                    throughFile(child)
                    saveFile(child)
                    Const.seekPoint()
                    if (child.isDirectory) {
                        traverseDirectory(root, child, banedFiles, banedDirs, throughFile)
                    }
                } else {
                    ologger.info { "SkipBlacklist: $child" }
                }
            } catch (e: Throwable) {
                ologger.error(e) { "traverseDirectoryFail: $file" }
                throw e
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
