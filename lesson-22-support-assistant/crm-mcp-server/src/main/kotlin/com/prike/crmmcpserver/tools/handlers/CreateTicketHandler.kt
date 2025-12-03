package com.prike.crmmcpserver.tools.handlers

import com.prike.crmmcpserver.storage.InMemoryCRMStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для создания тикета
 */
data class CreateTicketParams(
    val userId: String,
    val subject: String,
    val description: String
)

/**
 * Обработчик для инструмента create_ticket
 */
class CreateTicketHandler(
    private val storage: InMemoryCRMStorage
) : ToolHandler<CreateTicketParams, String>() {
    
    override val logger = LoggerFactory.getLogger(CreateTicketHandler::class.java)
    
    override fun execute(params: CreateTicketParams): String {
        logger.info("Создание тикета для пользователя: ${params.userId}")
        
        // Проверяем, что пользователь существует
        val user = storage.getUser(params.userId)
        if (user == null) {
            throw IllegalArgumentException("Пользователь не найден: ${params.userId}")
        }
        
        val ticket = storage.createTicket(
            userId = params.userId,
            subject = params.subject,
            description = params.description
        )
        
        return buildJsonObject {
            put("id", ticket.id)
            put("userId", ticket.userId)
            put("subject", ticket.subject)
            put("description", ticket.description)
            put("status", ticket.status.name)
            put("priority", ticket.priority.name)
            put("createdAt", ticket.createdAt)
            put("updatedAt", ticket.updatedAt)
        }.toString()
    }
    
    override fun prepareResult(request: CreateTicketParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): CreateTicketParams {
            val userId = arguments["userId"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Необходимо указать userId")
            val subject = arguments["subject"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Необходимо указать subject")
            val description = arguments["description"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Необходимо указать description")
            
            return CreateTicketParams(
                userId = userId,
                subject = subject,
                description = description
            )
        }
    }
}

