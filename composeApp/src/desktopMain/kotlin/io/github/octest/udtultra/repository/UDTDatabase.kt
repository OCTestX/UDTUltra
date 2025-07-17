package io.github.octest.udtultra.repository

import io.github.octest.udtultra.Config
import io.github.octest.udtultra.repository.FileTreeManager.getDirPathHex16
import io.github.octest.udtultra.repository.FileTreeManager.getFilePathHex16
import io.github.octestx.basic.multiplatform.common.utils.RateLimitInputStream
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

    /**
     * 按规范创建数据库表的扩展函数
     * 使用CREATE TABLE IF NOT EXISTS语法
     */
    fun Database.createTableIfNotExists(table: Table<*>): Boolean {
        return useConnection { conn ->
            val createTableSql = "CREATE TABLE IF NOT EXISTS ${table.tableName} (${table.columns.joinToString { "${it.name} ${it.sqlType.typeName}" }})"
            println("Create-Table-SQL: $createTableSql")
            val result = conn.prepareStatement(createTableSql).execute()
            result
        }
    }

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
        suspend fun addFile(file: File)
    }

    class EntryWorkerImpl(
        private val entry: DirTreeEntry
    ) : EntryWorker {
        override suspend fun addFile(file: File) {
            if (file.isFile) {
                val filePathHex16 = getFilePathHex16(file)
                val recordStatus = ktormDatabase
                    .from(Files)
                    .select()
                    .where { Files.filePathHex16 eq filePathHex16.joinToString("") }
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
                val dirPathHex16 = getDirPathHex16(file)
                println("DIR-HEX: $dirPathHex16")
                val record = ktormDatabase
                    .from(Dirs)
                    .select()
                    .where { Dirs.dirPathHex16 eq dirPathHex16.joinToString("") }
                    .map {
                        it[Dirs.dirPathHex16]
                    }.firstOrNull()

                if (record == null) {
                    writeDirInfo(file)
                } else {
                    println("SKIPDir: $file")
                }
            }
        }
    }

    fun writeFile(entry: DirTreeEntry, file: File) {
        ktormDatabase.update(Files) {
            set(it.status, 1)
            where {
                it.filePathHex16.eq(getFilePathHex16(file).joinToString(""))
            }
        }
        println("SavingFile: $file")
        ktormDatabase.insert(Files) {
            set(it.entryId, file.parentFile?.absolutePath ?: "")
            set(it.filePathHex16, getFilePathHex16(file).joinToString(""))
            set(it.fileName, file.name)
            set(it.size, file.length())
            set(it.createDate, file.lastModified())
            set(it.modifierData, file.lastModified())
            set(it.status, 0) // status 设置为 0
        }
        val targetFile = FileTreeManager.getFile(entry, getFilePathHex16(file))
        RateLimitInputStream(file.inputStream().apply { skipNBytes(targetFile.length()) }, Config.copySpeed).use { inputStream ->
            FileOutputStream(targetFile, true).use { outputStream ->
                inputStream.transferTo(outputStream)
            }
        }
        println("SaveFileDone: $file")
        ktormDatabase.update(Files) {
            set(it.status, 2)
            where {
                it.filePathHex16.eq(getFilePathHex16(file).joinToString(""))
            }
        }
    }

    fun writeDirInfo(dir: File) {
        ktormDatabase.insert(Dirs) {
            set(it.entryId, dir.absolutePath)
            set(it.dirPathHex16, getDirPathHex16(dir).joinToString(""))
            set(it.fileName, dir.name)
            set(it.createDate, dir.lastModified())
            set(it.modifierData, dir.lastModified())
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
        val filePathHex16 = varchar("filePathHex16").primaryKey()
        val fileName = varchar("fileName")
        val size = long("size")
        val createDate = long("createDate")
        val modifierData = long("modifierData")
        val status = int("status")
    }

    object Dirs : Table<Nothing>("dirs") {
        val entryId = varchar("entryId")
        val dirPathHex16 = varchar("dirPathHex16").primaryKey()
        val fileName = varchar("fileName")
        val createDate = long("createDate")
        val modifierData = long("modifierData")
    }
}