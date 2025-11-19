package com.prike.mcpserver.data.repository

import com.prike.mcpserver.data.model.TelegramMessage
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Репозиторий для работы с сообщениями Telegram в БД
 * Создает таблицу telegram_messages в summary.db
 */
class TelegramMessageRepository(
    private val databasePath: String
) {
    private val logger = LoggerFactory.getLogger(TelegramMessageRepository::class.java)
    
    init {
        initializeDatabase()
    }
    
    /**
     * Инициализация базы данных
     * Создает таблицу, если её нет
     */
    private fun initializeDatabase() {
        val dbFile = File(databasePath)
        val dbDir = dbFile.parentFile
        if (dbDir != null && !dbDir.exists()) {
            dbDir.mkdirs()
        }
        
        withConnection { connection ->
            connection.createStatement().use { statement ->
                // Создаем таблицу для сообщений Telegram
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS telegram_messages (
                        id TEXT PRIMARY KEY,
                        message_id INTEGER NOT NULL,
                        group_id TEXT NOT NULL,
                        content TEXT NOT NULL,
                        author TEXT,
                        timestamp INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Создаем индексы для быстрого поиска
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_telegram_timestamp 
                    ON telegram_messages(timestamp)
                """.trimIndent())
                
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_telegram_group 
                    ON telegram_messages(group_id)
                """.trimIndent())
            }
            
            logger.info("База данных Telegram сообщений инициализирована: ${dbFile.absolutePath}")
        }
    }
    
    /**
     * Получить соединение с базой данных
     */
    private fun <T> withConnection(block: (Connection) -> T): T {
        val connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")
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
     * Сохранить сообщение в БД
     */
    fun save(message: TelegramMessage): Result<Unit> {
        return try {
            withConnection { connection ->
                connection.prepareStatement("""
                    INSERT OR REPLACE INTO telegram_messages 
                    (id, message_id, group_id, content, author, timestamp, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { statement ->
                    statement.setString(1, message.id)
                    statement.setLong(2, message.messageId)
                    statement.setString(3, message.groupId)
                    statement.setString(4, message.content)
                    statement.setString(5, message.author)
                    statement.setLong(6, message.timestamp)
                    statement.setLong(7, message.createdAt)
                    
                    statement.executeUpdate()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Ошибка сохранения сообщения Telegram в SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Сохранить несколько сообщений
     */
    fun saveAll(messages: List<TelegramMessage>): Result<Unit> {
        if (messages.isEmpty()) {
            return Result.success(Unit)
        }
        
        return try {
            withConnection { connection ->
                connection.prepareStatement("""
                    INSERT OR REPLACE INTO telegram_messages 
                    (id, message_id, group_id, content, author, timestamp, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { statement ->
                    messages.forEach { message ->
                        statement.setString(1, message.id)
                        statement.setLong(2, message.messageId)
                        statement.setString(3, message.groupId)
                        statement.setString(4, message.content)
                        statement.setString(5, message.author)
                        statement.setLong(6, message.timestamp)
                        statement.setLong(7, message.createdAt)
                        statement.addBatch()
                    }
                    
                    statement.executeBatch()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Ошибка сохранения сообщений Telegram в SQLite: ${e.message}", e)
            Result.failure(e)
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

