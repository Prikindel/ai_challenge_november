package com.prike.domain.model

import com.prike.domain.service.FilterStats
import com.prike.domain.service.RerankDecision

/**
 * Ответ от RAG-запроса
 */
data class RAGResponse(
    val question: String,
    val answer: String,
    val contextChunks: List<RetrievedChunk>,  // использованные чанки
    val tokensUsed: Int? = null,
    val filterStats: FilterStats? = null,  // статистика фильтрации (если применён фильтр)
    val rerankInsights: List<RerankDecision>? = null,  // решения реранкера (если применён реранкер)
    val citations: List<Citation> = emptyList()  // извлечённые цитаты из ответа
)
