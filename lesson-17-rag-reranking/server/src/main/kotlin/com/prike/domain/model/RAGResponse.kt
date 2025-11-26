package com.prike.domain.model

/**
 * Ответ от RAG-запроса
 */
data class RAGResponse(
    val question: String,
    val answer: String,
    val contextChunks: List<RetrievedChunk>,  // использованные чанки
    val tokensUsed: Int? = null
)
