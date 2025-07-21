package io.github.octest.udtultra.repository.database

import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.varchar

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