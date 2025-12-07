package com.prike.domain.model

/**
 * Цитата из документа (для RAG)
 */
data class Citation(
    val text: String,
    val documentPath: String,
    val documentTitle: String,
    val chunkId: String? = null
)

