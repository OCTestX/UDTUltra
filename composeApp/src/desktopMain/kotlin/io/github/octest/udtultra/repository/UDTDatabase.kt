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

/**
 * UDTDatabase 是一个用于管理 UDiskEntry 及其相关文件和目录信息的数据库操作对象。
 * 它使用 SQLite 数据库存储数据，并通过 Ktorm 框架进行数据库访问。
 */
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

    /**
     * 在指定的 UDiskEntry 上运行一个操作块。
     *
     * @param entry 要操作的 UDiskEntry 实例。
     * @param block 要执行的操作块，该块将在 EntryWorker 上下文中执行。
     */
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

    /**
     * 将一个新的 UDiskEntry 写入数据库。
     *
     * @param entry 要写入的 UDiskEntry 实例。
     */
    suspend fun writeNewEntry(entry: UDiskEntry) {
        ktormDatabase.insert(Entrys) {
            set(it.id, entry.id)
            set(it.name, entry.name)
            set(it.totalSpace, entry.totalSpace)
            set(it.freeSpace, entry.freeSpace)
            set(it.type, entry.type)
        }
    }

    /**
     * EntryWorker 接口定义了在 UDiskEntry 上可以执行的操作。
     */
    interface EntryWorker {
        /**
         * 保存一个文件到数据库中。
         *
         * @param file 要保存的文件。
         */
        suspend fun saveFile(file: File)
    }

    /**
     * 注册一个文件到数据库中。
     *
     * @param entry 与文件关联的 UDiskEntry。
     * @param file 要注册的文件。
     */
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

    /**
     * 将文件内容写入目标位置，并更新数据库中的状态。
     *
     * @param entry 与文件关联的 UDiskEntry。
     * @param file 要写入的文件。
     */
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
                        ologger.info { "TARGET: $targetFile" }
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

    /**
     * 将目录信息写入数据库。
     *
     * @param entry 与目录关联的 UDiskEntry。
     * @param dir 要写入的目录。
     */
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

    /**
     * 获取所有 UDiskEntry。
     *
     * @return 包含所有 UDiskEntry 的列表。
     */
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

    /**
     * 根据 ID 获取一个 UDiskEntry。
     *
     * @param id 要获取的 UDiskEntry 的 ID。
     * @return 对应的 UDiskEntry 实例，如果不存在则返回 null。
     */
    fun getEntry(id: String): UDiskEntry? {
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
        }.firstOrNull()
    }

    /**
     * 检查文件是否被禁止。
     *
     * @param entryId 与文件关联的 UDiskEntry 的 ID。
     * @param relationFilePath 文件的相对路径。
     * @return 如果文件未被禁止，则返回 true；否则返回 false。
     */
    suspend fun fileIsBaned(entryId: String, relationFilePath: String): Boolean {
        return ktormDatabase.from(BanedFiles).select()
            .where { (BanedFiles.entryId eq entryId) and (BanedFiles.filePath eq relationFilePath) }.map { it }
            .firstOrNull() == null
    }

    /**
     * 更改文件的禁止状态。
     *
     * @param entry 与文件关联的 UDiskEntry。
     * @param relationFilePath 文件的相对路径。
     * @param baned 是否禁止该文件。
     */
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

    /**
     * 检查目录是否被禁止。
     *
     * @param entryId 与目录关联的 UDiskEntry 的 ID。
     * @param relationDirPath 目录的相对路径。
     * @return 如果目录未被禁止，则返回 true；否则返回 false。
     */
    suspend fun dirIsBaned(entryId: String, relationDirPath: String): Boolean {
        return ktormDatabase.from(BanedDirs).select()
            .where { (BanedDirs.entryId eq entryId) and (BanedDirs.dirPath eq relationDirPath) }.map { it }
            .firstOrNull() == null
    }

    /**
     * 更改目录的禁止状态。
     *
     * @param entry 与目录关联的 UDiskEntry。
     * @param relationFilePath 目录的相对路径。
     * @param baned 是否禁止该目录。
     */
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

    /**
     * 更改 UDiskEntry 的名称。
     *
     * @param entry 要更改名称的 UDiskEntry。
     * @param name 新的名称。
     */
    fun changeUDiskName(entry: UDiskEntry, name: String) {
        ktormDatabase.update(Entrys) {
            set(it.name, name)
            where {
                it.id.eq(entry.id)
            }
        }
    }

    /**
     * 更改 UDiskEntry 的类型。
     *
     * @param entry 要更改类型的 UDiskEntry。
     * @param type 新的类型。
     */
    fun changeUDiskType(entry: UDiskEntry, type: UDiskEntry.Companion.Type) {
        ktormDatabase.update(Entrys) {
            set(it.type, type.value)
            where {
                it.id.eq(entry.id)
            }
        }
    }

    /**
     * 获取指定 UDiskEntry 下的文件列表。
     *
     * @param entry 与文件关联的 UDiskEntry。
     * @param path 相对路径，默认为空字符串。
     * @return 包含文件记录的列表。
     */
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

    /**
     * 获取指定 UDiskEntry 下的禁止文件列表。
     *
     * @param entry 与文件关联的 UDiskEntry。
     * @return 包含禁止文件记录的列表。
     */
    fun getBanedFiles(entry: UDiskEntry): List<BanedFileRecord> {
        return ktormDatabase.from(BanedFiles).select().where {
            (BanedFiles.entryId eq entry.id)
        }.map {
            BanedFileRecord(
                entryId = it[BanedFiles.entryId] ?: "",
                filePath = it[BanedFiles.filePath] ?: "",
                fileName = it[BanedFiles.fileName] ?: "",
                parentDir = it[BanedFiles.parentDir] ?: "",
                size = it[BanedFiles.size] ?: 0
            )
        }
    }

    /**
     * 获取指定 UDiskEntry 下的禁止目录列表。
     *
     * @param entry 与目录关联的 UDiskEntry。
     * @return 包含禁止目录记录的列表。
     */
    fun getBanedDirs(entry: UDiskEntry): List<BanedDirRecord> {
        return ktormDatabase.from(BanedDirs).select().where {
            (BanedDirs.entryId eq entry.id)
        }.map {
            BanedDirRecord(
                entryId = it[BanedDirs.entryId] ?: "",
                dirPath = it[BanedDirs.dirPath] ?: "",
                dirName = it[BanedDirs.dirName] ?: "",
                parentDir = it[BanedDirs.parentDir] ?: ""
            )
        }
    }

    /**
     * 获取指定 UDiskEntry 和相对路径下的文件记录。
     *
     * @param entry 与文件关联的 UDiskEntry。
     * @param relationFilePath 文件的相对路径。
     * @return 对应的文件记录，如果不存在则返回 null。
     */
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

    /**
     * 获取指定 UDiskEntry 下的目录列表。
     *
     * @param entry 与目录关联的 UDiskEntry。
     * @param path 相对路径，默认为空字符串。
     * @return 包含目录记录的列表。
     */
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

    /**
     * 获取指定 UDiskEntry 和相对路径下的目录记录。
     *
     * @param entry 与目录关联的 UDiskEntry。
     * @param relationDirPath 目录的相对路径。
     * @return 对应的目录记录，如果不存在则返回 null。
     */
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

    /**
     * 深度遍历指定路径下的文件和目录。
     *
     * @param entry 与路径关联的 UDiskEntry。
     * @param path 要遍历的路径。
     * @param seekFile 遍历到文件时调用的回调函数。
     * @param seekDir 遍历到目录时调用的回调函数。
     */
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

    /**
     * 检查 UDiskEntry 是否存在于数据库中。
     *
     * @return 如果存在则返回 true，否则返回 false。
     */
    fun UDiskEntry.exist(): Boolean {
        return ktormDatabase.from(Entrys).select().where { Entrys.id eq this.id }.map { true }.isNotEmpty()
    }

    /**
     * EntryWorkerImpl 是 EntryWorker 接口的一个实现类。
     *
     * @param entry 与 EntryWorker 关联的 UDiskEntry。
     */
    class EntryWorkerImpl(
        private val entry: UDiskEntry
    ) : EntryWorker {
        /**
         * 保存一个文件或目录到数据库中。
         *
         * @param file 要保存的文件或目录。
         */
        override suspend fun saveFile(file: File) {
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
