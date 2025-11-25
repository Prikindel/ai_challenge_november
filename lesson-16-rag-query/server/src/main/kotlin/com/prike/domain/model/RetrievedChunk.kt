package com.prike.domain.model

/**
 * Извлечённый чанк из базы знаний для использования в RAG
 */
data class RetrievedChunk(
    val chunkId: String,
    val documentId: String,
    val documentPath: String?,
    val documentTitle: String?,
    val content: String,
    val similarity: Float,
    val chunkIndex: Int
)
