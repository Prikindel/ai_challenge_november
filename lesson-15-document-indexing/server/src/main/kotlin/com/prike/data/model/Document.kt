package com.prike.data.model

/**
 * Модель документа в базе знаний
 */
data class Document(
    val id: String,
    val filePath: String,
    val title: String?,
    val content: String,
    val indexedAt: Long,
    val chunkCount: Int
)

