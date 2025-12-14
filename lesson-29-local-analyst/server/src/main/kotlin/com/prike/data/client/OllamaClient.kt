package com.prike.data.client

import com.prike.config.OllamaConfig
import com.prike.data.dto.OllamaEmbeddingRequest
import com.prike.data.dto.OllamaEmbeddingResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с Ollama API
 */
class OllamaClient(
    private val config: OllamaConfig
) {
    private val logger = LoggerFactory.getLogger(OllamaClient::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeout
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = config.timeout
        }
    }
    
    /**
     * Генерирует эмбеддинг для текста через Ollama API
     * 
     * @param text текст для генерации эмбеддинга
     * @return список чисел (вектор эмбеддинга)
     * @throws OllamaException если не удалось сгенерировать эмбеддинг
     */
    suspend fun generateEmbedding(text: String): List<Float> {
        if (text.isBlank()) {
            throw IllegalArgumentException("Text cannot be blank")
        }
        
        // Проверка размера текста
        if (text.length > 1500) {
            logger.warn("Large text detected (${text.length} chars). Ollama may have issues with very large texts. Consider reducing chunk size.")
        }
        
        val url = "${config.baseUrl}/api/embeddings"
        logger.debug("Generating embedding for text (length: ${text.length} chars) via $url")
        
        try {
            val request = OllamaEmbeddingRequest(
                model = config.model,
                prompt = text
            )
            
            logger.debug("Sending embedding request to Ollama (timeout: ${config.timeout}ms, model: ${config.model})")
            
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                header("Connection", "close")
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                val errorBody = response.body<String>()
                throw OllamaException(
                    "Failed to generate embedding: ${response.status} - $errorBody"
                )
            }
            
            val embeddingResponse = response.body<OllamaEmbeddingResponse>()
            val embedding = embeddingResponse.embedding
            
            logger.debug("Generated embedding with dimension: ${embedding.size}")
            
            if (embedding.isEmpty()) {
                throw OllamaException("Received empty embedding from Ollama")
            }
            
            return embedding
        } catch (e: Exception) {
            when (e) {
                is OllamaException -> throw e
                is java.net.SocketTimeoutException -> {
                    logger.error("Timeout generating embedding (text length: ${text.length}, timeout: ${config.timeout}ms): ${e.message}")
                    throw OllamaException("Timeout generating embedding: request exceeded ${config.timeout}ms", e)
                }
                is java.io.EOFException -> {
                    logger.error("Connection closed (EOF) while generating embedding (text length: ${text.length}): ${e.message}")
                    throw OllamaException("Connection closed while generating embedding. Text may be too large or Ollama server may be overloaded.", e)
                }
                else -> {
                    logger.error("Error generating embedding (text length: ${text.length}): ${e.message}", e)
                    throw OllamaException("Failed to generate embedding: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Проверяет доступность Ollama сервера
     */
    suspend fun checkHealth(): Boolean {
        return try {
            val url = "${config.baseUrl}/api/tags"
            val response = client.get(url)
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.warn("Ollama health check failed: ${e.message}")
            false
        }
    }
    
    /**
     * Закрывает клиент и освобождает ресурсы
     */
    fun close() {
        client.close()
    }
}

/**
 * Исключение при работе с Ollama API
 */
class OllamaException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

