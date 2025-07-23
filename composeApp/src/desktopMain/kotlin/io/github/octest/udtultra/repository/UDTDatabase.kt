package io.github.octest.udtultra.repository

import io.github.octest.udtultra.Const
import io.github.octest.udtultra.logic.WorkStacker
import io.github.octest.udtultra.repository.FileTreeManager.getFilePathHex16
import io.github.octest.udtultra.repository.FileTreeManager.getRelationPath
import io.github.octest.udtultra.repository.database.*
import io.github.octest.udtultra.utils.SpeedMonitoringOutputStream
import io.github.octest.udtultra.utils.StringMerger
import io.github.octest.udtultra.utils.createTableIfNotExists
import io.github.octest.udtultra.utils.transferToWithProgress
import io.github.octestx.basic.multiplatform.common.utils.RateLimitInputStream
import io.github.octestx.basic.multiplatform.common.utils.storage
import io.klogging.noCoLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ktorm.database.Database
import org.ktorm.dsl.*
import java.io.File
import java.io.FileOutputStream

object UDTDatabase {
    private val ologger = noCoLogger<UDTDatabase>()
    val DBFile = File(Const.appDir, "db.db")
    private val ktormDatabase: Database by lazy {
        Database.connect(
            url = "jdbc:sqlite:${DBFile.absolutePath}", driver = "org.sqlite.JDBC"
        ).apply {
            // 使用扩展函数创建表
            createTableIfNotExists(Entrys)
            createTableIfNotExists(Files)
            createTableIfNotExists(Dirs)
            createTableIfNotExists(BanedFiles)
            createTableIfNotExists(BanedDirs)
        }
    }

    suspend fun runInEntry(entry: UDiskEntry, block: suspend EntryWorker.() -> Unit) {
        if (entry.exist().not()) {
            writeNewEntry(entry)
        }
        if (entry.type == UDiskEntry.Companion.Type.KEY.value) {
            changeUDiskType(entry, UDiskEntry.Companion.Type.KEY)
        } else if (entry.type == UDiskEntry.Companion.Type.MASTER.value) {
            changeUDiskType(entry, UDiskEntry.Companion.Type.MASTER)
        }
        withContext(Dispatchers.IO) {
            EntryWorkerImpl(entry).apply {
                block()
            }
        }
    }

    suspend fun writeNewEntry(entry: UDiskEntry) {
        ktormDatabase.insert(Entrys) {
            set(it.id, entry.id)
            set(it.name, entry.name)
            set(it.totalSpace, entry.totalSpace)
            set(it.freeSpace, entry.freeSpace)
            set(it.type, UDiskEntry.Companion.Type.COMMON.value)
        }
    }

    interface EntryWorker {
        suspend fun seekFile(file: File)
    }

    suspend fun registerFile(entry: UDiskEntry, file: File) {
        ktormDatabase.insert(Files) {
            set(it.entryId, entry.id)
            set(it.filePath, getRelationPath(entry.target, file))
            set(it.fileName, file.name)
            set(it.parentDir, getRelationPath(entry.target, file.parentFile))
            set(it.size, file.length())
            set(it.createDate, file.lastModified())
            set(it.modifierDate, file.lastModified())
            set(it.status, 0) // status 设置为 0
        }
    }

    suspend fun writeFile(entry: UDiskEntry, file: File) {
        WorkStacker.putWork(
            WorkStacker.Worker(
                WorkStacker.WorkInfo(
                    title = "正在提取U盘文件: $file",
                    type = WorkStacker.WorkType.CopyFromSource,
                    progressType = WorkStacker.ProgressType.HasProgress
                ), work = {
                    try {
                        ktormDatabase.update(Files) {
                            set(it.status, 1)
                            where {
                                (it.entryId eq entry.id) and (it.filePath.eq(getRelationPath(entry.target, file)))
                            }
                        }
                        ologger.info { "SavingFile: $file" }
                        val totalSize = file.length()
                        val targetFile = FileTreeManager.linkFile(entry, getFilePathHex16(entry.target, file))
                        targetFile.createNewFile()
                        var bytesTransferred = targetFile.length() // 初始已传输字节数（断点续传）

                        RateLimitInputStream(file.inputStream().apply {
                            skipNBytes(bytesTransferred) // 跳过已存在的字节
                        }, SettingRepository.copySpeed.value).use { inputStream ->
                            val stringMerger = StringMerger { map ->
                                setMessage(
                                    "${map["speed"] ?: ""}   ${map["progressInfo"] ?: ""}"
                                )
                            }
                            SpeedMonitoringOutputStream(FileOutputStream(targetFile, true), callback = { speed ->
                                stringMerger.applyString("speed", "${storage(speed)}/s")
                            }).use { outputStream ->
                                inputStream.buffered().use { bufferedInput ->
                                    bufferedInput.transferToWithProgress(outputStream) { currentStepRead ->
                                        bytesTransferred += currentStepRead
                                        val progress = bytesTransferred.toFloat() / totalSize
                                        setProgress(progress) // 调用进度回调
                                        stringMerger.applyString(
                                            "progressInfo", "${storage(bytesTransferred)}/${storage(totalSize)}"
                                        )
                                        ologger.debug { "正在提取U盘文件: $file [Total: $totalSize, Transferred: $bytesTransferred]" }
                                    }
                                }
                            }
                        }

                        ologger.info { "SaveFileDone: $file" }
                        ktormDatabase.update(Files) {
                            set(it.status, 2)
                            where {
                                (it.entryId eq entry.id) and (it.filePath.eq(getRelationPath(entry.target, file)))
                            }
                        }
                    } catch (e: Throwable) {
                        ktormDatabase.update(Files) {
                            set(it.status, 3)
                            where {
                                it.filePath.eq(getRelationPath(entry.target, file))
                            }
                        }
                        ologger.error(e) { "复制出错，可能u盘被移除" }
                        throwErrorAndCancel(e)
                    }
                })
        ).join()
    }


