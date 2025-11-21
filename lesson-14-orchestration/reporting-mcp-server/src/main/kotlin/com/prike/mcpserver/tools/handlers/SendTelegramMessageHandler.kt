package com.prike.mcpserver.tools.handlers

import com.prike.mcpserver.telegram.TelegramBotService
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Обработчик для инструмента send_telegram_message
 * Отправляет сообщение в Telegram
 */
class SendTelegramMessageHandler(
    private val telegramBotService: TelegramBotService,
    private val chatId: String  // ID чата из конфигурации
) : ToolHandler<SendTelegramMessageHandler.Params, SendTelegramMessageHandler.SendResult>() {

    override val logger = LoggerFactory.getLogger(SendTelegramMessageHandler::class.java)

    override fun execute(params: Params): SendResult {
        logger.info("Отправка сообщения в Telegram (chatId: $chatId, длина: ${params.message.length} символов)")
        
        return runBlocking {
            val result = telegramBotService.sendMessage(
                chatId = chatId,
                message = params.message
            )
            
            if (result.success) {
                logger.info("Сообщение отправлено успешно (messageId: ${result.messageId})")
                SendResult(
                    success = true,
                    messageId = result.messageId
                )
            } else {
                logger.error("Ошибка отправки сообщения: ${result.error}")
                SendResult(
                    success = false,
                    error = result.error
                )
            }
        }
    }

    override fun prepareResult(request: Params, result: SendResult): TextContent {
        val resultJson = buildJsonObject {
            put("success", result.success)
            result.messageId?.let { put("messageId", it) }
            result.error?.let { put("error", it) }
        }
        
        return TextContent(text = resultJson.toString())
    }

    data class Params(
        val message: String
    )
    
    data class SendResult(
        val success: Boolean,
        val messageId: Int? = null,
        val error: String? = null
    )
}

