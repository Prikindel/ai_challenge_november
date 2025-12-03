package com.prike.domain.service

import com.prike.data.client.CRMMCPClient
import com.prike.domain.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Сервис для работы с CRM MCP сервером
 */
class CRMMCPService(
    private val crmMCPClient: CRMMCPClient,
    private val lessonRoot: File,
    private val crmMCPJarPath: String? = null
) {
    private val logger = LoggerFactory.getLogger(CRMMCPService::class.java)
    
    /**
     * Подключение к CRM MCP серверу
     */
    suspend fun connect() {
        try {
            crmMCPClient.connectToServer(crmMCPJarPath, lessonRoot)
            logger.info("CRM MCP service connected successfully")
        } catch (e: Exception) {
            logger.error("Failed to connect to CRM MCP server: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Отключение от CRM MCP сервера
     */
    suspend fun disconnect() {
        try {
            crmMCPClient.disconnect()
            logger.info("CRM MCP service disconnected")
        } catch (e: Exception) {
            logger.warn("Error disconnecting from CRM MCP server: ${e.message}")
        }
    }
    
    /**
     * Получить пользователя по ID или email
     */
    suspend fun getUser(userId: String? = null, email: String? = null): User? {
        return try {
            if (!crmMCPClient.isConnected()) {
                logger.warn("CRM MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                userId?.let { put("userId", it) }
                email?.let { put("email", it) }
            }
            
            val result = crmMCPClient.callTool("get_user", arguments)
            
            if (result.contains("не найден") || result.contains("not found")) {
                logger.warn("User not found: userId=$userId, email=$email")
                return null
            }
            
            parseUser(result)
        } catch (e: Exception) {
            logger.error("Failed to get user: ${e.message}", e)
            null
        }
    }
    
    /**
     * Получить тикет по ID
     */
    suspend fun getTicket(ticketId: String): Ticket? {
        return try {
            if (!crmMCPClient.isConnected()) {
                logger.warn("CRM MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                put("ticketId", ticketId)
            }
            
            val result = crmMCPClient.callTool("get_ticket", arguments)
            
            if (result.contains("не найден") || result.contains("not found")) {
                logger.warn("Ticket not found: $ticketId")
                return null
            }
            
            parseTicket(result)
        } catch (e: Exception) {
            logger.error("Failed to get ticket: ${e.message}", e)
            null
        }
    }
    
    /**
     * Получить все тикеты пользователя
     */
    suspend fun getUserTickets(userId: String): List<Ticket> {
        return try {
            if (!crmMCPClient.isConnected()) {
                logger.warn("CRM MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                put("userId", userId)
            }
            
            val result = crmMCPClient.callTool("get_user_tickets", arguments)
            
            parseTickets(result)
        } catch (e: Exception) {
            logger.error("Failed to get user tickets: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Создать новый тикет
     */
    suspend fun createTicket(userId: String, subject: String, description: String): Ticket? {
        return try {
            if (!crmMCPClient.isConnected()) {
                logger.warn("CRM MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                put("userId", userId)
                put("subject", subject)
                put("description", description)
            }
            
            val result = crmMCPClient.callTool("create_ticket", arguments)
            parseTicket(result)
        } catch (e: Exception) {
            logger.error("Failed to create ticket: ${e.message}", e)
            null
        }
    }
    
    /**
     * Добавить сообщение в тикет
     */
    suspend fun addMessage(ticketId: String, author: String, content: String): TicketMessage? {
        return try {
            if (!crmMCPClient.isConnected()) {
                logger.warn("CRM MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                put("ticketId", ticketId)
                put("author", author)
                put("content", content)
            }
            
            val result = crmMCPClient.callTool("add_ticket_message", arguments)
            parseTicketMessage(result)
        } catch (e: Exception) {
            logger.error("Failed to add message: ${e.message}", e)
            null
        }
    }
    
    fun isConnected(): Boolean {
        return crmMCPClient.isConnected()
    }
    
    // Парсинг JSON ответов
    
    private fun parseUser(json: String): User? {
        return try {
            val obj = Json.parseToJsonElement(json) as? JsonObject ?: return null
            User(
                id = obj["id"]?.jsonPrimitive?.content ?: return null,
                email = obj["email"]?.jsonPrimitive?.content ?: return null,
                name = obj["name"]?.jsonPrimitive?.content,
                status = UserStatus.valueOf(obj["status"]?.jsonPrimitive?.content ?: "ACTIVE"),
                subscription = obj["subscription"]?.jsonObject?.let { subObj ->
                    Subscription(
                        plan = subObj["plan"]?.jsonPrimitive?.content ?: "",
                        expiresAt = subObj["expiresAt"]?.jsonPrimitive?.longOrNull
                    )
                },
                createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        } catch (e: Exception) {
            logger.error("Failed to parse user: ${e.message}", e)
            null
        }
    }
    
    private fun parseTicket(json: String): Ticket? {
        return try {
            val obj = Json.parseToJsonElement(json) as? JsonObject ?: return null
            Ticket(
                id = obj["id"]?.jsonPrimitive?.content ?: return null,
                userId = obj["userId"]?.jsonPrimitive?.content ?: return null,
                subject = obj["subject"]?.jsonPrimitive?.content ?: return null,
                description = obj["description"]?.jsonPrimitive?.content ?: return null,
                status = TicketStatus.valueOf(obj["status"]?.jsonPrimitive?.content ?: "OPEN"),
                priority = Priority.valueOf(obj["priority"]?.jsonPrimitive?.content ?: "MEDIUM"),
                messages = obj["messages"]?.jsonArray?.mapNotNull { parseTicketMessage(it.toString()) } ?: emptyList(),
                createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        } catch (e: Exception) {
            logger.error("Failed to parse ticket: ${e.message}", e)
            null
        }
    }
    
    private fun parseTickets(json: String): List<Ticket> {
        return try {
            val array = Json.parseToJsonElement(json) as? JsonArray ?: return emptyList()
            array.mapNotNull { element ->
                if (element is JsonObject) {
                    try {
                        Ticket(
                            id = element["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            userId = element["userId"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            subject = element["subject"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            description = element["description"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            status = TicketStatus.valueOf(element["status"]?.jsonPrimitive?.content ?: "OPEN"),
                            priority = Priority.valueOf(element["priority"]?.jsonPrimitive?.content ?: "MEDIUM"),
                            messages = emptyList(), // В списке тикетов сообщения не включаются
                            createdAt = element["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                            updatedAt = element["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to parse ticket from array: ${e.message}")
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse tickets: ${e.message}", e)
            emptyList()
        }
    }
    
    private fun parseTicketMessage(json: String): TicketMessage? {
        return try {
            val obj = Json.parseToJsonElement(json) as? JsonObject ?: return null
            TicketMessage(
                id = obj["id"]?.jsonPrimitive?.content ?: return null,
                ticketId = obj["ticketId"]?.jsonPrimitive?.content ?: return null,
                author = obj["author"]?.jsonPrimitive?.content ?: return null,
                content = obj["content"]?.jsonPrimitive?.content ?: return null,
                timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        } catch (e: Exception) {
            logger.error("Failed to parse ticket message: ${e.message}", e)
            null
        }
    }
}

