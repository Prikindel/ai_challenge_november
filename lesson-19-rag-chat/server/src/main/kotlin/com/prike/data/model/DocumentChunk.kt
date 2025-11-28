package com.prike.data.model

/**
 * Модель чанка документа с эмбеддингом
 */
data class DocumentChunk(
    val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val content: String,
    val startIndex: Int,
    val endIndex: Int,
    val tokenCount: Int,
    val embedding: List<Float>,  // нормализованный вектор
    val createdAt: Long
)

