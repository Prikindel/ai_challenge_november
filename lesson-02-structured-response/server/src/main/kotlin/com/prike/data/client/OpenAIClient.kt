package com.prike.data.client

import com.prike.data.dto.MessageDto
import com.prike.data.dto.OpenAIErrorResponse
import com.prike.data.dto.OpenAIRequest
import com.prike.data.dto.OpenAIResponse
import com.prike.domain.exception.AIServiceException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Клиент для работы с OpenAI API
 * Настраивается через конфигурацию, репозиторий не знает про детали
 */
class OpenAIClient(
    private val apiKey: String,
    private val apiUrl: String,
    private val model: String,
    private val temperature: Double,
    private val maxTokens: Int,
    private val requestTimeoutSeconds: Int,
    private val systemPrompt: String? = null,
    private val useJsonFormat: Boolean = false
) {
    private val requestTimeoutMs = requestTimeoutSeconds * 1000L
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeoutMs
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = requestTimeoutMs
        }
    }
    
    suspend fun getCompletion(userMessage: String): OpenAIResponse {
        val messages = buildList {
            systemPrompt?.let { add(MessageDto(role = "system", content = it)) }
            add(MessageDto(role = "user", content = userMessage))
        }
        
        val responseFormat = if (useJsonFormat) {
            buildJsonObject {
                put("type", "json_object")
            }
        } else null
        
        return runCatching {
            val response = client.post(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                if (apiUrl.contains("openrouter")) {
                    header("HTTP-Referer", "http://localhost:8080")
                    header("X-Title", "ChatAgent")
                }
                setBody(OpenAIRequest(model, messages, temperature, maxTokens, responseFormat))
            }
            
            if (!response.status.isSuccess()) {
                val errorBody = runCatching {
                    Json.decodeFromString<OpenAIErrorResponse>(response.bodyAsText())
                }.getOrNull()
                
                throw AIServiceException(
                    "Ошибка API: ${errorBody?.error?.message ?: "HTTP ${response.status.value}"}",
                    Exception("API вернул статус ${response.status.value}")
                )
            }
            
            response.body<OpenAIResponse>()
        }.getOrElse { throwable ->
            if (throwable is AIServiceException) throw throwable
            throw AIServiceException("Ошибка при обращении к OpenAI API: ${throwable.message}", throwable)
        }
    }
    
    /**
     * Закрыть HTTP клиент
     */
    fun close() {
        client.close()
    }
}

