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

data class UDiskEntry(
    val name: String,
    val target: File,
    val id: String,
    val totalSpace: Long,
    val freeSpace: Long,
    // common; disable; key; master
    // common 是普通u盘，需要复制
    // disable 是禁止复制的u盘
    // key 是密钥u盘, 插入u盘后才能进入图形化界面, 插入时会检测是否有数据被复制，并且销毁复制的数据; 根目录需要有.udtUltraKeyUDisk(并赋予隐藏属性)文件会被自动识别
    // master 是主u盘，插入u盘后才能进入图形化界面, 并且数据会复制到这个u盘的UDTUltraMaster文件夹中; 插入时会检测是否有数据被复制，并且销毁复制的数据; 根目录需要有.udtUltraMasterUDisk(并赋予隐藏属性)文件会被自动识别
    val type: String,
) {
    companion object {
        enum class Type(val value: String) {
            COMMON("common"),
            DISABLE("disable"),
            KEY("key"),
            MASTER("master")
        }
    }
}