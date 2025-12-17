package com.prike.analyticsmcpserver.tools

import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Анализатор SQLite баз данных
 */
class DatabaseAnalyzer(private val dbFile: File) {
    private val logger = LoggerFactory.getLogger(DatabaseAnalyzer::class.java)
    
    /**
     * Анализирует базу данных на основе запроса
     */
    fun analyze(query: String): String {
        val queryLower = query.lowercase()
        val result = StringBuilder()
        
        result.appendLine("Анализ базы данных: ${dbFile.name}")
        result.appendLine("=".repeat(50))
        
        return try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
                when {
                    queryLower.contains("таблиц") || queryLower.contains("table") -> {
                        result.appendLine("\n📋 Список таблиц:")
                        val tables = getTables(connection)
                        tables.forEach { table ->
                            result.appendLine("  - $table")
                        }
                        result.appendLine("\nВсего таблиц: ${tables.size}")
                    }
                    queryLower.startsWith("select") || queryLower.startsWith("SELECT") -> {
                        // Выполняем SQL запрос
                        result.appendLine("\n📊 Результат SQL запроса:")
                        result.appendLine(executeQuery(connection, query))
                    }
                    queryLower.contains("структур") || queryLower.contains("schema") -> {
                        result.appendLine("\n📋 Структура базы данных:")
                        val tables = getTables(connection)
                        tables.forEach { table ->
                            result.appendLine("\nТаблица: $table")
                            val columns = getTableColumns(connection, table)
                            columns.forEach { (name, type) ->
                                result.appendLine("  - $name: $type")
                            }
                        }
                    }
                    queryLower.contains("количеств") || queryLower.contains("count") -> {
                        result.appendLine("\n📈 Количество записей в таблицах:")
                        val tables = getTables(connection)
                        tables.forEach { table ->
                            val count = getTableCount(connection, table)
                            result.appendLine("  - $table: $count записей")
                        }
                    }
                    else -> {
                        // Общая информация
                        result.appendLine("\n💡 Общая информация:")
                        val tables = getTables(connection)
                        result.appendLine("  Таблиц: ${tables.size}")
                        result.appendLine("  Таблицы: ${tables.joinToString(", ")}")
                        
                        if (tables.isNotEmpty()) {
                            result.appendLine("\n📊 Количество записей:")
                            tables.forEach { table ->
                                val count = getTableCount(connection, table)
                                result.appendLine("  - $table: $count")
                            }
                        }
                    }
                }
                
                result.toString()
            }
        } catch (e: Exception) {
            "Error analyzing database: ${e.message}"
        }
    }
    
    /**
     * Получить сводку по базе данных
     */
    fun getSummary(): String {
        return try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
                val tables = getTables(connection)
                
                buildString {
                    appendLine("📊 Сводка по базе данных: ${dbFile.name}")
                    appendLine("=".repeat(50))
                    appendLine("Размер файла: ${dbFile.length()} байт")
                    appendLine("Количество таблиц: ${tables.size}")
                    if (tables.isNotEmpty()) {
                        appendLine("\nТаблицы:")
                        tables.forEach { table ->
                            val count = getTableCount(connection, table)
                            appendLine("  - $table: $count записей")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            "Error getting summary: ${e.message}"
        }
    }
    
    /**
     * Получить список таблиц
     */
    private fun getTables(connection: Connection): List<String> {
        val tables = mutableListOf<String>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            )
            while (rs.next()) {
                tables.add(rs.getString("name"))
            }
        }
        return tables
    }
    
    /**
     * Получить колонки таблицы
     */
    private fun getTableColumns(connection: Connection, tableName: String): List<Pair<String, String>> {
        val columns = mutableListOf<Pair<String, String>>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("PRAGMA table_info($tableName)")
            while (rs.next()) {
                columns.add(rs.getString("name") to rs.getString("type"))
            }
        }
        return columns
    }
    
    /**
     * Получить количество записей в таблице
     */
    private fun getTableCount(connection: Connection, tableName: String): Int {
        return try {
            connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) as count FROM $tableName")
                if (rs.next()) {
                    rs.getInt("count")
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to get count for table $tableName: ${e.message}")
            0
        }
    }
    
    /**
     * Выполнить SQL запрос
     */
    private fun executeQuery(connection: Connection, sql: String): String {
        return try {
            connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                formatResultSet(rs)
            }
        } catch (e: Exception) {
            "Error executing query: ${e.message}"
        }
    }
    
    /**
     * Форматирует ResultSet в строку
     */
    private fun formatResultSet(rs: ResultSet): String {
        val result = StringBuilder()
        val metaData = rs.metaData
        val columnCount = metaData.columnCount
        
        // Заголовки колонок
        result.appendLine("| " + (1..columnCount).joinToString(" | ") { metaData.getColumnName(it) } + " |")
        result.appendLine("|" + "-".repeat(columnCount * 15) + "|")
        
        // Данные (ограничиваем до 20 строк)
        var rowCount = 0
        while (rs.next() && rowCount < 20) {
            val row = (1..columnCount).joinToString(" | ") { 
                rs.getString(it)?.take(30) ?: "NULL"
            }
            result.appendLine("| $row |")
            rowCount++
        }
        
        if (rowCount == 20) {
            result.appendLine("\n... (показано только первые 20 строк)")
        }
        
        return result.toString()
    }
}

