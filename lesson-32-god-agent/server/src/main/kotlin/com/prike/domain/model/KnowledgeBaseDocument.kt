package com.prike.domain.model

import java.time.Instant

/**
 * Документ в базе знаний
 */
data class KnowledgeBaseDocument(
    val id: String,
    val filePath: String,
    val category: DocumentCategory,
    val title: String,
    val content: String,
    val indexedAt: Instant = Instant.now()
)

/**
 * Чанк документа для RAG поиска
 */
data class DocumentChunk(
    val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val content: String,
    val embedding: List<Float>,
    val category: DocumentCategory,
    val source: String, // Путь к файлу
    val startIndex: Int = 0,
    val endIndex: Int = 0
)

/**
 * Результат поиска в базе знаний
 */
data class RetrievedChunk(
    val id: String,
    val documentId: String,
    val text: String,
    val similarity: Double,
    val source: String,
    val category: DocumentCategory,
    val chunkIndex: Int
)

