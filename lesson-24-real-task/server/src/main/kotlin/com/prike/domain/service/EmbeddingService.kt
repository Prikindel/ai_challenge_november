package com.prike.domain.service

import com.prike.data.client.OllamaClient
import com.prike.data.client.OllamaException
import org.slf4j.LoggerFactory
import kotlinx.coroutines.delay

/**
 * Сервис для генерации эмбеддингов через локальный Ollama
 */
class EmbeddingService(
    private val ollamaClient: OllamaClient
) {
    private val logger = LoggerFactory.getLogger(EmbeddingService::class.java)
    
    /**
     * Генерирует эмбеддинг для текста
     * 
     * @param text текст для генерации эмбеддинга
     * @param maxRetries максимальное количество попыток при ошибке
     * @return список чисел (вектор эмбеддинга)
     * @throws EmbeddingGenerationException если не удалось сгенерировать эмбеддинг
     */
    suspend fun generateEmbedding(
        text: String,
        maxRetries: Int = 6
    ): List<Float> {
        if (text.isBlank()) {
            throw IllegalArgumentException("Text cannot be blank")
        }
        
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return ollamaClient.generateEmbedding(text)
            } catch (e: OllamaException) {
                lastException = e
                logger.warn("Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                
                if (attempt < maxRetries - 1) {
                    // Экспоненциальная задержка между попытками
                    val delayMs = (2000L * (1 shl attempt)).coerceAtMost(30000L)
                    logger.debug("Waiting ${delayMs}ms before retry...")
                    delay(delayMs)
                }
            }
        }
        
        throw EmbeddingGenerationException(
            "Failed to generate embedding after $maxRetries attempts",
            lastException
        )
    }
    
    /**
     * Генерирует эмбеддинги для нескольких текстов
     * 
     * @param texts список текстов
     * @return список эмбеддингов (в том же порядке, что и тексты)
     */
    suspend fun generateEmbeddings(texts: List<String>): List<List<Float>> {
        return texts.map { text ->
            generateEmbedding(text)
        }
    }
    
    /**
     * Проверяет доступность сервиса эмбеддингов
     */
    suspend fun checkHealth(): Boolean {
        return ollamaClient.checkHealth()
    }
}

/**
 * Исключение при генерации эмбеддинга
 */
class EmbeddingGenerationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

