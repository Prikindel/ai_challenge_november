package com.prike.data.client

import com.prike.config.LocalLLMAuthConfig
import com.prike.data.dto.OllamaChatRequest
import com.prike.data.dto.OllamaChatResponse
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
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.util.Base64
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * Клиент для работы с локальной LLM (Ollama)
 * 
 * Поддерживает:
 * - Ollama API (/api/generate)
 * - Генерацию текста с настройками температуры
 * - Обработку ошибок подключения
 * - HTTPS подключения
 * - Авторизацию (basic/bearer)
 */
class LocalLLMClient(
    private val baseUrl: String = "http://localhost:11434",
    private val defaultModel: String = "llama3.2",
    private val timeout: Long = 120000L,
    private val apiPath: String = "/api/generate",
    private val auth: LocalLLMAuthConfig? = null
) {
    private val logger = LoggerFactory.getLogger(LocalLLMClient::class.java)
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
                encodeDefaults = false
            })
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = timeout
            connectTimeoutMillis = 30000L
            socketTimeoutMillis = timeout
        }
        
        // Настройка SSL для работы с самоподписанными сертификатами
        // ВАЖНО: Это отключает проверку SSL сертификата (аналог curl -k)
        // Используйте только для разработки/тестирования!
        // Для VPS с самоподписанными сертификатами это необходимо
        if (baseUrl.startsWith("https://")) {
            logger.warn("⚠️ SSL certificate verification is DISABLED for HTTPS connections!")
            logger.warn("⚠️ This is equivalent to 'curl -k' and should only be used for development/testing")
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                    }
                }
            }
        }
    }
    
    /**
     * Генерирует ответ от локальной LLM с использованием messages формата
     * 
     * @param messages список сообщений с ролями (system, user, assistant)
     * @param model модель (если не указана, используется defaultModel)
     * @param parameters параметры LLM (temperature, maxTokens, topP, topK и др.)
     * @param stream использовать ли streaming (по умолчанию false)
     * @return ответ от LLM
     */
    suspend fun generateWithMessages(
        messages: List<com.prike.data.dto.OllamaMessage>,
        model: String? = null,
        parameters: com.prike.domain.model.LLMParameters = com.prike.domain.model.LLMParameters(),
        stream: Boolean = false
    ): String {
        val targetModel = model ?: defaultModel
        
        logger.debug("Generating response from local LLM (model: $targetModel, messages count: ${messages.size})")
        messages.forEach { msg ->
            logger.debug("Message [${msg.role}]: ${msg.content.take(100)}...")
        }
        
        return try {
            val chatRequest = OllamaChatRequest(
                model = targetModel,
                messages = messages,
                stream = stream,
                options = OllamaOptions(
                    temperature = parameters.temperature,
                    top_p = parameters.topP,
                    top_k = parameters.topK,
                    num_predict = parameters.maxTokens,
                    repeat_penalty = parameters.repeatPenalty,
                    num_ctx = parameters.contextWindow,
                    seed = parameters.seed
                )
            )
            
            // Используем /api/chat вместо /api/generate
            val connectionType = if (baseUrl.startsWith("https://")) "VPS" else "локальная"
            val fullUrl = "$baseUrl/api/chat"
            
            // Сериализуем запрос в JSON для логирования
            val requestJson = runCatching {
                jsonSerializer.encodeToString(chatRequest)
            }.getOrElse { "{}" }
            
            // Логируем запрос в формате OkHttp
            logger.info("--> POST $fullUrl")
            logger.info("Connection Type: $connectionType")
            logger.info("Content-Type: application/json")
            auth?.let { authConfig ->
                when (authConfig.type.lowercase()) {
                    "basic" -> {
                        if (authConfig.user.isNotBlank()) {
                            logger.info("Authorization: Basic (user: ${authConfig.user})")
                        }
                    }
                    "bearer" -> {
                        logger.info("Authorization: Bearer (token: ${if (authConfig.token.isNotBlank()) "***" else "не установлен"})")
                    }
                }
            } ?: logger.info("Authorization: нет")
            logger.info("")
            logger.info(requestJson)
            logger.info("--> END POST")
            
            val response = client.post(fullUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                
                // Добавляем авторизацию
                auth?.let { authConfig ->
                    when (authConfig.type.lowercase()) {
                        "basic" -> {
                            if (authConfig.user.isNotBlank() && authConfig.password.isNotBlank()) {
                                val credentials = "${authConfig.user}:${authConfig.password}"
                                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                                header(HttpHeaders.Authorization, "Basic $encoded")
                            }
                        }
                        "bearer" -> {
                            if (authConfig.token.isNotBlank()) {
                                header(HttpHeaders.Authorization, "Bearer ${authConfig.token}")
                            }
                        }
                    }
                }
                
                setBody(chatRequest)
            }
            
            val statusCode = response.status.value
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("Local LLM API error: $statusCode - $errorBody")
                when (statusCode) {
                    401 -> throw LocalLLMException(
                        "Ошибка авторизации: неверные учетные данные для VPS LLM",
                        Exception("HTTP 401 Unauthorized: $errorBody")
                    )
                    403 -> throw LocalLLMException(
                        "Доступ запрещен: недостаточно прав для доступа к VPS LLM",
                        Exception("HTTP 403 Forbidden: $errorBody")
                    )
                    in 500..599 -> throw LocalLLMException(
                        "Ошибка сервера VPS LLM: HTTP $statusCode",
                        Exception("Response: $errorBody")
                    )
                    else -> throw LocalLLMException(
                        "Ошибка локальной LLM API: HTTP $statusCode",
                        Exception("Response: $errorBody")
                    )
                }
            }
            
            val responseText = response.bodyAsText()
            
            // Логируем успешный ответ
            logger.info("<-- $statusCode $fullUrl")
            logger.info("Connection Type: $connectionType")
            logger.info("Response length: ${responseText.length} chars")
            if (logger.isDebugEnabled) {
                logger.debug("Response body (first 500 chars): ${responseText.take(500)}${if (responseText.length > 500) "..." else ""}")
            }
            logger.info("<-- END HTTP")
            
            // Парсим ответ из /api/chat (формат отличается от /api/generate)
            val chatResponse = try {
                val lines = responseText.lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) {
                    throw LocalLLMException("Пустой ответ от Ollama")
                }
                
                // Собираем все части ответа
                val fullResponse = StringBuilder()
                var isDone = false
                var lastModel = ""
                
                for (line in lines) {
                    try {
                        val jsonResponse = Json.decodeFromString<OllamaChatResponse>(line)
                        fullResponse.append(jsonResponse.message?.content ?: "")
                        isDone = jsonResponse.done ?: false
                        lastModel = jsonResponse.model ?: targetModel
                        
                        if (isDone) {
                            break
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to parse line: $line, error: ${e.message}")
                    }
                }
                
                val finalResponse = fullResponse.toString()
                if (finalResponse.isEmpty()) {
                    throw LocalLLMException("Пустой ответ после парсинга")
                }
                
                finalResponse
            } catch (e: LocalLLMException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to parse Ollama chat response: $responseText", e)
                throw LocalLLMException("Не удалось распарсить ответ от Ollama: ${e.message}", e)
            }
            
            logger.info("✓ Local LLM response generated successfully")
            logger.info("  Connection Type: $connectionType")
            logger.info("  Model: $targetModel")
            logger.info("  Response length: ${chatResponse.length} chars")
            
            chatResponse
        } catch (e: LocalLLMException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to generate response from local LLM: ${e.message}", e)
            throw LocalLLMException("Не удалось получить ответ от локальной LLM: ${e.message}", e)
        }
    }
    
    /**
     * Генерирует ответ от локальной LLM (старый метод для обратной совместимости)
     * 
     * @param prompt промпт для генерации
     * @param model модель (если не указана, используется defaultModel)
     * @param parameters параметры LLM (temperature, maxTokens, topP, topK и др.)
     * @param stream использовать ли streaming (по умолчанию false)
     * @return ответ от LLM
     */
    suspend fun generate(
        prompt: String,
        model: String? = null,
        parameters: com.prike.domain.model.LLMParameters = com.prike.domain.model.LLMParameters(),
        stream: Boolean = false
    ): String {
        val targetModel = model ?: defaultModel
        
        logger.debug("Generating response from local LLM (model: $targetModel, prompt length: ${prompt.length})")
        logger.debug("Prompt preview: ${prompt.take(200)}...")
        logger.debug("Parameters: temperature=${parameters.temperature}, maxTokens=${parameters.maxTokens}, topP=${parameters.topP}, topK=${parameters.topK}, repeatPenalty=${parameters.repeatPenalty}, contextWindow=${parameters.contextWindow}, seed=${parameters.seed}")
        
        return try {
            val request = OllamaGenerateRequest(
                model = targetModel,
                prompt = prompt,
                stream = stream,
                options = OllamaOptions(
                    temperature = parameters.temperature,
                    top_p = parameters.topP,
                    top_k = parameters.topK,
                    num_predict = parameters.maxTokens,
                    repeat_penalty = parameters.repeatPenalty,
                    num_ctx = parameters.contextWindow,
                    seed = parameters.seed
                )
            )
            
            // Определяем тип подключения для логирования
            val connectionType = if (baseUrl.startsWith("https://")) "VPS" else "локальная"
            val fullUrl = "$baseUrl$apiPath"
            
            // Сериализуем запрос в JSON для логирования
            val requestJson = runCatching {
                jsonSerializer.encodeToString(request)
            }.getOrElse { "{}" }
            
            // Логируем запрос в формате OkHttp
            logger.info("--> POST $fullUrl")
            logger.info("Connection Type: $connectionType")
            logger.info("Content-Type: application/json")
            auth?.let { authConfig ->
                when (authConfig.type.lowercase()) {
                    "basic" -> {
                        if (authConfig.user.isNotBlank()) {
                            logger.info("Authorization: Basic (user: ${authConfig.user})")
                        }
                    }
                    "bearer" -> {
                        logger.info("Authorization: Bearer (token: ${if (authConfig.token.isNotBlank()) "***" else "не установлен"})")
                    }
                }
            } ?: logger.info("Authorization: нет")
            logger.info("")
            logger.info(requestJson)
            logger.info("--> END POST")
            
            val response = client.post(fullUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                
                // Добавляем авторизацию
                auth?.let { authConfig ->
                    when (authConfig.type.lowercase()) {
                        "basic" -> {
                            if (authConfig.user.isNotBlank() && authConfig.password.isNotBlank()) {
                                val credentials = "${authConfig.user}:${authConfig.password}"
                                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                                header(HttpHeaders.Authorization, "Basic $encoded")
                                logger.debug("Using Basic auth for local LLM")
                            }
                        }
                        "bearer" -> {
                            if (authConfig.token.isNotBlank()) {
                                header(HttpHeaders.Authorization, "Bearer ${authConfig.token}")
                                logger.debug("Using Bearer auth for local LLM")
                            }
                        }
                    }
                }
                
                setBody(request)
            }
            
            val statusCode = response.status.value
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                
                // Логируем ошибку в формате OkHttp
                logger.info("<-- $statusCode $fullUrl")
                logger.info("Connection Type: $connectionType")
                if (errorBody.isNotEmpty()) {
                    logger.info("")
                    logger.info(errorBody)
                }
                logger.info("<-- END HTTP")
                
                logger.error("Local LLM API error: $statusCode - $errorBody")
                
                // Специальная обработка ошибок авторизации
                when (statusCode) {
                    401 -> throw LocalLLMException(
                        "Ошибка авторизации: неверные учетные данные для VPS LLM",
                        Exception("HTTP 401 Unauthorized: $errorBody")
                    )
                    403 -> throw LocalLLMException(
                        "Доступ запрещен: недостаточно прав для доступа к VPS LLM",
                        Exception("HTTP 403 Forbidden: $errorBody")
                    )
                    in 500..599 -> throw LocalLLMException(
                        "Ошибка сервера VPS LLM: HTTP $statusCode",
                        Exception("Response: $errorBody")
                    )
                    else -> throw LocalLLMException(
                        "Ошибка локальной LLM API: HTTP $statusCode",
                        Exception("Response: $errorBody")
                    )
                }
            }
            
            // Ollama может возвращать application/x-ndjson (newline-delimited JSON)
            // даже когда stream: false, поэтому читаем как текст и парсим все строки
            val responseText = response.bodyAsText()
            
            // Логируем успешный ответ в формате OkHttp
            logger.info("<-- $statusCode $fullUrl")
            logger.info("Connection Type: $connectionType")
            logger.info("Response length: ${responseText.length} chars")
            // Полный ответ логируем только на DEBUG уровне
            if (logger.isDebugEnabled) {
                logger.debug("Response body (first 500 chars): ${responseText.take(500)}${if (responseText.length > 500) "..." else ""}")
            }
            logger.info("<-- END HTTP")
            
            // Логируем только краткую информацию о raw response (DEBUG уровень)
            if (logger.isTraceEnabled) {
                logger.trace("Ollama raw response (first 500 chars): ${responseText.take(500)}")
            }
            
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
            
            logger.info("✓ Local LLM response generated successfully")
            logger.info("  Connection Type: $connectionType")
            logger.info("  Model: ${generateResponse.model}")
            logger.info("  Response length: ${generateResponse.response.length} chars")
            logger.info("  Done: ${generateResponse.done}")
            
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
        val connectionType = if (baseUrl.startsWith("https://")) "VPS" else "локальная"
        val fullUrl = "$baseUrl/api/version"
        
        logger.info("--> GET $fullUrl")
        logger.info("Connection Type: $connectionType")
        logger.info("Purpose: availability check")
        
        return try {
            val response = client.get(fullUrl) {
                timeout {
                    requestTimeoutMillis = 5000L
                }
                
                // Добавляем авторизацию для проверки доступности
                auth?.let { authConfig ->
                    when (authConfig.type.lowercase()) {
                        "basic" -> {
                            if (authConfig.user.isNotBlank() && authConfig.password.isNotBlank()) {
                                val credentials = "${authConfig.user}:${authConfig.password}"
                                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                                header(HttpHeaders.Authorization, "Basic $encoded")
                            }
                        }
                        "bearer" -> {
                            if (authConfig.token.isNotBlank()) {
                                header(HttpHeaders.Authorization, "Bearer ${authConfig.token}")
                            }
                        }
                    }
                }
            }
            
            val statusCode = response.status.value
            val isAvailable = response.status.isSuccess()
            
            logger.info("<-- $statusCode $fullUrl")
            logger.info("Connection Type: $connectionType")
            logger.info("Available: $isAvailable")
            logger.info("<-- END HTTP")
            
            isAvailable
        } catch (e: Exception) {
            logger.info("<-- ERROR $fullUrl")
            logger.info("Connection Type: $connectionType")
            logger.info("Error: ${e.message}")
            logger.info("<-- END HTTP")
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
        val connectionType = if (baseUrl.startsWith("https://")) "VPS" else "локальная"
        val fullUrl = "$baseUrl/api/tags"
        
        logger.info("--> GET $fullUrl")
        logger.info("Connection Type: $connectionType")
        logger.info("Purpose: list models")
        
        return try {
            val response = client.get(fullUrl) {
                // Добавляем авторизацию
                auth?.let { authConfig ->
                    when (authConfig.type.lowercase()) {
                        "basic" -> {
                            if (authConfig.user.isNotBlank() && authConfig.password.isNotBlank()) {
                                val credentials = "${authConfig.user}:${authConfig.password}"
                                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                                header(HttpHeaders.Authorization, "Basic $encoded")
                            }
                        }
                        "bearer" -> {
                            if (authConfig.token.isNotBlank()) {
                                header(HttpHeaders.Authorization, "Bearer ${authConfig.token}")
                            }
                        }
                    }
                }
            }
            
            val statusCode = response.status.value
            
            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                
                logger.info("<-- $statusCode $fullUrl")
                logger.info("Connection Type: $connectionType")
                logger.info("")
                if (responseText.length > 2000) {
                    logger.info("Response (first 2000 chars): ${responseText.take(2000)}...")
                } else {
                    logger.info(responseText)
                }
                logger.info("<-- END HTTP")
                
                val json = Json.parseToJsonElement(responseText)
                val models = (json as? JsonObject)?.get("models") as? JsonArray
                val modelList = models?.mapNotNull { modelElement ->
                    (modelElement as? JsonObject)?.get("name")?.jsonPrimitive?.content
                } ?: emptyList()
                
                logger.info("✓ Found ${modelList.size} models: ${modelList.joinToString(", ")}")
                modelList
            } else {
                logger.info("<-- $statusCode $fullUrl")
                logger.info("Connection Type: $connectionType")
                logger.info("Failed to list models")
                logger.info("<-- END HTTP")
                emptyList()
            }
        } catch (e: Exception) {
            logger.info("<-- ERROR $fullUrl")
            logger.info("Connection Type: $connectionType")
            logger.info("Error: ${e.message}")
            logger.info("<-- END HTTP")
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

