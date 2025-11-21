package com.prike.mcpserver.telegram

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с Telegram Bot API
 */
class TelegramBotService(
    private val botToken: String,
    private val defaultChatId: String
) {
    private val logger = LoggerFactory.getLogger(TelegramBotService::class.java)
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    private val baseUrl = "https://api.telegram.org/bot$botToken"
    
    /**
     * Отправить сообщение в Telegram
     * @param chatId ID чата (если не указан, используется defaultChatId)
     * @param message текст сообщения
     * @return результат отправки
     */
    suspend fun sendMessage(chatId: String? = null, message: String): SendMessageResult {
        val targetChatId = chatId ?: defaultChatId
        
        return try {
            logger.info("Отправка сообщения в Telegram (chatId: $targetChatId, длина: ${message.length} символов)")
            
            val response = httpClient.post("$baseUrl/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest(
                    chatId = targetChatId,
                    text = message
                ))
            }
            
            val result = response.body<TelegramResponse<SendMessageResponse>>()
            
            if (result.ok) {
                val messageId = result.result?.messageId
                logger.info("Сообщение отправлено успешно (messageId: $messageId)")
                SendMessageResult(
                    success = true,
                    messageId = messageId
                )
            } else {
                logger.error("Ошибка отправки сообщения: ${result.description}")
                SendMessageResult(
                    success = false,
                    error = result.description ?: "Unknown error"
                )
            }
        } catch (e: Exception) {
            logger.error("Ошибка при отправке сообщения в Telegram: ${e.message}", e)
            SendMessageResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    @Serializable
    private data class SendMessageRequest(
        @SerialName("chat_id")
        val chatId: String,
        val text: String
    )
    
    @Serializable
    private data class TelegramResponse<T>(
        val ok: Boolean,
        val result: T? = null,
        val description: String? = null
    )
    
    @Serializable
    private data class SendMessageResponse(
        @SerialName("message_id")
        val messageId: Int
    )
}

data class SendMessageResult(
    val success: Boolean,
    val messageId: Int? = null,
    val error: String? = null
)

