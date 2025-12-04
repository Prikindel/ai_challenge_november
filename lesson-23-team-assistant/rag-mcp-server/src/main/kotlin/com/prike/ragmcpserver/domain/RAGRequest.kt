package com.prike.ragmcpserver.domain.model

/**
 * Запрос для RAG-запроса
 */
data class RAGRequest(
    val question: String,
    val topK: Int = 3,  // количество чанков для контекста
    val minSimilarity: Float = 0.7f  // минимальное сходство
)