    suspend fun writeDirInfo(entry: UDiskEntry, dir: File) {
        ktormDatabase.insert(Dirs) {
            set(it.entryId, entry.id)
            set(it.dirPath, getRelationPath(entry.target, dir))
            set(
                it.parentDir, getRelationPath(entry.target, dir.parentFile)
            ) // if equals to entry.target, parentDir is null
            set(it.fileName, dir.name)
            set(it.createDate, dir.lastModified())
            set(it.modifierDate, dir.lastModified())
        }
    }

    fun getEntrys(): List<UDiskEntry> {
        return ktormDatabase.from(Entrys).select().map {
            UDiskEntry(
                name = it[Entrys.name] ?: "",
                target = File(it[Entrys.name] ?: ""),
                id = it[Entrys.id] ?: "",
                totalSpace = it[Entrys.totalSpace] ?: 0,
                freeSpace = it[Entrys.freeSpace] ?: 0,
                type = it[Entrys.type] ?: "Common"
            )
        }.apply {
            ologger.debug { "getEntrys: $this@apply" }
        }
    }

    fun getEntry(id: String): UDiskEntry {
        return ktormDatabase.from(Entrys).select().where {
            Entrys.id.eq(id)
        }.map {
            UDiskEntry(
                name = it[Entrys.name] ?: "",
                target = File(it[Entrys.name] ?: ""),
                id = it[Entrys.id] ?: "",
                totalSpace = it[Entrys.totalSpace] ?: 0,
                freeSpace = it[Entrys.freeSpace] ?: 0,
                type = it[Entrys.type] ?: "Common"
            )
        }.first()
    }

    suspend fun fileIsBaned(entryId: String, relationFilePath: String): Boolean {
        return ktormDatabase.from(BanedFiles).select()
            .where { (BanedFiles.entryId eq entryId) and (BanedFiles.filePath eq relationFilePath) }.map { it }
            .firstOrNull() == null
    }

    suspend fun changeBanedFileStatus(entry: UDiskEntry, relationFilePath: String, baned: Boolean) {
        if (baned) {
            if (fileIsBaned(entry.id, relationFilePath)) {
                val fileRecord = getFile(entry, relationFilePath)
                if (fileRecord != null) {
                    ktormDatabase.insert(BanedFiles) {
                        set(it.entryId, entry.id)
                        set(it.filePath, relationFilePath)
                        set(it.fileName, fileRecord.fileName)
                        set(it.parentDir, fileRecord.parentDir)
                        set(it.size, fileRecord.size)
                    }
                }
            }
        } else {
            ktormDatabase.delete(BanedFiles) {
                (it.entryId eq entry.id) and (it.filePath.eq(relationFilePath))
            }
        }
        ktormDatabase.delete(Files) {
            (it.entryId eq entry.id) and (it.filePath.eq(relationFilePath))
        }
    }

    suspend fun dirIsBaned(entryId: String, relationDirPath: String): Boolean {
        return ktormDatabase.from(BanedDirs).select()
            .where { (BanedDirs.entryId eq entryId) and (BanedDirs.dirPath eq relationDirPath) }.map { it }
            .firstOrNull() == null
    }

    suspend fun changeBanedDirStatus(entry: UDiskEntry, relationFilePath: String, baned: Boolean) {
        val dirRecord = getDir(entry, relationFilePath)
        if (baned && dirRecord != null) {
            ktormDatabase.insert(BanedDirs) {
                set(BanedDirs.entryId, entry.id)
                set(BanedDirs.dirPath, relationFilePath)
                set(BanedDirs.dirName, File(relationFilePath).name)
                set(BanedDirs.parentDir, dirRecord.parentDir)
            }
        } else {
            ktormDatabase.delete(BanedDirs) {
                (BanedDirs.entryId eq entry.id) and (it.entryId eq entry.id)
            }
        }
        ktormDatabase.delete(Dirs) {
            (it.entryId eq entry.id) and (it.dirPath.eq(relationFilePath))
        }
    }
    fun changeUDiskName(entry: UDiskEntry, name: String) {
        ktormDatabase.update(Entrys) {
            set(it.name, name)
            where {
                it.id.eq(entry.id)
            }
        }
    }
    fun changeUDiskType(entry: UDiskEntry, type: UDiskEntry.Companion.Type) {
        ktormDatabase.update(Entrys) {
            set(it.type, type.value)
            where {
                it.id.eq(entry.id)
            }
        }
    }

