package io.github.octest.udtultra.repository

import io.github.octest.udtultra.Config
import io.github.octest.udtultra.repository.FileTreeManager.getFilePathHex16
import io.github.octest.udtultra.repository.FileTreeManager.getRelationPath
import io.github.octest.udtultra.utils.createTableIfNotExists
import io.github.octestx.basic.multiplatform.common.utils.RateLimitInputStream
import io.klogging.noCoLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.varchar
import java.io.File
import java.io.FileOutputStream

object UDTDatabase {
    private val ologger = noCoLogger<UDTDatabase>()
    val DBFile = File(Config.appDir, "db.db")
    private val ktormDatabase: Database by lazy {
        Database.connect(
            url = "jdbc:sqlite:${DBFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        ).apply {
            // 使用扩展函数创建表
            createTableIfNotExists(Entrys)
            createTableIfNotExists(Files)
            createTableIfNotExists(Dirs)
        }
    }
    suspend fun lockEntry(entry: DirTreeEntry, block: suspend EntryWorker.() -> Unit) {
        if (entry.exist().not()) {
            writeNewEntry(entry)
        }
        // 实现锁机制，这里简化处理
        synchronized(entry.id.intern()) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    EntryWorkerImpl(entry).apply {
                        block()
                    }
                }
            }
        }
    }

    fun writeNewEntry(entry: DirTreeEntry) {
        ktormDatabase.insert(Entrys) {
            set(it.id, entry.id)
            set(it.name, entry.name)
            set(it.totalSpace, entry.totalSpace)
            set(it.freeSpace, entry.freeSpace)
        }
    }

    interface EntryWorker {
        suspend fun seekFile(file: File)
    }

    fun writeFile(entry: DirTreeEntry, file: File) {
        ktormDatabase.update(Files) {
            set(it.status, 1)
            where {
                it.filePath.eq(getRelationPath(entry.target, file))
            }
        }
        ologger.info { "SavingFile: $file" }
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
        val targetFile = FileTreeManager.linkFile(entry, getFilePathHex16(entry.target, file))
        RateLimitInputStream(file.inputStream().apply { skipNBytes(targetFile.length()) }, Config.copySpeed).use { inputStream ->
            FileOutputStream(targetFile, true).use { outputStream ->
                inputStream.transferTo(outputStream)
            }
        }
        ologger.info { "SaveFileDone: $file" }
        ktormDatabase.update(Files) {
            set(it.status, 2)
            where {
                it.filePath.eq(getRelationPath(entry.target, file))
            }
        }
    }

    fun writeDirInfo(entry: DirTreeEntry, dir: File) {
        ktormDatabase.insert(Dirs) {
            set(it.entryId, entry.id)
            set(it.dirPath, getRelationPath(entry.target, dir))
            set(
                it.parentDir,
                getRelationPath(entry.target, dir.parentFile)
            ) // if equals to entry.target, parentDir is null
            set(it.fileName, dir.name)
            set(it.createDate, dir.lastModified())
            set(it.modifierDate, dir.lastModified())
        }
    }

    class EntryWorkerImpl(
        private val entry: DirTreeEntry
    ) : EntryWorker {
        override suspend fun seekFile(file: File) {
            if (file.isFile) {
                val recordStatus = ktormDatabase
                    .from(Files)
                    .select()
                    .where { Files.filePath eq (getRelationPath(entry.target, file)) }
                    .map {
                        it[Files.status]
                    }.firstOrNull()
                // 检查文件状态是否有效
                if (recordStatus == null || recordStatus != 2) {
                    writeFile(entry, file) // 状态3表示异常，也需要重新写入
                } else {
                    println("SKIPFile: $file")
                }
            } else if (file.isDirectory) {
                val record = ktormDatabase
                    .from(Dirs)
                    .select()
                    .where { Dirs.dirPath eq (getRelationPath(entry.target, file)) }
                    .map {
                        it[Dirs.dirPath]
                    }.firstOrNull()

                if (record == null) {
                    writeDirInfo(entry, file)
                } else {
                    println("SKIPDir: $file")
                }
            }
        }
    }

    fun getEntrys(): List<DirTreeEntry> {
        return ktormDatabase
            .from(Entrys)
            .select()
            .map {
                DirTreeEntry(
                    name = it[Entrys.name] ?: "",
                    target = File(it[Entrys.name] ?: ""),
                    id = it[Entrys.id] ?: "",
                    totalSpace = it[Entrys.totalSpace] ?: 0,
                    freeSpace = it[Entrys.freeSpace] ?: 0,
                )
            }
    }
    fun getFiles(entry: DirTreeEntry, path: String = ""): List<FileRecord> {
        return ktormDatabase
            .from(Files)
            .select()
            .where {
                (Files.entryId eq entry.id) and (Files.parentDir eq path)
            }
            .map {
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

    fun getDirs(entry: DirTreeEntry, path: String = ""): List<DirRecord> {
        return ktormDatabase
            .from(Dirs)
            .select()
            .where {
                (Dirs.entryId eq entry.id) and (Dirs.parentDir eq path)
            }
            .map {
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

    fun deepSeek(
        entry: DirTreeEntry,
        path: String,
        seekFile: (relationPath: String) -> Unit,
        seekDir: (relationPath: String) -> Unit
    ) {
        // 获取当前路径下的文件
        val files = getFiles(entry, path)
        for (file in files) {
            seekFile(file.relationFilePath)
        }

        // 获取当前路径下的目录并递归遍历
        val dirs = getDirs(entry, path)
        for (dir in dirs) {
            seekDir(dir.relationDirPath)
            // 递归遍历子目录
            deepSeek(entry, dir.relationDirPath, seekFile, seekDir)
        }
    }

    data class FileRecord(
        val entryId: String,
        val relationFilePath: String,
        val fileName: String,
        val parentDir: String?,
        val size: Long,
        val createDate: Long,
        val modifierDate: Long,
        val status: Int,
    )

    data class DirRecord(
        val entryId: String,
        val relationDirPath: String,
        val dirName: String,
        val parentDir: String?,
        val createDate: Long,
        val modifierDate: Long,
    )

    data class DirTreeEntry(
        val name: String,
        val target: File,
        val id: String,
        val totalSpace: Long,
        val freeSpace: Long,
    ) {
        fun exist(): Boolean {
            return ktormDatabase
                .from(Entrys)
                .select()
                .where { Entrys.id eq this.id }
                .map { true }
                .isNotEmpty()
        }
    }

    object Entrys : Table<Nothing>("entrys") {
        val id = varchar("id").primaryKey()
        val name = varchar("name")
        val totalSpace = long("totalSpace")
        val freeSpace = long("freeSpace")
    }

    object Files : Table<Nothing>("files") {
        val entryId = varchar("entryId")
        val filePath = varchar("filePath").primaryKey()
        val fileName = varchar("fileName")
        val parentDir = varchar("parentDir")
        val size = long("size")
        val createDate = long("createDate")
        val modifierDate = long("modifierDate")
        val status = int("status")
    }

    object Dirs : Table<Nothing>("dirs") {
        val entryId = varchar("entryId")
        val dirPath = varchar("dirPath").primaryKey()
        val fileName = varchar("dirName")
        val parentDir = varchar("parentDir")
        val createDate = long("createDate")
        val modifierDate = long("modifierDate")
    }
}