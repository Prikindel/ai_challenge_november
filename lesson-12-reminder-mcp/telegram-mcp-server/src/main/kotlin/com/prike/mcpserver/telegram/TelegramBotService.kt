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
 * Сервис для отправки сообщений пользователю в Telegram
 * Используется для отправки summary лично пользователю
 */
class TelegramBotService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String
) {
    private val logger = LoggerFactory.getLogger(TelegramBotService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Отправить summary пользователю в Telegram
     * @param userId ID пользователя для отправки (личное сообщение)
     * @param summary текст summary для отправки
     */
    suspend fun sendSummaryToUser(userId: String, summary: String): Result<Unit> {
        return try {
            val requestBody = buildJsonObject {
                put("chat_id", userId)
                put("text", summary)
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
                throw IllegalStateException("Telegram API error: $description")
            }
            
            logger.info("Summary отправлен пользователю $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Ошибка отправки summary в Telegram: ${e.message}", e)
            Result.failure(e)
        }
    }
}

