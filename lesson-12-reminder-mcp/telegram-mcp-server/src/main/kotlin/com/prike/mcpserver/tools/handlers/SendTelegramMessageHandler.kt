package com.prike.mcpserver.tools.handlers

import com.prike.mcpserver.telegram.TelegramBotService
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
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
    private val telegramBotService: TelegramBotService
) : ToolHandler<SendTelegramMessageParams, String>() {
    
    override val logger = LoggerFactory.getLogger(SendTelegramMessageHandler::class.java)
    
    override fun execute(params: SendTelegramMessageParams): String {
        logger.info("Отправка сообщения пользователю ${params.userId}")
        
        // Используем runBlocking для вызова suspend функции
        val result = runBlocking {
            telegramBotService.sendSummaryToUser(
                userId = params.userId,
                summary = params.message
            )
        }
        
        return when {
            result.isSuccess -> "Сообщение успешно отправлено пользователю ${params.userId}"
            else -> {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                logger.error("Ошибка отправки сообщения: $error")
                throw IllegalStateException("Failed to send message: $error")
            }
        }
    }
    
    override fun prepareResult(request: SendTelegramMessageParams, result: String): TextContent {
        return TextContent(text = result)
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

