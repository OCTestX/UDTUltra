package io.github.octest.udtultra.logic

import io.github.octest.udtultra.repository.FileTreeManager
import io.github.octest.udtultra.repository.UDTDatabase
import io.github.octest.udtultra.repository.database.FileRecord
import io.github.octest.udtultra.repository.database.UDiskEntry
import io.github.octestx.basic.multiplatform.common.utils.storage
import kotlinx.io.IOException
import java.io.File
import java.io.FileOutputStream

/**
 * 处理从非KEY U盘复制特定文件到KEY U盘的核心逻辑类
 * 根据KEY U盘上预定义的规则文件，筛选并复制符合条件的文件
 *
 * @property keyEntry 表示KEY U盘的设备条目，作为文件复制的目标位置
 */
class QuitCopper(private val keyEntry: UDiskEntry) {
    /**
     * 启动文件复制流程
     * 执行以下核心步骤：
     * 1. 加载KEY U盘中的复制规则文件
     * 2. 创建目标存储目录结构
     * 3. 遍历所有非KEY U盘设备
     * 4. 筛选已完整复制且符合规则的文件
     * 5. 将文件复制到KEY U盘指定位置
     *
     * 当规则文件不存在时立即终止流程
     *
     * @return 无返回值
     */
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

    /**
     * 将单个文件从源U盘复制到KEY U盘
     * 实现断点续传机制，支持大文件安全传输
     * 目标路径保留源U盘ID和原始目录结构
     *
     * @param keyEntry KEY U盘设备条目（目标位置）
     * @param sourceEntry 源U盘设备条目
     * @param fileRecord 待复制的文件记录对象
     * @return 无返回值
     */
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
