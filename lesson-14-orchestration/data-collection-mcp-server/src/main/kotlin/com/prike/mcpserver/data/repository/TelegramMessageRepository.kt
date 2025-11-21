package com.prike.mcpserver.data.repository

import com.prike.mcpserver.data.model.TelegramMessage
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Репозиторий для работы с сообщениями Telegram в БД
 */
class TelegramMessageRepository(
    private val databasePath: String
) {
    private val logger = LoggerFactory.getLogger(TelegramMessageRepository::class.java)
    
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
     * Получить сообщения за период времени для указанной группы
     * @param groupId ID группы
     * @param startTime начало периода (Unix timestamp в миллисекундах)
     * @param endTime конец периода (Unix timestamp в миллисекундах)
     * @return список сообщений
     */
    fun getMessagesBetween(groupId: String, startTime: Long, endTime: Long): List<TelegramMessage> {
        return try {
            withConnection { connection ->
                connection.prepareStatement("""
                    SELECT id, message_id, group_id, content, author, timestamp, created_at
                    FROM telegram_messages
                    WHERE group_id = ? AND timestamp >= ? AND timestamp <= ?
                    ORDER BY timestamp ASC
                """.trimIndent()).use { statement ->
                    statement.setString(1, groupId)
                    statement.setLong(2, startTime)
                    statement.setLong(3, endTime)
                    statement.executeQuery().use { resultSet ->
                        val list = mutableListOf<TelegramMessage>()
                        while (resultSet.next()) {
                            list.add(mapResultSetToMessage(resultSet))
                        }
                        list
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Ошибка получения сообщений Telegram из SQLite: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Преобразовать ResultSet в TelegramMessage
     */
    private fun mapResultSetToMessage(resultSet: ResultSet): TelegramMessage {
        return TelegramMessage(
            id = resultSet.getString("id"),
            messageId = resultSet.getLong("message_id"),
            groupId = resultSet.getString("group_id"),
            content = resultSet.getString("content"),
            author = resultSet.getString("author"),
            timestamp = resultSet.getLong("timestamp"),
            createdAt = resultSet.getLong("created_at")
        )
    }
}

