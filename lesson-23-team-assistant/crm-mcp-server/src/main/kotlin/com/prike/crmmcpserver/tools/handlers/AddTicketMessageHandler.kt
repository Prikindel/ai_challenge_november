package com.prike.crmmcpserver.tools.handlers

import com.prike.crmmcpserver.storage.InMemoryCRMStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для добавления сообщения в тикет
 */
data class AddTicketMessageParams(
    val ticketId: String,
    val author: String,
    val content: String
)

/**
 * Обработчик для инструмента add_ticket_message
 */
class AddTicketMessageHandler(
    private val storage: InMemoryCRMStorage
) : ToolHandler<AddTicketMessageParams, String>() {
    
    override val logger = LoggerFactory.getLogger(AddTicketMessageHandler::class.java)
    
    override fun execute(params: AddTicketMessageParams): String {
        logger.info("Добавление сообщения в тикет: ${params.ticketId}")
        
        // Проверяем, что тикет существует
        val ticket = storage.getTicket(params.ticketId)
        if (ticket == null) {
            throw IllegalArgumentException("Тикет не найден: ${params.ticketId}")
        }
        
        val message = storage.addMessage(
            ticketId = params.ticketId,
            author = params.author,
            content = params.content
        )
        
        return buildJsonObject {
            put("id", message.id)
            put("ticketId", message.ticketId)
            put("author", message.author)
            put("content", message.content)
            put("timestamp", message.timestamp)
        }.toString()
    }
    
    override fun prepareResult(request: AddTicketMessageParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): AddTicketMessageParams {
            val ticketId = arguments["ticketId"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Необходимо указать ticketId")
            val author = arguments["author"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Необходимо указать author")
            val content = arguments["content"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Необходимо указать content")
            
            return AddTicketMessageParams(
                ticketId = ticketId,
                author = author,
                content = content
            )
        }
    }
}

