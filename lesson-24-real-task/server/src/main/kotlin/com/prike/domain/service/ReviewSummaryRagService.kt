package com.prike.domain.service

import com.prike.data.repository.ReviewsRepository
import com.prike.domain.model.ReviewSummary
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Сервис для RAG (Retrieval-Augmented Generation) для саммари отзывов
 * Индексирует саммари отзывов с эмбеддингами и предоставляет поиск
 */
class ReviewSummaryRagService(
    private val embeddingService: EmbeddingService,
    private val reviewsRepository: ReviewsRepository,
    private val database: org.jetbrains.exposed.sql.Database
) {
    private val logger = LoggerFactory.getLogger(ReviewSummaryRagService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Индексирует саммари отзыва (создает чанк с эмбеддингом)
     * 
     * @param summary саммари отзыва для индексации
     */
    suspend fun indexSummary(summary: ReviewSummary) {
        try {
            // Создаем текст для индексации из саммари
            val textToIndex = buildTextForIndexing(summary)
            
            // Генерируем эмбеддинг
            val embedding = embeddingService.generateEmbedding(textToIndex)
            
            // Сохраняем чанк в БД
            transaction(database) {
                val chunkId = UUID.randomUUID().toString()
                // Проверяем, существует ли уже чанк для этого reviewId
                val existing = com.prike.data.repository.ReviewSummaryChunksTable
                    .select { com.prike.data.repository.ReviewSummaryChunksTable.reviewId eq summary.reviewId }
                    .firstOrNull()
                
                val embeddingJson: String = json.encodeToString(embedding)
                val table = com.prike.data.repository.ReviewSummaryChunksTable
                
                if (existing == null) {
                    table.insert {
                        it[table.id] = chunkId
                        it[table.reviewId] = summary.reviewId
                        it[table.chunkIndex] = 0
                        it[table.content] = textToIndex
                        it[table.embedding] = embeddingJson
                        it[table.weekStart] = summary.weekStart
                        it[table.createdAt] = java.time.Instant.now().toString()
                    }
                } else {
                    // Обновляем существующий чанк
                    table.update({
                        table.reviewId eq summary.reviewId
                    }) {
                        it[table.content] = textToIndex
                        it[table.embedding] = embeddingJson
                        it[table.weekStart] = summary.weekStart
                    }
                }
            }
            
            logger.debug("Indexed summary for review ${summary.reviewId}")
        } catch (e: Exception) {
            logger.error("Error indexing summary for review ${summary.reviewId}: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Индексирует несколько саммари отзывов
     * 
     * @param summaries список саммари для индексации
     */
    suspend fun indexSummaries(summaries: List<ReviewSummary>) {
        logger.info("Indexing ${summaries.size} review summaries")
        
        summaries.forEach { summary ->
            try {
                indexSummary(summary)
            } catch (e: Exception) {
                logger.warn("Failed to index summary for review ${summary.reviewId}: ${e.message}")
            }
        }
        
        logger.info("Finished indexing ${summaries.size} review summaries")
    }
    
    /**
     * Выполняет поиск по саммари отзывов
     * 
     * @param query поисковый запрос
     * @param limit максимальное количество результатов
     * @param minSimilarity минимальное сходство (0.0 - 1.0)
     * @return список результатов поиска, отсортированных по сходству
     */
    suspend fun search(
        query: String,
        limit: Int = 10,
        minSimilarity: Double = 0.0
    ): List<ReviewSummarySearchResult> {
        if (query.isBlank()) {
            return emptyList()
        }
        
        logger.debug("Searching for: $query (limit: $limit, minSimilarity: $minSimilarity)")
        
        // Генерируем эмбеддинг для запроса
        val queryEmbedding = embeddingService.generateEmbedding(query)
        
        // Получаем все чанки из БД
        val allChunks = transaction(database) {
            com.prike.data.repository.ReviewSummaryChunksTable.selectAll().map { row ->
                ChunkData(
                    id = row[com.prike.data.repository.ReviewSummaryChunksTable.id],
                    reviewId = row[com.prike.data.repository.ReviewSummaryChunksTable.reviewId],
                    content = row[com.prike.data.repository.ReviewSummaryChunksTable.content],
                    embedding = json.decodeFromString<List<Float>>(
                        row[com.prike.data.repository.ReviewSummaryChunksTable.embedding]
                    ),
                    weekStart = row[com.prike.data.repository.ReviewSummaryChunksTable.weekStart]
                )
            }
        }
        
        if (allChunks.isEmpty()) {
            logger.warn("No chunks in RAG index")
            return emptyList()
        }
        
        logger.debug("Searching in ${allChunks.size} chunks")
        
        // Вычисляем сходство для каждого чанка
        val results = allChunks.map { chunk ->
            val similarity = calculateCosineSimilarity(queryEmbedding, chunk.embedding)
            
            ReviewSummarySearchResult(
                reviewId = chunk.reviewId,
                content = chunk.content,
                similarity = similarity,
                weekStart = chunk.weekStart
            )
        }
        
        // Фильтруем по минимальному сходству, сортируем и возвращаем топ-N
        return results
            .filter { it.similarity >= minSimilarity }
            .sortedByDescending { it.similarity }
            .take(limit)
    }
    
    /**
     * Строит текст для индексации из саммари отзыва
     */
    private fun buildTextForIndexing(summary: ReviewSummary): String {
        val topicsText = summary.topics.joinToString(", ") { it.name }
        return """
            |Рейтинг: ${summary.rating}/5
            |Категория: ${summary.category.name}
            |Темы: $topicsText
            |Критичность: ${summary.criticality.name}
            |Саммари: ${summary.summary}
        """.trimMargin()
    }
    
    /**
     * Вычисляет косинусное сходство между двумя векторами
     */
    private fun calculateCosineSimilarity(vec1: List<Float>, vec2: List<Float>): Double {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vectors must have the same size")
        }
        
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        val denominator = Math.sqrt(norm1) * Math.sqrt(norm2)
        return if (denominator == 0.0) 0.0 else dotProduct / denominator
    }
    
    /**
     * Удаляет все чанки для указанного reviewId
     */
    fun deleteChunksForReview(reviewId: String) {
        transaction(database) {
            com.prike.data.repository.ReviewSummaryChunksTable.deleteWhere {
                com.prike.data.repository.ReviewSummaryChunksTable.reviewId eq reviewId
            }
        }
    }
}

/**
 * Результат поиска по саммари отзывов
 */
data class ReviewSummarySearchResult(
    val reviewId: String,
    val content: String,
    val similarity: Double,
    val weekStart: String
)

/**
 * Данные чанка из БД
 */
private data class ChunkData(
    val id: String,
    val reviewId: String,
    val content: String,
    val embedding: List<Float>,
    val weekStart: String
)

