package io.github.octest.udtultra.utils

import io.klogging.noCoLogger
import org.ktorm.database.Database
import org.ktorm.schema.Table

/**
 * 按规范创建数据库表的扩展函数
 * 使用CREATE TABLE IF NOT EXISTS语法
 */
private val ologger = noCoLogger("KtormUtils")
fun Database.createTableIfNotExists(table: Table<*>): Boolean {
    return useConnection { conn ->
        val createTableSql =
            "CREATE TABLE IF NOT EXISTS ${table.tableName} (${table.columns.joinToString { "${it.name} ${it.sqlType.typeName}" }})"
        ologger.debug { "Create-Table-SQL: $createTableSql" }
        val result = conn.prepareStatement(createTableSql).execute()
        result
    }
}