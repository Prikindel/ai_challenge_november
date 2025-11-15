package com.prike.data.repository

import com.prike.data.model.MemoryEntry
import com.prike.data.model.MemoryMetadata
import com.prike.data.model.MemoryStats
import com.prike.data.model.MessageRole
import com.prike.domain.repository.MemoryRepository
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant

/**
 * Реализация MemoryRepository для SQLite
 * 
 * Сохраняет записи памяти в SQLite базу данных
 * Обеспечивает персистентность данных между запусками приложения
 */
class SqliteMemoryRepository(
    private val databasePath: String
) : MemoryRepository {
    private val logger = LoggerFactory.getLogger(SqliteMemoryRepository::class.java)
    
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
                // Создаем таблицу для записей памяти
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS memory_entries (
                        id TEXT PRIMARY KEY,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        model TEXT,
                        prompt_tokens INTEGER,
                        completion_tokens INTEGER,
                        total_tokens INTEGER
                    )
                """.trimIndent())
                
                // Создаем индекс для быстрого поиска по времени
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_timestamp ON memory_entries(timestamp)
                """.trimIndent())
            }
            
            logger.info("База данных SQLite инициализирована: ${dbFile.absolutePath}")
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
    
    override suspend fun save(entry: MemoryEntry): Result<Unit> {
        return try {
            withConnection { connection ->
                connection.prepareStatement("""
                    INSERT OR REPLACE INTO memory_entries 
                    (id, role, content, timestamp, model, prompt_tokens, completion_tokens, total_tokens)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { statement ->
                    statement.setString(1, entry.id)
                    statement.setString(2, entry.role.name)
                    statement.setString(3, entry.content)
                    statement.setLong(4, entry.timestamp)
                    statement.setString(5, entry.metadata?.model)
                    statement.setObject(6, entry.metadata?.promptTokens)
                    statement.setObject(7, entry.metadata?.completionTokens)
                    statement.setObject(8, entry.metadata?.totalTokens)
                    
                    statement.executeUpdate()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Ошибка сохранения записи в SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun saveAll(entries: List<MemoryEntry>): Result<Unit> {
        if (entries.isEmpty()) {
            return Result.success(Unit)
        }
        
        return try {
            withConnection { connection ->
                connection.prepareStatement("""
                    INSERT OR REPLACE INTO memory_entries 
                    (id, role, content, timestamp, model, prompt_tokens, completion_tokens, total_tokens)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { statement ->
                    entries.forEach { entry ->
                        statement.setString(1, entry.id)
                        statement.setString(2, entry.role.name)
                        statement.setString(3, entry.content)
                        statement.setLong(4, entry.timestamp)
                        statement.setString(5, entry.metadata?.model)
                        statement.setObject(6, entry.metadata?.promptTokens)
                        statement.setObject(7, entry.metadata?.completionTokens)
                        statement.setObject(8, entry.metadata?.totalTokens)
                        statement.addBatch()
                    }
                    
                    statement.executeBatch()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Ошибка сохранения записей в SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun loadAll(): Result<List<MemoryEntry>> {
        return try {
            val entries = withConnection { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("""
                        SELECT id, role, content, timestamp, model, 
                               prompt_tokens, completion_tokens, total_tokens
                        FROM memory_entries
                        ORDER BY timestamp ASC
                    """.trimIndent()).use { resultSet ->
                        val list = mutableListOf<MemoryEntry>()
                        while (resultSet.next()) {
                            list.add(mapResultSetToEntry(resultSet))
                        }
                        list
                    }
                }
            }
            Result.success(entries)
        } catch (e: Exception) {
            logger.error("Ошибка загрузки записей из SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun findById(id: String): Result<MemoryEntry?> {
        return try {
            val entry = withConnection { connection ->
                connection.prepareStatement("""
                    SELECT id, role, content, timestamp, model, 
                           prompt_tokens, completion_tokens, total_tokens
                    FROM memory_entries
                    WHERE id = ?
                """.trimIndent()).use { statement ->
                    statement.setString(1, id)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            mapResultSetToEntry(resultSet)
                        } else {
                            null
                        }
                    }
                }
            }
            Result.success(entry)
        } catch (e: Exception) {
            logger.error("Ошибка поиска записи в SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun findByDateRange(start: Instant, end: Instant): Result<List<MemoryEntry>> {
        return try {
            val entries = withConnection { connection ->
                connection.prepareStatement("""
                    SELECT id, role, content, timestamp, model, 
                           prompt_tokens, completion_tokens, total_tokens
                    FROM memory_entries
                    WHERE timestamp >= ? AND timestamp <= ?
                    ORDER BY timestamp ASC
                """.trimIndent()).use { statement ->
                    statement.setLong(1, start.toEpochMilli())
                    statement.setLong(2, end.toEpochMilli())
                    statement.executeQuery().use { resultSet ->
                        val list = mutableListOf<MemoryEntry>()
                        while (resultSet.next()) {
                            list.add(mapResultSetToEntry(resultSet))
                        }
                        list
                    }
                }
            }
            Result.success(entries)
        } catch (e: Exception) {
            logger.error("Ошибка поиска записей по диапазону дат в SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun clear(): Result<Unit> {
        return try {
            withConnection { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("DELETE FROM memory_entries")
                }
            }
            logger.info("Память SQLite очищена")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Ошибка очистки памяти SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getStats(): Result<MemoryStats> {
        return try {
            val stats = withConnection { connection ->
                connection.createStatement().use { statement ->
                    // Общее количество записей
                    val totalEntries = statement.executeQuery("SELECT COUNT(*) as total FROM memory_entries").use { resultSet ->
                        if (resultSet.next()) resultSet.getInt("total") else 0
                    }
                    
                    // Количество сообщений пользователя
                    val userMessages = statement.executeQuery("""
                        SELECT COUNT(*) as count FROM memory_entries WHERE role = 'USER'
                    """.trimIndent()).use { resultSet ->
                        if (resultSet.next()) resultSet.getInt("count") else 0
                    }
                    
                    // Количество ответов ассистента
                    val assistantMessages = statement.executeQuery("""
                        SELECT COUNT(*) as count FROM memory_entries WHERE role = 'ASSISTANT'
                    """.trimIndent()).use { resultSet ->
                        if (resultSet.next()) resultSet.getInt("count") else 0
                    }
                    
                    // Самая старая запись
                    val oldestTimestamp = statement.executeQuery("""
                        SELECT MIN(timestamp) as oldest FROM memory_entries
                    """.trimIndent()).use { resultSet ->
                        if (resultSet.next() && resultSet.getObject("oldest") != null) {
                            Instant.ofEpochMilli(resultSet.getLong("oldest"))
                        } else null
                    }
                    
                    // Самая новая запись
                    val newestTimestamp = statement.executeQuery("""
                        SELECT MAX(timestamp) as newest FROM memory_entries
                    """.trimIndent()).use { resultSet ->
                        if (resultSet.next() && resultSet.getObject("newest") != null) {
                            Instant.ofEpochMilli(resultSet.getLong("newest"))
                        } else null
                    }
                    
                    MemoryStats(
                        totalEntries = totalEntries,
                        userMessages = userMessages,
                        assistantMessages = assistantMessages,
                        oldestEntry = oldestTimestamp,
                        newestEntry = newestTimestamp
                    )
                }
            }
            Result.success(stats)
        } catch (e: Exception) {
            logger.error("Ошибка получения статистики из SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Преобразовать ResultSet в MemoryEntry
     */
    private fun mapResultSetToEntry(resultSet: ResultSet): MemoryEntry {
        val metadata = if (resultSet.getObject("model") != null ||
            resultSet.getObject("prompt_tokens") != null ||
            resultSet.getObject("completion_tokens") != null ||
            resultSet.getObject("total_tokens") != null) {
            MemoryMetadata(
                model = resultSet.getString("model"),
                promptTokens = resultSet.getObject("prompt_tokens") as? Int,
                completionTokens = resultSet.getObject("completion_tokens") as? Int,
                totalTokens = resultSet.getObject("total_tokens") as? Int
            )
        } else {
            null
        }
        
        return MemoryEntry(
            id = resultSet.getString("id"),
            role = MessageRole.valueOf(resultSet.getString("role")),
            content = resultSet.getString("content"),
            timestamp = resultSet.getLong("timestamp"),
            metadata = metadata
        )
    }
}

