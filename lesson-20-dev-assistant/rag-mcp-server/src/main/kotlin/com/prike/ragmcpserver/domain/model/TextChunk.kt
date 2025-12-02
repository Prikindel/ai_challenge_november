package com.prike.ragmcpserver.domain.model

/**
 * Модель чанка текста
 */
data class TextChunk(
    val id: String,
    val documentId: String,
    val content: String,
    val startIndex: Int,  // позиция начала в исходном документе
    val endIndex: Int,   // позиция конца в исходном документе
    val tokenCount: Int,
    val chunkIndex: Int  // порядковый номер чанка в документе
)

