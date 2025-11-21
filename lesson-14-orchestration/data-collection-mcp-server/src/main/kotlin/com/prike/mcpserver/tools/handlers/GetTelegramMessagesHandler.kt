package com.prike.mcpserver.tools.handlers

import com.prike.mcpserver.data.model.TelegramMessage
import com.prike.mcpserver.data.repository.TelegramMessageRepository
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Обработчик для инструмента get_telegram_messages
 * Получает сообщения из Telegram за указанный период времени
 * groupId берется из конфигурации (env), а не из параметров
 */
class GetTelegramMessagesHandler(
    private val telegramMessageRepository: TelegramMessageRepository,
    private val groupId: String  // ID группы из конфигурации
) : ToolHandler<GetTelegramMessagesHandler.Params, List<TelegramMessage>>() {

    override val logger = LoggerFactory.getLogger(GetTelegramMessagesHandler::class.java)

    override fun execute(params: Params): List<TelegramMessage> {
        logger.info("Получение сообщений Telegram за период: ${params.startTime} - ${params.endTime} для группы $groupId")
        val messages = telegramMessageRepository.getMessagesBetween(
            groupId = groupId,
            startTime = params.startTime,
            endTime = params.endTime
        )
        logger.info("Найдено сообщений: ${messages.size}")
        return messages
    }

    override fun prepareResult(request: Params, result: List<TelegramMessage>): TextContent {
        val messagesJson = buildJsonArray {
            result.forEach { message ->
                addJsonObject {
                    put("id", message.id)
                    put("messageId", message.messageId)
                    put("groupId", message.groupId)
                    put("content", message.content)
                    message.author?.let { put("author", it) }
                    put("timestamp", message.timestamp)
                    put("createdAt", message.createdAt)
                }
            }
        }

        val resultText = buildString {
            append("Сообщения Telegram за период:\n")
            append("- Группа: $groupId\n")
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

