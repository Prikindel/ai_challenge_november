package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Цитата из документа (для RAG)
 */
@Serializable
data class Citation(
    val text: String,
    val documentPath: String,
    val documentTitle: String,
    val chunkId: String? = null
)

