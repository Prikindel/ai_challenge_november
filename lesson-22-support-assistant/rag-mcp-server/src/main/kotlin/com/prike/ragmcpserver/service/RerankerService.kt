package com.prike.ragmcpserver.domain.service

import com.prike.ragmcpserver.config.AIConfig
import com.prike.mcpcommon.client.OpenAIClient
import com.prike.mcpcommon.dto.MessageDto
import com.prike.ragmcpserver.domain.model.RetrievedChunk
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Результат реранкинга одного чанка
 */
data class RerankDecision(
    val chunkId: String,
    val rerankScore: Float,  // Оценка релевантности от LLM (0-1)
    val reason: String,       // Объяснение решения
    val shouldUse: Boolean   // Рекомендация использовать чанк
)

/**
 * Результат реранкинга всех чанков
 */
data class RerankResult(
    val decisions: List<RerankDecision>,  // Решения для каждого чанка
    val rerankedChunks: List<RetrievedChunk>  // Переранжированные чанки (отсортированы по rerankScore)
)

/**
 * Конфигурация реранкера (domain)
 */
data class RerankerConfig(
    val model: String = "gpt-4o-mini",
    val maxChunks: Int = 6,
    val systemPrompt: String = DEFAULT_RERANKER_PROMPT
)

/**
 * Сервис для реранкинга чанков через LLM
 * 
 * Использует LLM для оценки релевантности каждого чанка вопросу
 * и переранжирования результатов поиска.
 */
