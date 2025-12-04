package com.prike.crmmcpserver.tools.handlers

import com.prike.crmmcpserver.storage.InMemoryCRMStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для получения тикетов пользователя
 */
data class GetUserTicketsParams(
    val userId: String
)

/**
 * Обработчик для инструмента get_user_tickets
 */
class GetUserTicketsHandler(
    private val storage: InMemoryCRMStorage
) : ToolHandler<GetUserTicketsParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetUserTicketsHandler::class.java)
    
    override fun execute(params: GetUserTicketsParams): String {
        logger.info("Получение тикетов пользователя: ${params.userId}")
        
        val tickets = storage.getUserTickets(params.userId)
        
        return buildJsonArray {
            tickets.forEach { ticket ->
                add(buildJsonObject {
                    put("id", ticket.id)
                    put("userId", ticket.userId)
                    put("subject", ticket.subject)
                    put("description", ticket.description)
                    put("status", ticket.status.name)
                    put("priority", ticket.priority.name)
                    put("messageCount", ticket.messages.size)
                    put("createdAt", ticket.createdAt)
                    put("updatedAt", ticket.updatedAt)
                })
            }
        }.toString()
    }
    
    override fun prepareResult(request: GetUserTicketsParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): GetUserTicketsParams {
            val userId = arguments["userId"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Необходимо указать userId")
            return GetUserTicketsParams(userId = userId)
        }
    }
}

