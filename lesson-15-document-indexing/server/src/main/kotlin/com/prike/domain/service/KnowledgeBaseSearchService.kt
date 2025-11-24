package com.prike.domain.service

import com.prike.data.model.Document
import com.prike.data.model.DocumentChunk
import com.prike.data.repository.KnowledgeBaseRepository
import com.prike.domain.indexing.CosineSimilarityCalculator
import com.prike.domain.indexing.VectorNormalizer
import org.slf4j.LoggerFactory

/**
 * Сервис для поиска по базе знаний
 */
class KnowledgeBaseSearchService(
    private val embeddingService: EmbeddingService,
    private val vectorNormalizer: VectorNormalizer,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val similarityCalculator: CosineSimilarityCalculator
) {
    private val logger = LoggerFactory.getLogger(KnowledgeBaseSearchService::class.java)
    
    /**
     * Выполняет поиск по запросу в базе знаний
     * 
     * @param query поисковый запрос
     * @param limit максимальное количество результатов
     * @return список результатов поиска, отсортированных по сходству
     */
    suspend fun search(
        query: String,
        limit: Int = 10
    ): List<SearchResult> {
        if (query.isBlank()) {
            return emptyList()
        }
        
        logger.debug("Searching for: $query (limit: $limit)")
        
        // 1. Генерация эмбеддинга для запроса
        val queryEmbedding = embeddingService.generateEmbedding(query)
        
        // 2. Нормализация эмбеддинга запроса
        val normalizedQuery = vectorNormalizer.normalizeTo01(queryEmbedding)
        
        // 3. Получение всех чанков из БД
        val allChunks = knowledgeBaseRepository.getAllChunks()
        
        if (allChunks.isEmpty()) {
            logger.warn("No chunks in knowledge base")
            return emptyList()
        }
        
        logger.debug("Searching in ${allChunks.size} chunks")
        
        // 4. Вычисление сходства для каждого чанка
        val results = allChunks.map { chunk ->
            val similarity = similarityCalculator.calculateSimilarity(
                normalizedQuery,
                chunk.embedding
            )
            
            SearchResult(
                chunkId = chunk.id,
                documentId = chunk.documentId,
                content = chunk.content,
                similarity = similarity,
                chunkIndex = chunk.chunkIndex,
                startIndex = chunk.startIndex,
                endIndex = chunk.endIndex
            )
        }
        
        // 5. Сортировка и возврат топ-N
        val topResults = results
            .sortedByDescending { it.similarity }
            .take(limit)
        
        // 6. Обогащаем результаты информацией о документах
        // Загружаем только нужные документы для оптимизации
        val documentIds = topResults.map { it.documentId }.distinct()
        val documentsMap = knowledgeBaseRepository.getDocumentsByIds(documentIds)
            .associateBy { it.id }
        
        return topResults.map { result ->
            val document = documentsMap[result.documentId]
            result.copy(
                documentTitle = document?.title,
                documentFilePath = document?.filePath
            )
        }
    }
    
    /**
     * Выполняет поиск с минимальным порогом сходства
     * 
     * @param query поисковый запрос
     * @param limit максимальное количество результатов
     * @param minSimilarity минимальное сходство (по умолчанию 0.0)
     * @return список результатов поиска
     */
    suspend fun searchWithThreshold(
        query: String,
        limit: Int = 10,
        minSimilarity: Float = 0.0f
    ): List<SearchResult> {
        val results = search(query, limit * 2) // Получаем больше результатов для фильтрации
        return results.filter { it.similarity >= minSimilarity }.take(limit)
    }
}

/**
 * Результат поиска по базе знаний
 */
data class SearchResult(
    val chunkId: String,
    val documentId: String,
    val content: String,
    val similarity: Float,
    val chunkIndex: Int,
    val startIndex: Int,
    val endIndex: Int,
    val documentTitle: String? = null,
    val documentFilePath: String? = null
)

