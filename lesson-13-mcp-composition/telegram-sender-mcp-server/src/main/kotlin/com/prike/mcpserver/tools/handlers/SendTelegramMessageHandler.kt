package com.prike.mcpserver.tools.handlers

import com.prike.mcpserver.telegram.SendMessageResult
import com.prike.mcpserver.telegram.TelegramBotClient
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Параметры для отправки сообщения в Telegram
 */
data class SendTelegramMessageParams(
    val userId: String,
    val message: String
)

/**
 * Обработчик для инструмента send_telegram_message
 */
class SendTelegramMessageHandler(
    private val telegramBotClient: TelegramBotClient
) : ToolHandler<SendTelegramMessageParams, SendMessageResult>() {
    
    override val logger = LoggerFactory.getLogger(SendTelegramMessageHandler::class.java)
    
    override fun execute(params: SendTelegramMessageParams): SendMessageResult {
        logger.info("Отправка сообщения пользователю ${params.userId}")

        val result = runBlocking {
            telegramBotClient.sendMessage(
                userId = params.userId.toLong(),
                message = params.message
            )
        }

        return result
    }
    
    override fun prepareResult(request: SendTelegramMessageParams, result: SendMessageResult): TextContent {
        val jsonResult = buildJsonObject {
            put("success", result.success)
            put("messageId", result.messageId)
            put("sentAt", result.sentAt)
        }

        return TextContent(text = jsonResult.toString())
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): SendTelegramMessageParams {
            val userId = arguments["userId"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("userId is required")
            val message = arguments["message"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("message is required")
            
            return SendTelegramMessageParams(
                userId = userId,
                message = message
            )
        }
    }
}

