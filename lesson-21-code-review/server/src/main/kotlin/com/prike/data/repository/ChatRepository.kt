package com.prike.data.repository

import com.prike.domain.model.ChatMessage
import com.prike.domain.model.ChatSession
import com.prike.domain.model.MessageRole
import com.prike.domain.model.Citation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Репозиторий для работы с историей чата (SQLite)
 */
class ChatRepository(
    private val databasePath: String
) {
    private val logger = LoggerFactory.getLogger(ChatRepository::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    init {
        // Создаём директорию для базы данных, если её нет
        val dbFile = File(databasePath)
        dbFile.parentFile?.mkdirs()
        
        // Инициализируем схему БД
        initializeDatabase()
    }
    
    /**
     * Инициализирует схему базы данных для чата
     */
    private fun initializeDatabase() {
        withConnection { connection ->
            // Таблица сессий чата
            connection.createStatement().executeUpdate("""
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            
            // Таблица сообщений чата
            connection.createStatement().executeUpdate("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    citations TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Индексы для быстрого поиска
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_messages_session 
                ON chat_messages(session_id)
            """.trimIndent())
            
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_messages_created 
                ON chat_messages(created_at)
            """.trimIndent())
            
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_sessions_updated 
                ON chat_sessions(updated_at)
            """.trimIndent())
            
            logger.info("Chat database schema initialized")
        }
    }
    
    /**
     * Создаёт новую сессию чата
     */
    fun createSession(title: String? = null): ChatSession {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        withConnection { connection ->
            val sql = """
                INSERT INTO chat_sessions (id, title, created_at, updated_at)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setString(2, title)
                stmt.setLong(3, now)
                stmt.setLong(4, now)
                stmt.executeUpdate()
            }
        }
        
        return ChatSession(
            id = sessionId,
            title = title,
            createdAt = now,
            updatedAt = now
        )
    }
    
    /**
     * Получает сессию по ID
     */
    fun getSession(sessionId: String): ChatSession? {
        return withConnection { connection ->
            val sql = """
                SELECT id, title, created_at, updated_at
                FROM chat_sessions
                WHERE id = ?
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sessionId)
                val rs = stmt.executeQuery()
                
                if (rs.next()) {
                    ChatSession(
                        id = rs.getString("id"),
                        title = rs.getString("title"),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at")
                    )
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * Получает список всех сессий, отсортированных по дате обновления
     */
    fun getAllSessions(): List<ChatSession> {
        return withConnection { connection ->
            val sql = """
                SELECT id, title, created_at, updated_at
                FROM chat_sessions
                ORDER BY updated_at DESC
            """.trimIndent()
            
            val sessions = mutableListOf<ChatSession>()
            connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    sessions.add(
                        ChatSession(
                            id = rs.getString("id"),
                            title = rs.getString("title"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at")
                        )
                    )
                }
            }
            sessions
        }
    }
    
    /**
     * Обновляет сессию (обновляет updated_at и опционально title)
     */
    fun updateSession(sessionId: String, title: String? = null) {
        withConnection { connection ->
            val sql = if (title != null) {
                """
                    UPDATE chat_sessions
                    SET title = ?, updated_at = ?
                    WHERE id = ?
                """.trimIndent()
            } else {
                """
                    UPDATE chat_sessions
                    SET updated_at = ?
                    WHERE id = ?
                """.trimIndent()
            }
            
            connection.prepareStatement(sql).use { stmt ->
                if (title != null) {
                    stmt.setString(1, title)
                    stmt.setLong(2, System.currentTimeMillis())
                    stmt.setString(3, sessionId)
                } else {
                    stmt.setLong(1, System.currentTimeMillis())
                    stmt.setString(2, sessionId)
                }
                stmt.executeUpdate()
            }
        }
    }
    
    /**
     * Удаляет сессию и все её сообщения
     */
    fun deleteSession(sessionId: String) {
        withConnection { connection ->
            val sql = """
                DELETE FROM chat_sessions
                WHERE id = ?
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.executeUpdate()
            }
        }
    }
    
    /**
     * Сохраняет сообщение в историю
     */
    fun saveMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        citations: List<Citation> = emptyList()
    ): ChatMessage {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        // Обновляем время последнего обновления сессии
        updateSession(sessionId)
        
        withConnection { connection ->
            val sql = """
                INSERT INTO chat_messages (id, session_id, role, content, citations, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            val citationsJson = if (citations.isNotEmpty()) {
                json.encodeToString(citations)
            } else {
                null
            }
            
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, messageId)
                stmt.setString(2, sessionId)
                stmt.setString(3, role.name)
                stmt.setString(4, content)
                stmt.setString(5, citationsJson)
                stmt.setLong(6, now)
                stmt.executeUpdate()
            }
        }
        
        return ChatMessage(
            id = messageId,
            sessionId = sessionId,
            role = role,
            content = content,
            citations = citations,
            createdAt = now
        )
    }
    
    /**
     * Получает историю сообщений для сессии
     */
    fun getHistory(sessionId: String, limit: Int? = null): List<ChatMessage> {
        return withConnection { connection ->
            val sql = if (limit != null) {
                """
                    SELECT id, session_id, role, content, citations, created_at
                    FROM chat_messages
                    WHERE session_id = ?
                    ORDER BY created_at ASC
                    LIMIT ?
                """.trimIndent()
            } else {
                """
                    SELECT id, session_id, role, content, citations, created_at
                    FROM chat_messages
                    WHERE session_id = ?
                    ORDER BY created_at ASC
                """.trimIndent()
            }
            
            val messages = mutableListOf<ChatMessage>()
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sessionId)
                if (limit != null) {
                    stmt.setInt(2, limit)
                }
                val rs = stmt.executeQuery()
                
                while (rs.next()) {
                    val citationsJson = rs.getString("citations")
                    val citations = if (citationsJson != null && citationsJson.isNotEmpty()) {
                        try {
                            json.decodeFromString<List<Citation>>(citationsJson)
                        } catch (e: Exception) {
                            logger.warn("Failed to parse citations for message ${rs.getString("id")}: ${e.message}")
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    
                    messages.add(
                        ChatMessage(
                            id = rs.getString("id"),
                            sessionId = rs.getString("session_id"),
                            role = MessageRole.valueOf(rs.getString("role")),
                            content = rs.getString("content"),
                            citations = citations,
                            createdAt = rs.getLong("created_at")
                        )
                    )
                }
            }
            messages
        }
    }
    
    /**
     * Выполняет операцию с подключением к БД
     */
    private fun <T> withConnection(block: (Connection) -> T): T {
        DriverManager.getConnection("jdbc:sqlite:$databasePath").use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                return result
            } catch (e: Exception) {
                connection.rollback()
                logger.error("Database operation failed: ${e.message}", e)
                throw e
            }
        }
    }
}