class RerankerService(
    private val aiConfig: AIConfig,
    private val config: RerankerConfig
) {
    private val logger = LoggerFactory.getLogger(RerankerService::class.java)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val rerankerClient = OpenAIClient(
        apiKey = aiConfig.apiKey,
        model = config.model,
        temperature = 0.3,  // Низкая температура для более детерминированных оценок
        maxTokens = 2000,
        requestTimeoutSeconds = 180  // Увеличенный таймаут для реранкера (3 минуты)
    )
    
    /**
     * Выполняет реранкинг списка чанков
     * 
     * @param question вопрос пользователя
     * @param chunks список чанков для реранкинга
     * @return результат реранкинга с решениями и переранжированными чанками
     */
    suspend fun rerank(question: String, chunks: List<RetrievedChunk>): RerankResult {
        if (chunks.isEmpty()) {
            return RerankResult(emptyList(), emptyList())
        }
        
        // Ограничиваем количество чанков для реранкинга
        val chunksToRerank = chunks.take(config.maxChunks)
        if (chunks.size > config.maxChunks) {
            logger.warn("Reranking only top ${config.maxChunks} chunks out of ${chunks.size}")
        }
        
        logger.info("Reranking ${chunksToRerank.size} chunks (model: ${config.model})")
        
        try {
            // Формируем промпт для реранкера
            val prompt = buildRerankerPrompt(question, chunksToRerank)
            
            // Отправляем запрос к LLM
            val messages = listOf(
                MessageDto(role = "system", content = config.systemPrompt),
                MessageDto(role = "user", content = prompt)
            )
            
            // OpenAIClient сам логирует запрос и ответ в формате OkHttp
            val response = rerankerClient.chatCompletion(messages, temperature = 0.3)
            
            val responseContent = response.choices.firstOrNull()?.message?.content ?: ""
            val tokensUsed = response.usage?.totalTokens
            
            // Парсим JSON ответ
            val decisions = parseRerankerResponse(responseContent)
            
            if (decisions.isEmpty()) {
                logger.warn("Reranker returned no decisions, falling back to original order")
                return createFallbackResult(chunksToRerank)
            }
            
            // Создаём мапу решений по chunkId для быстрого поиска
            val decisionMap = decisions.associateBy { it.chunkId }
            
            // Объединяем чанки с решениями и сортируем по rerankScore
            val rerankedChunks = chunksToRerank.mapNotNull { chunk ->
                val decision = decisionMap[chunk.chunkId]
                if (decision != null) {
                    chunk
                } else {
                    logger.warn("No rerank decision for chunk ${chunk.chunkId}, skipping")
                    null
                }
            }.sortedByDescending { chunk ->
                decisionMap[chunk.chunkId]?.rerankScore ?: chunk.similarity
            }
            
            // Фильтруем по shouldUse
            val filteredByShouldUse = rerankedChunks.filter { chunk ->
                decisionMap[chunk.chunkId]?.shouldUse == true
            }
            
            val shouldUseCount = decisions.count { it.shouldUse }
            
            // Fallback: если реранкер отфильтровал все чанки, используем топ-3 по rerankScore
            // Это предотвращает ситуацию, когда все чанки отфильтровываются, но некоторые могут быть полезны
            val finalChunks = if (filteredByShouldUse.isEmpty() && rerankedChunks.isNotEmpty()) {
                logger.warn("Reranker filtered out all chunks, using top ${minOf(3, rerankedChunks.size)} chunks by rerankScore as fallback")
                rerankedChunks.take(minOf(3, rerankedChunks.size))
            } else {
                filteredByShouldUse
            }
            
            logger.info("Reranker result: ${chunksToRerank.size} → ${finalChunks.size} chunks (shouldUse: $shouldUseCount/${decisions.size}${if (tokensUsed != null) ", tokens: $tokensUsed" else ""})")
            
            return RerankResult(
                decisions = decisions,
                rerankedChunks = finalChunks
            )
            
        } catch (e: Exception) {
            logger.error("Reranker failed: ${e.message}, falling back to original order", e)
            // При ошибке (таймаут, недоступность API) возвращаем исходный порядок
            // Это позволяет системе продолжать работать даже если реранкер недоступен
            return createFallbackResult(chunksToRerank)
        }
    }
    
    /**
     * Формирует промпт для реранкера
     */
    private fun buildRerankerPrompt(question: String, chunks: List<RetrievedChunk>): String {
        val chunksJson = chunks.map { chunk ->
            """
            {
                "chunkId": "${chunk.chunkId}",
                "content": ${json.encodeToString(chunk.content.take(500))},
                "similarity": ${chunk.similarity}
            }
            """.trimIndent()
        }.joinToString(",\n")
        
        return """
        Вопрос пользователя: "$question"

        Оцени релевантность каждого чанка вопросу. Верни JSON массив с оценками:
        [
            {
                "chunkId": "id_чанка",
                "relevance": 0.85,  // оценка релевантности от 0 до 1
                "reason": "Чанк содержит конкретные инструкции по созданию MCP сервера",
                "shouldUse": true   // использовать ли этот чанк в контексте
            },
            ...
        ]

        Чанки для оценки:
        [$chunksJson]
        """.trimIndent()
    }
    
    /**
     * Парсит JSON ответ от реранкера
     */
    private fun parseRerankerResponse(responseText: String): List<RerankDecision> {
        return try {
            // Извлекаем JSON из ответа (может быть обёрнут в markdown код блоки)
            val jsonText = extractJsonFromResponse(responseText)
            
            val decisions = json.decodeFromString<List<RerankDecisionDto>>(jsonText)
            
            decisions.map { dto ->
                RerankDecision(
                    chunkId = dto.chunkId,
                    rerankScore = dto.relevance.coerceIn(0f, 1f),
                    reason = dto.reason,
                    shouldUse = dto.shouldUse
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to parse reranker response: ${e.message}", e)
            logger.debug("Response text that failed to parse:\n$responseText")
            emptyList()
        }
    }
    
    /**
     * Извлекает JSON из ответа LLM (может быть в markdown блоке)
     */
    private fun extractJsonFromResponse(response: String): String {
        var text = response.trim()
        
        // Удаляем markdown код блоки, если есть
        if (text.startsWith("```")) {
            val lines = text.lines()
            val startIndex = lines.indexOfFirst { it.contains("```") } + 1
            val endIndex = lines.indexOfLast { it.contains("```") }
            if (endIndex > startIndex) {
                text = lines.subList(startIndex, endIndex).joinToString("\n")
            }
        }
        
        // Ищем JSON массив
        val jsonStart = text.indexOf('[')
        val jsonEnd = text.lastIndexOf(']')
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1)
        }
        
        return text
    }
    
    /**
     * Создаёт fallback результат (если реранкер не сработал)
     */
    private fun createFallbackResult(chunks: List<RetrievedChunk>): RerankResult {
        val decisions = chunks.map { chunk ->
            RerankDecision(
                chunkId = chunk.chunkId,
                rerankScore = chunk.similarity,
                reason = "Fallback: using original similarity score",
                shouldUse = true
            )
        }
        
        return RerankResult(
            decisions = decisions,
            rerankedChunks = chunks.sortedByDescending { it.similarity }
        )
    }
    
    /**
     * Закрывает клиент
     */
    fun close() {
        rerankerClient.close()
    }
}

/**
 * DTO для парсинга ответа реранкера
 */
@Serializable
private data class RerankDecisionDto(
    val chunkId: String,
    val relevance: Float,
    val reason: String,
    val shouldUse: Boolean
)

/**
 * Промпт по умолчанию для реранкера
 */
private const val DEFAULT_RERANKER_PROMPT = """
Ты — эксперт по оценке релевантности текстовых фрагментов.

Твоя задача — оценить, насколько каждый фрагмент текста (чанк) релевантен заданному вопросу.

Для каждого чанка верни:
- relevance: оценка от 0 до 1 (1 = полностью релевантен, 0 = не релевантен)
- reason: краткое объяснение оценки (1-2 предложения)
- shouldUse: использовать ли этот чанк в контексте для ответа на вопрос

Верни только валидный JSON массив, без дополнительных комментариев.
"""

