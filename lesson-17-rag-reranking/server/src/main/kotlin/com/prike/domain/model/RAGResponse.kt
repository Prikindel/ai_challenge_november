package com.prike.domain.model

import com.prike.domain.service.FilterStats

/**
 * Ответ от RAG-запроса
 */
data class RAGResponse(
    val question: String,
    val answer: String,
    val contextChunks: List<RetrievedChunk>,  // использованные чанки
    val tokensUsed: Int? = null,
    val filterStats: FilterStats? = null  // статистика фильтрации (если применён фильтр)
)
