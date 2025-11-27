package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для списка документов
 */
@Serializable
data class DocumentsListResponse(
    val documents: List<DocumentInfo>
)

/**
 * DTO для информации о документе
 */
@Serializable
data class DocumentInfo(
    val id: String,
    val filePath: String,
    val title: String?,
    val indexedAt: Long,
    val chunkCount: Int
)

