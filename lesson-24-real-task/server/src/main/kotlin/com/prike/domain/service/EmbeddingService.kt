package com.prike.domain.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для генерации эмбеддингов через OpenAI API
 */
class EmbeddingService(
    private val apiKey: String,
    private val model: String = "text-embedding-3-small"
) {
    private val logger = LoggerFactory.getLogger(EmbeddingService::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Генерирует эмбеддинг для текста
     * 
     * @param text текст для генерации эмбеддинга
     * @return список чисел (вектор эмбеддинга)
     * @throws EmbeddingGenerationException если не удалось сгенерировать эмбеддинг
     */
    suspend fun generateEmbedding(text: String): List<Float> {
        if (text.isBlank()) {
            throw IllegalArgumentException("Text cannot be blank")
        }
        
        logger.debug("Generating embedding for text (length: ${text.length} chars)")
        
        try {
            val request = OpenAIEmbeddingRequest(
                model = model,
                input = text
            )
            
            val response = client.post("https://api.openai.com/v1/embeddings") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                val errorBody = response.body<String>()
                throw EmbeddingGenerationException(
                    "Failed to generate embedding: ${response.status} - $errorBody"
                )
            }
            
            val embeddingResponse = response.body<OpenAIEmbeddingResponse>()
            val embedding = embeddingResponse.data.firstOrNull()?.embedding
            
            if (embedding == null || embedding.isEmpty()) {
                throw EmbeddingGenerationException("Received empty embedding from OpenAI")
            }
            
            logger.debug("Generated embedding with dimension: ${embedding.size}")
            return embedding
        } catch (e: Exception) {
            when (e) {
                is EmbeddingGenerationException -> throw e
                else -> {
                    logger.error("Error generating embedding: ${e.message}", e)
                    throw EmbeddingGenerationException("Error generating embedding: ${e.message}", e)
                }
            }
        }
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
}

/**
 * Исключение при генерации эмбеддинга
 */
class EmbeddingGenerationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

@Serializable
data class OpenAIEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
data class OpenAIEmbeddingResponse(
    val data: List<EmbeddingData>
)

@Serializable
data class EmbeddingData(
    val embedding: List<Float>,
    val index: Int
)

