package com.prike.data.repository

import com.prike.domain.model.ChatMessage
import com.prike.domain.model.ChatSession
import com.prike.domain.model.MessageRole
import com.prike.domain.model.Citation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Таблицы для чата
 */
object ChatSessionsTable : Table("chat_sessions") {
    val id = text("id")
    val title = text("title").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    
    override val primaryKey = PrimaryKey(id)
}

object ChatMessagesTable : Table("chat_messages") {
    val id = text("id")
    val sessionId = text("session_id").references(ChatSessionsTable.id, onDelete = ReferenceOption.CASCADE)
    val role = text("role")
    val content = text("content")
    val citations = text("citations").nullable()
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        index(false, sessionId)
        index(false, createdAt)
    }
}

/**
 * Репозиторий для работы с историей чата
 */
class ChatRepository(
    private val database: Database
) {
    private val logger = LoggerFactory.getLogger(ChatRepository::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    init {
        // Инициализируем схему БД
        initializeDatabase()
    }
    
    /**
     * Инициализирует схему базы данных для чата
     */
    private fun initializeDatabase() {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                ChatSessionsTable,
                ChatMessagesTable
            )
        }
        logger.info("Chat database schema initialized")
    }
    
    /**
     * Создаёт новую сессию чата
     */
    fun createSession(title: String? = null): ChatSession {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        transaction(database) {
            ChatSessionsTable.insert {
                it[ChatSessionsTable.id] = sessionId
                it[ChatSessionsTable.title] = title
                it[ChatSessionsTable.createdAt] = now
                it[ChatSessionsTable.updatedAt] = now
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
        return transaction(database) {
            ChatSessionsTable
                .select { ChatSessionsTable.id eq sessionId }
                .firstOrNull()
                ?.let {
                    ChatSession(
                        id = it[ChatSessionsTable.id],
                        title = it[ChatSessionsTable.title],
                        createdAt = it[ChatSessionsTable.createdAt],
                        updatedAt = it[ChatSessionsTable.updatedAt]
                    )
                }
        }
    }
    
    /**
     * Получает список всех сессий, отсортированных по дате обновления
     */
    fun getAllSessions(): List<ChatSession> {
        return transaction(database) {
            ChatSessionsTable
                .selectAll()
                .orderBy(ChatSessionsTable.updatedAt to SortOrder.DESC)
                .map {
                    ChatSession(
                        id = it[ChatSessionsTable.id],
                        title = it[ChatSessionsTable.title],
                        createdAt = it[ChatSessionsTable.createdAt],
                        updatedAt = it[ChatSessionsTable.updatedAt]
                    )
                }
        }
    }
    
    /**
     * Обновляет сессию (обновляет updated_at и опционально title)
     */
    fun updateSession(sessionId: String, title: String? = null) {
        transaction(database) {
            if (title != null) {
                ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
                    it[ChatSessionsTable.title] = title
                    it[ChatSessionsTable.updatedAt] = System.currentTimeMillis()
                }
            } else {
                ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
                    it[ChatSessionsTable.updatedAt] = System.currentTimeMillis()
                }
            }
        }
    }
    
    /**
     * Удаляет сессию и все её сообщения
     */
    fun deleteSession(sessionId: String) {
        transaction(database) {
            // Удаляем сообщения сначала (каскадное удаление должно работать, но на всякий случай)
            // Используем SQL напрямую через Exposed
            exec("DELETE FROM chat_messages WHERE session_id = '$sessionId'")
            // Затем удаляем сессию
            exec("DELETE FROM chat_sessions WHERE id = '$sessionId'")
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
        
        transaction(database) {
            val citationsJson = if (citations.isNotEmpty()) {
                json.encodeToString(citations)
            } else {
                null
            }
            
            ChatMessagesTable.insert {
                it[ChatMessagesTable.id] = messageId
                it[ChatMessagesTable.sessionId] = sessionId
                it[ChatMessagesTable.role] = role.name
                it[ChatMessagesTable.content] = content
                it[ChatMessagesTable.citations] = citationsJson
                it[ChatMessagesTable.createdAt] = now
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
        return transaction(database) {
            val query = ChatMessagesTable
                .select { ChatMessagesTable.sessionId eq sessionId }
                .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            
            val messages = if (limit != null) {
                query.limit(limit)
            } else {
                query
            }
            
            messages.mapNotNull {
                val citationsJson = it[ChatMessagesTable.citations]
                val citations = if (citationsJson != null && citationsJson.isNotEmpty()) {
                    try {
                        json.decodeFromString<List<Citation>>(citationsJson)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse citations for message ${it[ChatMessagesTable.id]}: ${e.message}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                
                ChatMessage(
                    id = it[ChatMessagesTable.id],
                    sessionId = it[ChatMessagesTable.sessionId],
                    role = MessageRole.valueOf(it[ChatMessagesTable.role]),
                    content = it[ChatMessagesTable.content],
                    citations = citations,
                    createdAt = it[ChatMessagesTable.createdAt]
                )
            }
        }
    }
}

