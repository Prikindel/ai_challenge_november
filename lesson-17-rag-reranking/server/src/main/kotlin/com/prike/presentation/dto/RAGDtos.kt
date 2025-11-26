package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для RAG-запроса
 */
@Serializable
data class RAGQueryRequestDto(
    val question: String,
    val topK: Int = 3,
    val minSimilarity: Float = 0.7f
)

/**
 * DTO для извлечённого чанка
 */
@Serializable
data class RetrievedChunkDto(
    val chunkId: String,
    val documentPath: String?,
    val documentTitle: String?,
    val content: String,
    val similarity: Float,
    val chunkIndex: Int
)

/**
 * DTO для отброшенного чанка
 */
@Serializable
data class DroppedChunkDto(
    val chunkId: String,
    val documentPath: String?,
    val similarity: Float,
    val reason: String
)

/**
 * DTO для статистики фильтрации
 */
@Serializable
data class FilterStatsDto(
    val retrieved: Int,
    val kept: Int,
    val dropped: List<DroppedChunkDto>,
    val avgSimilarityBefore: Float,
    val avgSimilarityAfter: Float
)

/**
 * DTO для решения реранкера
 */
@Serializable
data class RerankDecisionDto(
    val chunkId: String,
    val rerankScore: Float,
    val reason: String,
    val shouldUse: Boolean
)

/**
 * DTO для RAG-ответа
 */
@Serializable
data class RAGQueryResponseDto(
    val question: String,
    val answer: String,
    val contextChunks: List<RetrievedChunkDto>,
    val tokensUsed: Int? = null,
    val filterStats: FilterStatsDto? = null,
    val rerankInsights: List<RerankDecisionDto>? = null
)

/**
 * DTO для обычного ответа
 */
@Serializable
data class StandardResponseDto(
    val question: String,
    val answer: String,
    val tokensUsed: Int
)

/**
 * DTO для результата сравнения
 */
@Serializable
data class ComparisonResponseDto(
    val question: String,
    val ragResponse: RAGQueryResponseDto,
    val standardResponse: StandardResponseDto
)

