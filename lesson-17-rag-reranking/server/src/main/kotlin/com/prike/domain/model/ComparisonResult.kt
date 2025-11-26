package com.prike.domain.model

/**
 * Результат сравнения разных режимов
 */
data class ComparisonResult(
    val question: String,
    val baseline: RAGResponse? = null,  // RAG без фильтра (если сравниваем с фильтром)
    val filtered: RAGResponse? = null,  // RAG с фильтром
    val standardResponse: StandardResponse? = null,  // Обычный режим (без RAG)
    val metrics: ComparisonMetrics? = null  // Метрики сравнения
)

/**
 * Метрики сравнения режимов
 */
data class ComparisonMetrics(
    val baselineChunks: Int? = null,  // Количество чанков в baseline
    val filteredChunks: Int? = null,  // Количество чанков после фильтрации
    val avgSimilarityBefore: Float? = null,  // Среднее сходство до фильтрации
    val avgSimilarityAfter: Float? = null,  // Среднее сходство после фильтрации
    val tokensSaved: Int? = null,  // Экономия токенов
    val filterApplied: Boolean = false,  // Применён ли фильтр
    val strategy: String? = null  // Использованная стратегия
)

/**
 * Ответ в обычном режиме (без контекста)
 */
data class StandardResponse(
    val question: String,
    val answer: String,
    val tokensUsed: Int
)

