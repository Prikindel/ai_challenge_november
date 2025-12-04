package com.prike.domain.koog.tools

import ai.koog.agents.core.tools.SimpleTool
import com.prike.domain.service.CRMMCPService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory

/**
 * Аргументы для инструмента get_user
 */
@Serializable
data class GetUserArgs(
    val userId: String? = null,
    val email: String? = null
)

/**
 * Koog инструмент для получения информации о пользователе из CRM
 */
class GetUserTool(private val crmMCPService: CRMMCPService?) : SimpleTool<GetUserArgs>() {
    private val logger = LoggerFactory.getLogger(GetUserTool::class.java)
    
    override val name = "get_user"
    override val description = "Получить информацию о пользователе по ID или email. Возвращает статус, подписку и другую информацию."
    
    override val argsSerializer = serializer<GetUserArgs>()
    
    override suspend fun doExecute(args: GetUserArgs): String {
        return try {
            if (crmMCPService == null) {
                return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("CRM MCP service is not available"))))
            }
            
            if (args.userId == null && args.email == null) {
                return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Either userId or email must be provided"))))
            }
            
            val user = crmMCPService.getUser(args.userId, args.email)
                ?: return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("User not found"))))
            
            val result = buildJsonObject {
                put("id", JsonPrimitive(user.id))
                put("email", JsonPrimitive(user.email))
                put("name", JsonPrimitive(user.name ?: ""))
                put("status", JsonPrimitive(user.status.name))
                put("subscription", user.subscription?.let { sub ->
                    buildJsonObject {
                        put("plan", JsonPrimitive(sub.plan))
                        put("expiresAt", JsonPrimitive(sub.expiresAt ?: 0))
                    }
                } ?: JsonNull)
            }
            
            Json.encodeToString(result)
        } catch (e: Exception) {
            logger.error("Error in get_user tool", e)
            Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Failed to get user: ${e.message}"))))
        }
    }
}

/**
 * Аргументы для инструмента get_ticket
 */
@Serializable
data class GetTicketArgs(
    val ticketId: String
)

/**
 * Koog инструмент для получения информации о тикете
 */
class GetTicketTool(private val crmMCPService: CRMMCPService?) : SimpleTool<GetTicketArgs>() {
    private val logger = LoggerFactory.getLogger(GetTicketTool::class.java)
    
    override val name = "get_ticket"
    override val description = "Получить информацию о тикете по ID. Возвращает историю сообщений, статус и приоритет."
    
    override val argsSerializer = serializer<GetTicketArgs>()
    
    override suspend fun doExecute(args: GetTicketArgs): String {
        return try {
            if (crmMCPService == null) {
                return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("CRM MCP service is not available"))))
            }
            
            val ticket = crmMCPService.getTicket(args.ticketId)
                ?: return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Ticket not found"))))
            
            val result = buildJsonObject {
                put("id", JsonPrimitive(ticket.id))
                put("userId", JsonPrimitive(ticket.userId))
                put("subject", JsonPrimitive(ticket.subject))
                put("description", JsonPrimitive(ticket.description))
                put("status", JsonPrimitive(ticket.status.name))
                put("priority", JsonPrimitive(ticket.priority.name))
                put("messages", JsonArray(ticket.messages.map { msg ->
                    buildJsonObject {
                        put("id", JsonPrimitive(msg.id))
                        put("author", JsonPrimitive(msg.author))
                        put("content", JsonPrimitive(msg.content))
                        put("timestamp", JsonPrimitive(msg.timestamp))
                    }
                }))
                put("createdAt", JsonPrimitive(ticket.createdAt))
                put("updatedAt", JsonPrimitive(ticket.updatedAt))
            }
            
            Json.encodeToString(result)
        } catch (e: Exception) {
            logger.error("Error in get_ticket tool", e)
            Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Failed to get ticket: ${e.message}"))))
        }
    }
}

/**
 * Аргументы для инструмента get_user_tickets
 */
@Serializable
data class GetUserTicketsArgs(
    val userId: String
)

/**
 * Koog инструмент для получения тикетов пользователя
 */
