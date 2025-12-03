package com.prike.crmmcpserver.tools.handlers

import com.prike.crmmcpserver.storage.InMemoryCRMStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для получения тикета
 */
data class GetTicketParams(
    val ticketId: String
)

/**
 * Обработчик для инструмента get_ticket
 */
class GetTicketHandler(
    private val storage: InMemoryCRMStorage
) : ToolHandler<GetTicketParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetTicketHandler::class.java)
    
    override fun execute(params: GetTicketParams): String {
        logger.info("Получение тикета: ${params.ticketId}")
        
        val ticket = storage.getTicket(params.ticketId)
        
        if (ticket == null) {
            return "Тикет не найден: ${params.ticketId}"
        }
        
        return buildJsonObject {
            put("id", ticket.id)
            put("userId", ticket.userId)
            put("subject", ticket.subject)
            put("description", ticket.description)
            put("status", ticket.status.name)
            put("priority", ticket.priority.name)
            put("messages", buildJsonArray {
                ticket.messages.forEach { message ->
                    add(buildJsonObject {
                        put("id", message.id)
                        put("ticketId", message.ticketId)
                        put("author", message.author)
                        put("content", message.content)
                        put("timestamp", message.timestamp)
                    })
                }
            })
            put("createdAt", ticket.createdAt)
            put("updatedAt", ticket.updatedAt)
        }.toString()
    }
    
    override fun prepareResult(request: GetTicketParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): GetTicketParams {
            val ticketId = arguments["ticketId"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Необходимо указать ticketId")
            return GetTicketParams(ticketId = ticketId)
        }
    }
}

