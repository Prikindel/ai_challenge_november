package com.prike.mcpserver.tools.handlers

import com.prike.mcpserver.data.model.ChatMessage
import com.prike.mcpserver.data.repository.ChatHistoryRepository
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Обработчик для инструмента get_chat_history
 * Содержит логику обработки запроса и формирования ответа
 */
class GetChatHistoryHandler(
    private val chatHistoryRepository: ChatHistoryRepository
) : ToolHandler<GetChatHistoryHandler.Params, List<ChatMessage>>() {

    override val logger = LoggerFactory.getLogger(GetChatHistoryHandler::class.java)

    override fun execute(params: Params): List<ChatMessage> {
        logger.info("Получение истории чата за период: ${params.startTime} - ${params.endTime}")
        val messages = chatHistoryRepository.getMessagesBetween(params.startTime, params.endTime)
        logger.info("Найдено сообщений: ${messages.size}")
        return messages
    }

    override fun prepareResult(request: Params, result: List<ChatMessage>): TextContent {
        val messagesJson = buildJsonArray {
            result.forEach { message ->
                addJsonObject {
                    put("id", message.id)
                    put("role", message.role)
                    put("content", message.content)
                    put("timestamp", message.timestamp)
                    message.model?.let { put("model", it) }
                }
            }
        }

        val resultText = buildString {
            append("История чата за период:\n")
            append("- Начало: ${Instant.ofEpochMilli(request.startTime)}\n")
            append("- Конец: ${Instant.ofEpochMilli(request.endTime)}\n")
            append("- Найдено сообщений: ${result.size}\n\n")
            append("Сообщения:\n")
            append(messagesJson.toString())
        }

        return TextContent(text = resultText)
    }

    data class Params(
        val startTime: Long,
        val endTime: Long
    )
}

