package com.prike.data.client

import com.prike.data.dto.OllamaGenerateRequest
import com.prike.data.dto.OllamaGenerateResponse
import com.prike.data.dto.OllamaOptions
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с локальной LLM (Ollama)
 * 
 * Поддерживает:
 * - Ollama API (/api/generate)
 * - Генерацию текста с настройками температуры
 * - Обработку ошибок подключения
 */
class LocalLLMClient(
    private val baseUrl: String = "http://localhost:11434",
    private val defaultModel: String = "llama3.2",
    private val timeout: Long = 120000L
) {
    private val logger = LoggerFactory.getLogger(LocalLLMClient::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = timeout
            connectTimeoutMillis = 30000L
            socketTimeoutMillis = timeout
        }
    }
    
    /**
     * Генерирует ответ от локальной LLM
     * 
     * @param prompt промпт для генерации
     * @param model модель (если не указана, используется defaultModel)
     * @param temperature температура генерации (0.0 - 1.0)
     * @param stream использовать ли streaming (по умолчанию false)
     * @return ответ от LLM
     */
    suspend fun generate(
        prompt: String,
        model: String? = null,
        temperature: Double = 0.7,
        stream: Boolean = false
    ): String {
        val targetModel = model ?: defaultModel
        
        logger.debug("Generating response from local LLM (model: $targetModel, prompt length: ${prompt.length})")
        logger.debug("Prompt preview: ${prompt.take(200)}...")
        
        return try {
            val request = OllamaGenerateRequest(
                model = targetModel,
                prompt = prompt,
                stream = stream,
                options = OllamaOptions(
                    temperature = temperature,
                    num_predict = null // Не ограничиваем длину ответа
                )
            )
            
            val response = client.post("$baseUrl/api/generate") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("Local LLM API error: ${response.status.value} - $errorBody")
                throw LocalLLMException(
                    "Ошибка локальной LLM API: HTTP ${response.status.value}",
                    Exception("Response: $errorBody")
                )
            }
            
            // Ollama может возвращать application/x-ndjson (newline-delimited JSON)
            // даже когда stream: false, поэтому читаем как текст и парсим все строки
            val responseText = response.bodyAsText()
            logger.debug("Ollama raw response (first 500 chars): ${responseText.take(500)}")
            
            val generateResponse = try {
                // Парсим все строки NDJSON и собираем полный ответ
                val lines = responseText.lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) {
                    throw LocalLLMException("Пустой ответ от Ollama")
                }
                
                // Собираем все части ответа из всех строк
                val fullResponse = StringBuilder()
                var isDone = false
                var lastModel = ""
                
                for (line in lines) {
                    try {
                        val jsonResponse = Json.decodeFromString<OllamaGenerateResponse>(line)
                        fullResponse.append(jsonResponse.response)
                        isDone = jsonResponse.done
                        lastModel = jsonResponse.model
                        
                        if (isDone) {
                            break
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to parse line: $line, error: ${e.message}")
                        // Продолжаем обработку других строк
                    }
                }
                
                val finalResponse = fullResponse.toString()
                if (finalResponse.isEmpty()) {
                    throw LocalLLMException("Пустой ответ после парсинга всех строк")
                }
                
                if (!isDone) {
                    logger.warn("Local LLM response not done after parsing all lines, but using collected response")
                }
                
                OllamaGenerateResponse(
                    model = lastModel.ifEmpty { targetModel },
                    response = finalResponse,
                    done = isDone
                )
            } catch (e: LocalLLMException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to parse Ollama response: $responseText", e)
                throw LocalLLMException("Не удалось распарсить ответ от Ollama: ${e.message}", e)
            }
            
            logger.debug("Local LLM response generated (length: ${generateResponse.response.length}, done: ${generateResponse.done})")
            generateResponse.response
        } catch (e: LocalLLMException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to generate response from local LLM: ${e.message}", e)
            throw LocalLLMException("Не удалось получить ответ от локальной LLM: ${e.message}", e)
        }
    }
    
    /**
     * Проверяет доступность локальной LLM
     * 
     * @return true если LLM доступна, false иначе
     */
    suspend fun checkAvailability(): Boolean {
        return try {
            val response = client.get("$baseUrl/api/version") {
                timeout {
                    requestTimeoutMillis = 5000L
                }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.debug("Local LLM not available: ${e.message}")
            false
        }
    }
    
    /**
     * Получает список доступных моделей
     * 
     * @return список моделей или пустой список при ошибке
     */
    suspend fun listModels(): List<String> {
        return try {
            val response = client.get("$baseUrl/api/tags")
            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val json = Json.parseToJsonElement(responseText)
                val models = (json as? JsonObject)?.get("models") as? JsonArray
                models?.mapNotNull { modelElement ->
                    (modelElement as? JsonObject)?.get("name")?.jsonPrimitive?.content
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("Failed to list models: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Закрывает HTTP клиент
     */
    fun close() {
        client.close()
    }
}

/**
 * Исключение при работе с локальной LLM
 */
class LocalLLMException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

