package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Извлеченный чанк из базы знаний
 */
@Serializable
data class RetrievedChunk(
    val chunkId: String,
    val documentPath: String?,
    val documentTitle: String?,
    val content: String,
    val similarity: Float = 0.0f,
    val chunkIndex: Int = 0
)

