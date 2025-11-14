package com.prike.data.client

import com.prike.data.dto.MessageDto
import com.prike.data.dto.OpenAIErrorResponse
import com.prike.data.dto.OpenAIRequest
import com.prike.data.dto.OpenAIResponse
import com.prike.data.dto.ResponseFormatDto
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
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с OpenAI API
 * Настраивается через конфигурацию, репозиторий не знает про детали
 */
class OpenAIClient(
    private val apiKey: String,
    private val apiUrl: String,
    private val model: String,
    private val temperature: Double,
    private val maxTokens: Int?,
    private val requestTimeoutSeconds: Int,
    private val systemPrompt: String? = null,
    private val defaultUseJsonResponse: Boolean = false
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
    
    suspend fun getCompletion(userMessage: String): CompletionResult {
        val messages = buildList {
            systemPrompt?.let { add(MessageDto(role = "system", content = it)) }
            add(MessageDto(role = "user", content = userMessage))
        }
        return getCompletionWithHistory(messages)
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
     * Получить ответ от LLM с использованием истории сообщений
     * @param messages список сообщений (включая system prompt и историю диалога)
     * @return результат с ответом и JSON запросом/ответом
     */
    data class RequestOptions(
        val model: String? = null,
        val temperature: Double? = null,
        val maxTokens: Int? = null,
        val useJsonResponseFormat: Boolean? = null
    )

    suspend fun getCompletionWithHistory(
        messages: List<MessageDto>,
        options: RequestOptions = RequestOptions()
    ): CompletionResult {
        val request = OpenAIRequest(
            model = options.model ?: model,
            messages = messages,
            temperature = options.temperature ?: temperature,
            maxTokens = options.maxTokens ?: maxTokens,
            responseFormat = when (options.useJsonResponseFormat ?: defaultUseJsonResponse) {
                true -> ResponseFormatDto(type = "json_object")
                else -> null
            }
        )
        
        // Сериализуем запрос в JSON для логирования
        val requestJson = runCatching {
            jsonSerializer.encodeToString(request)
        }.getOrElse { "{}" }
        
        // Логируем JSON запрос в формате OkHttp
        runCatching {
            logger.info("--> POST $apiUrl")
            logger.info("Content-Type: application/json")
            logger.info("")
            logger.info(requestJson)
            logger.info("--> END POST")
        }
        
        return runCatching {
            val response = client.post(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                if (apiUrl.contains("openrouter")) {
                    header("HTTP-Referer", "http://localhost:8080")
                    header("X-Title", "ChatAgent")
                }
                setBody(request)
            }
            
            val statusCode = response.status.value
            
            if (!response.status.isSuccess()) {
                val errorBody = runCatching {
                    Json.decodeFromString<OpenAIErrorResponse>(response.bodyAsText())
                }.getOrNull()
                
                val errorJson = runCatching {
                    if (errorBody != null) jsonSerializer.encodeToString(errorBody) else ""
                }.getOrElse { "" }
                
                // Логируем ошибку в формате OkHttp
                runCatching {
                    logger.info("<-- $statusCode $apiUrl")
                    if (errorJson.isNotEmpty()) {
                        logger.info(errorJson)
                    }
                    logger.info("<-- END HTTP")
                }
                
                throw AIServiceException(
                    "Ошибка API: ${errorBody?.error?.message ?: "HTTP $statusCode"}",
                    Exception("API вернул статус $statusCode")
                )
            }
            
            val responseBody = response.body<OpenAIResponse>()
            
            // Сериализуем ответ в JSON для логирования
            val responseJson = runCatching {
                jsonSerializer.encodeToString(responseBody)
            }.getOrElse { "{}" }
            
            // Логируем JSON ответ в формате OkHttp
            runCatching {
                logger.info("<-- $statusCode $apiUrl")
                logger.info("")
                logger.info(responseJson)
                logger.info("<-- END HTTP")
            }
            
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