class GetUserTicketsTool(private val crmMCPService: CRMMCPService?) : SimpleTool<GetUserTicketsArgs>() {
    private val logger = LoggerFactory.getLogger(GetUserTicketsTool::class.java)
    
    override val name = "get_user_tickets"
    override val description = "Получить все тикеты пользователя по его ID."
    
    override val argsSerializer = serializer<GetUserTicketsArgs>()
    
    override suspend fun doExecute(args: GetUserTicketsArgs): String {
        return try {
            if (crmMCPService == null) {
                return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("CRM MCP service is not available"))))
            }
            
            val tickets = crmMCPService.getUserTickets(args.userId)
            
            val result = buildJsonObject {
                put("tickets", JsonArray(tickets.map { ticket ->
                    buildJsonObject {
                        put("id", JsonPrimitive(ticket.id))
                        put("subject", JsonPrimitive(ticket.subject))
                        put("status", JsonPrimitive(ticket.status.name))
                        put("priority", JsonPrimitive(ticket.priority.name))
                        put("messageCount", JsonPrimitive(ticket.messages.size))
                    }
                }))
            }
            
            Json.encodeToString(result)
        } catch (e: Exception) {
            logger.error("Error in get_user_tickets tool", e)
            Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Failed to get user tickets: ${e.message}"))))
        }
    }
}

/**
 * Аргументы для инструмента create_ticket
 */
@Serializable
data class CreateTicketArgs(
    val userId: String,
    val subject: String,
    val description: String
)

/**
 * Koog инструмент для создания тикета
 */
class CreateTicketTool(private val crmMCPService: CRMMCPService?) : SimpleTool<CreateTicketArgs>() {
    private val logger = LoggerFactory.getLogger(CreateTicketTool::class.java)
    
    override val name = "create_ticket"
    override val description = "Создать новый тикет для пользователя."
    
    override val argsSerializer = serializer<CreateTicketArgs>()
    
    override suspend fun doExecute(args: CreateTicketArgs): String {
        return try {
            if (crmMCPService == null) {
                return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("CRM MCP service is not available"))))
            }
            
            val ticket = crmMCPService.createTicket(args.userId, args.subject, args.description)
                ?: return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Failed to create ticket"))))
            
            val result = buildJsonObject {
                put("id", JsonPrimitive(ticket.id))
                put("userId", JsonPrimitive(ticket.userId))
                put("subject", JsonPrimitive(ticket.subject))
                put("status", JsonPrimitive(ticket.status.name))
            }
            
            Json.encodeToString(result)
        } catch (e: Exception) {
            logger.error("Error in create_ticket tool", e)
            Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Failed to create ticket: ${e.message}"))))
        }
    }
}

/**
 * Аргументы для инструмента add_ticket_message
 */
@Serializable
data class AddTicketMessageArgs(
    val ticketId: String,
    val author: String,
    val content: String
)

/**
 * Koog инструмент для добавления сообщения в тикет
 */
class AddTicketMessageTool(private val crmMCPService: CRMMCPService?) : SimpleTool<AddTicketMessageArgs>() {
    private val logger = LoggerFactory.getLogger(AddTicketMessageTool::class.java)
    
    override val name = "add_ticket_message"
    override val description = "Добавить сообщение в существующий тикет."
    
    override val argsSerializer = serializer<AddTicketMessageArgs>()
    
    override suspend fun doExecute(args: AddTicketMessageArgs): String {
        return try {
            if (crmMCPService == null) {
                return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("CRM MCP service is not available"))))
            }
            
            val message = crmMCPService.addMessage(args.ticketId, args.author, args.content)
                ?: return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Failed to add message"))))
            
            val result = buildJsonObject {
                put("id", JsonPrimitive(message.id))
                put("ticketId", JsonPrimitive(message.ticketId))
                put("author", JsonPrimitive(message.author))
                put("content", JsonPrimitive(message.content))
                put("timestamp", JsonPrimitive(message.timestamp))
            }
            
            Json.encodeToString(result)
        } catch (e: Exception) {
            logger.error("Error in add_ticket_message tool", e)
            Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Failed to add ticket message: ${e.message}"))))
        }
    }
}
