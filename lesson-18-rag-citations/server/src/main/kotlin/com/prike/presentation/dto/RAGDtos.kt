package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для RAG-запроса
 */
@Serializable
data class RAGQueryRequestDto(
    val question: String,
    val topK: Int = 3,
    val minSimilarity: Float = 0.7f,
    val applyFilter: Boolean? = null,  // Применить фильтр (null = использовать конфигурацию)
    val strategy: String? = null  // Стратегия фильтрации (none, threshold, reranker, hybrid)
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
 * DTO для цитаты
 */
@Serializable
data class CitationDto(
    val text: String,
    val documentPath: String,
    val documentTitle: String,
    val chunkId: String? = null
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
    val rerankInsights: List<RerankDecisionDto>? = null,
    val citations: List<CitationDto> = emptyList()
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
 * DTO для метрик сравнения
 */
@Serializable
data class ComparisonMetricsDto(
    val baselineChunks: Int? = null,
    val filteredChunks: Int? = null,
    val avgSimilarityBefore: Float? = null,
    val avgSimilarityAfter: Float? = null,
    val tokensSaved: Int? = null,
    val filterApplied: Boolean = false,
    val strategy: String? = null
)

/**
 * DTO для результата сравнения
 */
@Serializable
data class ComparisonResponseDto(
    val question: String,
    val baseline: RAGQueryResponseDto? = null,  // RAG без фильтра
    val filtered: RAGQueryResponseDto? = null,  // RAG с фильтром
    val ragResponse: RAGQueryResponseDto? = null,  // Для обратной совместимости
    val standardResponse: StandardResponseDto? = null,
    val metrics: ComparisonMetricsDto? = null
)

/**
 * DTO для конфигурации фильтра
 */
@Serializable
data class FilterConfigDto(
    val enabled: Boolean,
    val strategy: String,  // "none" | "threshold" | "reranker" | "hybrid"
    val threshold: ThresholdConfigDto? = null
)

/**
 * DTO для конфигурации порога
 */
@Serializable
data class ThresholdConfigDto(
    val minSimilarity: Float,
    val keepTop: Int? = null
)

/**
 * DTO для запроса обновления конфигурации фильтра
 */
@Serializable
data class UpdateFilterConfigRequestDto(
    val strategy: String? = null,
    val minSimilarity: Float? = null,
    val keepTop: Int? = null
)

/**
 * DTO для запроса тестирования цитат
 */
@Serializable
data class CitationTestRequestDto(
    val questions: List<String>,
    val topK: Int = 5,
    val minSimilarity: Float = 0.4f,
    val applyFilter: Boolean = true,
    val strategy: String = "hybrid"
)

/**
 * DTO для результата теста одного вопроса
 */
@Serializable
data class CitationTestResultDto(
    val question: String,
    val hasCitations: Boolean,
    val citationsCount: Int,
    val validCitationsCount: Int,
    val answer: String,
    val citations: List<CitationDto>
)

/**
 * DTO для метрик тестирования
 */
@Serializable
data class CitationMetricsDto(
    val totalQuestions: Int,
    val questionsWithCitations: Int,
    val averageCitationsPerAnswer: Double,
    val validCitationsPercentage: Double,
    val answersWithoutHallucinations: Int
)

/**
 * DTO для отчёта о тестировании
 */
@Serializable
data class CitationTestReportDto(
    val results: List<CitationTestResultDto>,
    val metrics: CitationMetricsDto
)

