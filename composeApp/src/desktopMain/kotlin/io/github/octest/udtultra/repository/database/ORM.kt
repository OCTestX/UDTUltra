package io.github.octest.udtultra.repository.database

import java.io.File

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
)