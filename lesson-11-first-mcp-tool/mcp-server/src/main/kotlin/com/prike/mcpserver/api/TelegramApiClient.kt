package com.prike.mcpserver.api

import com.prike.mcpserver.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

class TelegramApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    suspend fun getMe(): TelegramUser {
        val response = httpClient.get("$baseUrl/bot$token/getMe") {
            contentType(ContentType.Application.Json)
        }
        
        // Используем явную десериализацию для правильной обработки snake_case полей
        val responseText = response.bodyAsText()
        val result = json.decodeFromString<GetMeResponse>(responseText)
        
        if (!result.ok) {
            throw IllegalStateException("Telegram API error: ${result.result}")
        }
        
        return result.result
    }
    
    suspend fun sendMessage(chatId: String, text: String): TelegramMessage {
        val requestBody = mapOf(
            "chat_id" to chatId,
            "text" to text
        )
        
        val response = httpClient.post("$baseUrl/bot$token/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        
        // Используем явную десериализацию для правильной обработки snake_case полей
        val responseText = response.bodyAsText()
        val result = json.decodeFromString<SendMessageResponse>(responseText)
        
        if (!result.ok) {
            throw IllegalStateException("Telegram API error: failed to send message")
        }
        
        return result.result
    }
}

