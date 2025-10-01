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

/**
 * 用于记录并处理 U 盘目录结构的类。
 *
 * @param entry 表示当前操作的 U 盘条目信息。
 */
class DirRecorder(
    private val entry: UDiskEntry
) {
    private val ologger = noCoLogger("DirRecorder-${entry.id}")

    /**
     * 启动目录遍历与文件保存流程。
     * 此方法将在 IO 调度器中执行，并根据 entry 类型判断是否进行实际复制工作。
     */
    suspend fun start() {
        withContext(Dispatchers.IO) {
            UDTDatabase.runInEntry(entry) {
                // 只有普通u盘才会去复制
                if (entry.type == UDiskEntry.Companion.Type.COMMON.value) {
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

                            // 开始递归遍历目标目录
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
    }

    /**
     * 递归遍历指定目录下的所有子项（包括文件和目录）。
     * 对于不在黑名单中的项目，将调用 [throughFile] 回调并将其保存到数据库。
     *
     * @param root 根路径，用于计算相对路径。
     * @param file 当前正在处理的文件或目录。
     * @param banedFiles 已被禁止的文件列表。
     * @param banedDirs 已被禁止的目录列表。
     * @param throughFile 每遇到一个有效文件/目录时触发的回调函数。
     */
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

                // 判断该文件或目录是否在黑名单中
                if (
                    (child.isFile && tryToGetBanedFile == null) ||
                    (child.isDirectory && tryToGetBanedDir == null)
                ) {
                    throughFile(child)
                    saveFile(child)
                    Const.seekPointDelay()

                    // 若是目录则继续递归
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
