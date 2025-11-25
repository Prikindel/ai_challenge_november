package com.prike.domain.model

/**
 * Результат сравнения RAG и обычного режима
 */
data class ComparisonResult(
    val question: String,
    val ragResponse: RAGResponse,
    val standardResponse: StandardResponse
)

/**
 * Ответ в обычном режиме (без контекста)
 */
data class StandardResponse(
    val question: String,
    val answer: String,
    val tokensUsed: Int
)

