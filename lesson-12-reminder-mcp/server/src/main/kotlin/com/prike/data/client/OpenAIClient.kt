package com.prike.data.client

import com.prike.data.dto.MessageDto
import com.prike.data.dto.OpenAIErrorResponse
import com.prike.data.dto.OpenAIRequest
import com.prike.data.dto.OpenAIResponse
import com.prike.domain.exception.AIServiceException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с OpenAI API через OpenRouter
 * Поддерживает tools (function calling)
 */
class OpenAIClient(
    private val apiKey: String,
    private val apiUrl: String = "https://openrouter.ai/api/v1/chat/completions",
    private val model: String,
    private val temperature: Double = 0.7,
    private val maxTokens: Int = 2000,
    private val requestTimeoutSeconds: Int = 60
) {
    private val requestTimeoutMs = requestTimeoutSeconds * 1000L
    private val logger = LoggerFactory.getLogger(OpenAIClient::class.java)
    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    
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
    
    /**
     * Получить ответ от LLM с поддержкой tools
     */
    suspend fun getCompletionWithTools(
        messages: List<MessageDto>,
        tools: List<com.prike.data.dto.ToolDto>? = null
    ): CompletionResult {
        val request = OpenAIRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            tools = tools,
            toolChoice = if (tools != null && tools.isNotEmpty()) "auto" else null
        )
        
        return executeRequest(request)
    }
    
    /**
     * Получить простой ответ от LLM (без tools)
     */
    suspend fun getCompletion(userMessage: String): OpenAIResponse {
        val messages = listOf(
            MessageDto(role = "user", content = userMessage)
        )
        val request = OpenAIRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            tools = null,
            toolChoice = null
        )
        return executeRequest(request).response
    }
    
    /**
     * Результат запроса к LLM с JSON запросом и ответом
     */
    data class CompletionResult(
        val response: OpenAIResponse,
        val requestJson: String,
        val responseJson: String
    )
    
    /**
     * Выполнить запрос к API
     */
    private suspend fun executeRequest(request: OpenAIRequest): CompletionResult {
        // Сериализуем запрос в JSON для логирования
        val requestJson = runCatching {
            jsonSerializer.encodeToString(request)
        }.getOrElse { "{}" }
        
        return runCatching {
            val response = client.post(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                if (apiUrl.contains("openrouter")) {
                    header("HTTP-Referer", "http://localhost:8080")
                    header("X-Title", "Lesson-12-Summary-Agent")
                }
                setBody(request)
            }
            
            val statusCode = response.status.value
            
            if (!response.status.isSuccess()) {
                val errorBody = runCatching {
                    Json.decodeFromString<OpenAIErrorResponse>(response.bodyAsText())
                }.getOrNull()
                
                logger.error("API error: $statusCode - ${errorBody?.error?.message ?: "Unknown error"}")
                
                throw AIServiceException(
                    "Ошибка API: ${errorBody?.error?.message ?: "HTTP $statusCode"}",
                    Exception("API вернул статус $statusCode")
                )
            }
            
            val responseBody = response.body<OpenAIResponse>()
            
            // Сериализуем ответ в JSON для возврата
            val responseJson = runCatching {
                jsonSerializer.encodeToString(responseBody)
            }.getOrElse { "{}" }
            
            CompletionResult(responseBody, requestJson, responseJson)
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

