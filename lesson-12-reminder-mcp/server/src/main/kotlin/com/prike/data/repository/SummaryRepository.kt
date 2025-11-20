package com.prike.data.repository

import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Модель summary
 */
data class Summary(
    val id: String,
    val source: String,  // "web_chat", "telegram", "both"
    val periodStart: Long,  // Unix timestamp в миллисекундах
    val periodEnd: Long,  // Unix timestamp в миллисекундах
    val summaryText: String,
    val messageCount: Int = 0,
    val generatedAt: Long,  // Unix timestamp в миллисекундах
    val deliveredToTelegram: Boolean = false,
    val llmModel: String? = null
)

/**
 * Репозиторий для работы с summary в SQLite
 */
class SummaryRepository(
    private val databasePath: String
) {
    private val logger = LoggerFactory.getLogger(SummaryRepository::class.java)
    
    init {
        initializeDatabase()
    }
    
    /**
     * Инициализация базы данных
     */
    private fun initializeDatabase() {
        val dbFile = File(databasePath)
        val dbDir = dbFile.parentFile
        if (dbDir != null && !dbDir.exists()) {
            dbDir.mkdirs()
        }
        
        withConnection { connection ->
            connection.createStatement().use { statement ->
                // Создаем таблицу summaries
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS summaries (
                        id TEXT PRIMARY KEY,
                        source TEXT NOT NULL,
                        period_start INTEGER NOT NULL,
                        period_end INTEGER NOT NULL,
                        summary_text TEXT NOT NULL,
                        message_count INTEGER NOT NULL DEFAULT 0,
                        generated_at INTEGER NOT NULL,
                        delivered_to_telegram BOOLEAN DEFAULT 0,
                        llm_model TEXT
                    )
                """.trimIndent())
                
                // Создаем индексы
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_summaries_generated_at 
                    ON summaries(generated_at)
                """.trimIndent())
                
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_summaries_source 
                    ON summaries(source)
                """.trimIndent())
            }
            
            logger.info("Summary database initialized: ${dbFile.absolutePath}")
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
     * Сохранить summary
     */
    suspend fun save(summary: Summary): Result<Unit> {
        return try {
            withConnection { connection ->
                connection.prepareStatement("""
                    INSERT OR REPLACE INTO summaries 
                    (id, source, period_start, period_end, summary_text, message_count, 
                     generated_at, delivered_to_telegram, llm_model)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { statement ->
                    statement.setString(1, summary.id)
                    statement.setString(2, summary.source)
                    statement.setLong(3, summary.periodStart)
                    statement.setLong(4, summary.periodEnd)
                    statement.setString(5, summary.summaryText)
                    statement.setInt(6, summary.messageCount)
                    statement.setLong(7, summary.generatedAt)
                    statement.setBoolean(8, summary.deliveredToTelegram)
                    statement.setString(9, summary.llmModel)
                    
                    statement.executeUpdate()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Error saving summary: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить все summary, отсортированные по времени генерации (новые первыми)
     */
    suspend fun getAll(limit: Int = 100): Result<List<Summary>> {
        return try {
            val summaries = withConnection { connection ->
                connection.prepareStatement("""
                    SELECT id, source, period_start, period_end, summary_text, message_count,
                           generated_at, delivered_to_telegram, llm_model
                    FROM summaries
                    ORDER BY generated_at DESC
                    LIMIT ?
                """.trimIndent()).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use { resultSet ->
                        val list = mutableListOf<Summary>()
                        while (resultSet.next()) {
                            list.add(mapResultSetToSummary(resultSet))
                        }
                        list
                    }
                }
            }
            Result.success(summaries)
        } catch (e: Exception) {
            logger.error("Error loading summaries: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить summary по ID
     */
    suspend fun findById(id: String): Result<Summary?> {
        return try {
            val summary = withConnection { connection ->
                connection.prepareStatement("""
                    SELECT id, source, period_start, period_end, summary_text, message_count,
                           generated_at, delivered_to_telegram, llm_model
                    FROM summaries
                    WHERE id = ?
                """.trimIndent()).use { statement ->
                    statement.setString(1, id)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            mapResultSetToSummary(resultSet)
                        } else {
                            null
                        }
                    }
                }
            }
            Result.success(summary)
        } catch (e: Exception) {
            logger.error("Error finding summary: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Отметить summary как отправленное в Telegram
     */
    suspend fun markAsDelivered(id: String): Result<Unit> {
        return try {
            withConnection { connection ->
                connection.prepareStatement("""
                    UPDATE summaries 
                    SET delivered_to_telegram = 1
                    WHERE id = ?
                """.trimIndent()).use { statement ->
                    statement.setString(1, id)
                    statement.executeUpdate()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Error marking summary as delivered: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Преобразовать ResultSet в Summary
     */
    private fun mapResultSetToSummary(resultSet: java.sql.ResultSet): Summary {
        return Summary(
            id = resultSet.getString("id"),
            source = resultSet.getString("source"),
            periodStart = resultSet.getLong("period_start"),
            periodEnd = resultSet.getLong("period_end"),
            summaryText = resultSet.getString("summary_text"),
            messageCount = resultSet.getInt("message_count"),
            generatedAt = resultSet.getLong("generated_at"),
            deliveredToTelegram = resultSet.getBoolean("delivered_to_telegram"),
            llmModel = resultSet.getString("llm_model")
        )
    }
}

