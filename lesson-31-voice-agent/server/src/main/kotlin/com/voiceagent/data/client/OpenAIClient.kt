package com.voiceagent.data.client

import com.voiceagent.data.dto.MessageDto
import com.voiceagent.data.dto.OpenAIErrorResponse
import com.voiceagent.data.dto.OpenAIRequest
import com.voiceagent.data.dto.OpenAIResponse
import com.voiceagent.domain.exception.AIServiceException
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
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с OpenAI API
 * Настраивается через конфигурацию, репозиторий не знает про детали
 */
class OpenAIClient(
    private val apiKey: String?,
    private val apiUrl: String,
    private val model: String,
    private val temperature: Double,
    private val maxTokens: Int,
    private val requestTimeoutSeconds: Int,
    private val systemPrompt: String? = null,
    private val authType: String? = null,
    private val authUser: String? = null,
    private val authPassword: String? = null
) {
    private val logger = LoggerFactory.getLogger(OpenAIClient::class.java)
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

        logger.info(
            "LLM request -> url={}, model={}, authType={}, hasApiKey={}",
            apiUrl, model, authType ?: "bearer", !apiKey.isNullOrBlank()
        )
        
        return runCatching {
            val response = client.post(apiUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)

                when {
                    authType.equals("basic", ignoreCase = true) && !authUser.isNullOrBlank() && !authPassword.isNullOrBlank() -> {
                        val credentials = "$authUser:$authPassword"
                        val encoded = java.util.Base64.getEncoder().encodeToString(credentials.toByteArray())
                        header(HttpHeaders.Authorization, "Basic $encoded")
                    }
                    !apiKey.isNullOrBlank() -> {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                }
                if (apiUrl.contains("openrouter")) {
                    header("HTTP-Referer", "http://localhost:8080")
                    header("X-Title", "ChatAgent")
                }
                setBody(OpenAIRequest(model, messages, temperature, maxTokens))
            }
            
            if (!response.status.isSuccess()) {
                val rawBody = runCatching { response.bodyAsText() }.getOrDefault("<no body>")
                val errorBody = runCatching {
                    Json.decodeFromString<OpenAIErrorResponse>(rawBody)
                }.getOrNull()

                logger.error(
                    "LLM response error: status={}, body={}",
                    response.status.value,
                    rawBody.take(2000)
                )

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

