package com.prike.mcpserver.data.repository

import com.prike.mcpserver.data.model.ChatMessage
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Репозиторий для чтения истории чата из memory.db (lesson-09)
 */
class ChatHistoryRepository(
    private val databasePath: String
) {
    private val logger = LoggerFactory.getLogger(ChatHistoryRepository::class.java)
    
    init {
        validateDatabase()
    }
    
    /**
     * Проверка существования базы данных
     */
    private fun validateDatabase() {
        val dbFile = resolveDatabasePath()
        if (!dbFile.exists()) {
            logger.warn("База данных не найдена: ${dbFile.absolutePath}")
        } else {
            logger.info("База данных найдена: ${dbFile.absolutePath}")
        }
    }
    
    /**
     * Разрешить путь к базе данных
     */
    private fun resolveDatabasePath(): File {
        val dbPath = databasePath
        val dbFile = File(dbPath)
        
        // Если путь абсолютный, используем его
        if (dbFile.isAbsolute) {
            return dbFile
        }
        
        // Если путь относительный, пытаемся найти относительно конфигурационного файла
        var currentDir = File(System.getProperty("user.dir"))
        
        // Если запущены из data-collection-mcp-server директории
        if (currentDir.name == "data-collection-mcp-server") {
            return File(currentDir, dbPath)
        }
        
        // Если запущены из корня урока
        if (currentDir.name == "lesson-14-orchestration") {
            return File(currentDir, dbPath)
        }
        
        // Ищем lesson-14-orchestration вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-14-orchestration") {
                return File(searchDir, dbPath)
            }
            
            val lessonDir = File(searchDir, "lesson-14-orchestration")
            if (lessonDir.exists()) {
                return File(lessonDir, dbPath)
            }
            
            searchDir = searchDir.parentFile
        }
        
        // Fallback: используем относительно текущей директории
        return dbFile
    }
    
    /**
     * Получить соединение с базой данных
     */
    private fun <T> withConnection(block: (Connection) -> T): T {
        val dbFile = resolveDatabasePath()
        val connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        return try {
            connection.autoCommit = false
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.close()
        }
    }
    
    /**
     * Получить сообщения за период времени
     * @param startTime начало периода (Unix timestamp в миллисекундах)
     * @param endTime конец периода (Unix timestamp в миллисекундах)
     * @return список сообщений
     */
    fun getMessagesBetween(startTime: Long, endTime: Long): List<ChatMessage> {
        return try {
            withConnection { connection ->
                connection.prepareStatement("""
                    SELECT id, role, content, timestamp, model
                    FROM memory_entries
                    WHERE timestamp >= ? AND timestamp <= ?
                    ORDER BY timestamp ASC
                """.trimIndent()).use { statement ->
                    statement.setLong(1, startTime)
                    statement.setLong(2, endTime)
                    statement.executeQuery().use { resultSet ->
                        val list = mutableListOf<ChatMessage>()
                        while (resultSet.next()) {
                            list.add(mapResultSetToMessage(resultSet))
                        }
                        list
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Ошибка получения сообщений из SQLite: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Преобразовать ResultSet в ChatMessage
     */
    private fun mapResultSetToMessage(resultSet: ResultSet): ChatMessage {
        return ChatMessage(
            id = resultSet.getString("id"),
            role = resultSet.getString("role"),
            content = resultSet.getString("content"),
            timestamp = resultSet.getLong("timestamp"),
            model = resultSet.getString("model")
        )
    }
}