    fun getFiles(entry: UDiskEntry, path: String = ""): List<FileRecord> {
        return ktormDatabase.from(Files).select().where {
            (Files.entryId eq entry.id) and (Files.parentDir eq path)
        }.map {
            FileRecord(
                entryId = it[Files.entryId] ?: "",
                relationFilePath = it[Files.filePath] ?: "",
                fileName = it[Files.fileName] ?: "",
                parentDir = it[Files.parentDir] ?: "",
                size = it[Files.size] ?: 0,
                createDate = it[Files.createDate] ?: 0,
                modifierDate = it[Files.modifierDate] ?: 0,
                status = it[Files.status] ?: 0
            )
        }
    }

    fun getFile(entry: UDiskEntry, relationFilePath: String): FileRecord? {
        return ktormDatabase.from(Files).select().where {
            (Files.entryId eq entry.id) and (Files.filePath eq relationFilePath)
        }.map {
            FileRecord(
                entryId = it[Files.entryId] ?: "",
                relationFilePath = it[Files.filePath] ?: "",
                fileName = it[Files.fileName] ?: "",
                parentDir = it[Files.parentDir] ?: "",
                size = it[Files.size] ?: 0,
                createDate = it[Files.createDate] ?: 0,
                modifierDate = it[Files.modifierDate] ?: 0,
                status = it[Files.status] ?: 0
            )
        }.firstOrNull()
    }

    fun getDirs(entry: UDiskEntry, path: String = ""): List<DirRecord> {
        return ktormDatabase.from(Dirs).select().where {
            (Dirs.entryId eq entry.id) and (Dirs.parentDir eq path)
        }.map {
            DirRecord(
                entryId = it[Dirs.entryId] ?: "",
                relationDirPath = it[Dirs.dirPath] ?: "",
                dirName = it[Dirs.fileName] ?: "",
                parentDir = it[Dirs.parentDir] ?: "",
                createDate = it[Dirs.createDate] ?: 0,
                modifierDate = it[Dirs.modifierDate] ?: 0,
            )
        }
    }

    fun getDir(entry: UDiskEntry, relationDirPath: String): DirRecord? {
        return ktormDatabase.from(Dirs).select().where {
            (Dirs.entryId eq entry.id) and (Dirs.dirPath eq relationDirPath)
        }.map {
            DirRecord(
                entryId = it[Dirs.entryId] ?: "",
                relationDirPath = it[Dirs.dirPath] ?: "",
                dirName = it[Dirs.fileName] ?: "",
                parentDir = it[Dirs.parentDir] ?: "",
                createDate = it[Dirs.createDate] ?: 0,
                modifierDate = it[Dirs.modifierDate] ?: 0,
            )
        }.firstOrNull()
    }

    suspend fun deepSeek(
        entry: UDiskEntry,
        path: String,
        seekFile: suspend (fileRecord: FileRecord) -> Unit,
        seekDir: suspend (dirRecord: DirRecord) -> Unit
    ) {
        // 获取当前路径下的文件
        val files = getFiles(entry, path)
        for (file in files) {
            seekFile(file)
        }

        // 获取当前路径下的目录并递归遍历
        val dirs = getDirs(entry, path)
        for (dir in dirs) {
            seekDir(dir)
            // 递归遍历子目录
            deepSeek(entry, dir.relationDirPath, seekFile, seekDir)
        }
    }

    fun UDiskEntry.exist(): Boolean {
        return ktormDatabase.from(Entrys).select().where { Entrys.id eq this.id }.map { true }.isNotEmpty()
    }

    class EntryWorkerImpl(
        private val entry: UDiskEntry
    ) : EntryWorker {
        override suspend fun seekFile(file: File) {
            if (file.isFile) {
                val recordStatus =
                    ktormDatabase.from(Files).select().where { Files.filePath eq (getRelationPath(entry.target, file)) }
                        .map {
                            it[Files.status]
                        }.firstOrNull()
                // 检查文件状态是否有效
                if (recordStatus == null) {
                    registerFile(entry, file)
                }
                if (recordStatus != 2) {
                    writeFile(entry, file) // 状态3表示异常，也需要重新写入
                } else {
                    ologger.info { "SKIPFile: $file" }
                }
            } else if (file.isDirectory) {
                val record =
                    ktormDatabase.from(Dirs).select().where { Dirs.dirPath eq (getRelationPath(entry.target, file)) }
                        .map {
                            it[Dirs.dirPath]
                        }.firstOrNull()

                if (record == null) {
                    writeDirInfo(entry, file)
                } else {
                    ologger.info { "SKIPDir: $file" }
                }
            }
        }
    }

}