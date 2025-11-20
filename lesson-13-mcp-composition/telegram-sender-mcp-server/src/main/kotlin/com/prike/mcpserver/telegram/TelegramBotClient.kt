package com.prike.mcpserver.telegram

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Клиент для отправки сообщений в Telegram через Bot API
 */
class TelegramBotClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String
) {
    private val logger = LoggerFactory.getLogger(TelegramBotClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Отправить сообщение пользователю в Telegram
     * @param userId ID пользователя для отправки (личное сообщение)
     * @param message текст сообщения для отправки
     * @return результат отправки с messageId и временем отправки
     */
    suspend fun sendMessage(userId: Long, message: String): SendMessageResult {
        try {
            val requestBody = buildJsonObject {
                put("chat_id", userId.toString())
                put("text", message)
                put("parse_mode", "Markdown")  // Поддержка Markdown
            }
            
            val response = httpClient.post("$baseUrl/bot$token/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            
            val responseText = response.bodyAsText()
            val result = json.parseToJsonElement(responseText).jsonObject
            
            val ok = result["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!ok) {
                val description = result["description"]?.jsonPrimitive?.contentOrNull
                    ?: "Unknown error"
                val errorCode = result["error_code"]?.jsonPrimitive?.intOrNull
                throw IllegalStateException("Telegram API error (code: $errorCode): $description")
            }
            
            val messageResult = result["result"]?.jsonObject
            val messageId = messageResult?.get("message_id")?.jsonPrimitive?.longOrNull
                ?: throw IllegalStateException("Message ID not found in response")
            
            logger.info("Сообщение успешно отправлено пользователю $userId (messageId: $messageId)")
            
            return SendMessageResult(
                success = true,
                messageId = messageId,
                sentAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error("Ошибка отправки сообщения в Telegram: ${e.message}", e)
            throw e
        }
    }
}

/**
 * Результат отправки сообщения
 */
data class SendMessageResult(
    val success: Boolean,
    val messageId: Long,
    val sentAt: Long
)

