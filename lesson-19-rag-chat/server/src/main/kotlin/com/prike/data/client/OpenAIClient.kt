package com.prike.data.client

import com.prike.data.dto.MessageDto
import com.prike.data.dto.OpenAIErrorResponse
import com.prike.data.dto.OpenAIRequest
import com.prike.data.dto.OpenAIResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с OpenAI API через OpenRouter
 */
class OpenAIClient(
    private val apiKey: String,
    private val apiUrl: String = "https://openrouter.ai/api/v1/chat/completions",
    private val model: String,
    private val temperature: Double = 0.7,
    private val maxTokens: Int = 2000,
    private val requestTimeoutSeconds: Int = 120
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
     * Получить ответ от LLM (chat completion)
     */
    suspend fun chatCompletion(
        messages: List<MessageDto>,
        temperature: Double = this.temperature
    ): OpenAIResponse {
        val request = OpenAIRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens
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
        
        return try {
            val response = client.post(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                if (apiUrl.contains("openrouter")) {
                    header("HTTP-Referer", "http://localhost:8080")
                    header("X-Title", "Lesson-19-RAG-Chat")
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
                
                logger.error("API error: $statusCode - ${errorBody?.error?.message ?: "Unknown error"}")
                
                throw OpenAIException(
                    "Ошибка API: ${errorBody?.error?.message ?: "HTTP $statusCode"}",
                    Exception("API вернул статус $statusCode")
                )
            }
            
            val responseBody = response.body<OpenAIResponse>()
            
            // Сериализуем ответ в JSON для логирования
            val responseJson = runCatching {
                jsonSerializer.encodeToString(responseBody)
            }.getOrElse { "{}" }
            
            // Логируем ответ в формате OkHttp
            runCatching {
                logger.info("<-- 200 $apiUrl")
                logger.info("Content-Type: application/json")
                logger.info("")
                logger.info(responseJson)
                logger.info("<-- END HTTP")
            }
            
            responseBody
        } catch (e: Exception) {
            when (e) {
                is OpenAIException -> throw e
                else -> {
                    logger.error("Error calling OpenRouter API: ${e.message}", e)
                    throw OpenAIException("Ошибка при обращении к OpenRouter API: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Закрыть HTTP клиент
     */
    fun close() {
        client.close()
    }
}

/**
 * Исключение при работе с OpenAI API
 */
class OpenAIException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

