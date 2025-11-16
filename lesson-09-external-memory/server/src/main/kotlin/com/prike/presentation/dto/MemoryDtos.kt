package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для API работы с внешней памятью
 */

// Запросы
@Serializable
data class SendMessageRequest(
    val message: String
)

// Ответы
@Serializable
data class SendMessageResponse(
    val message: String,
    val usage: UsageDto? = null
)

@Serializable
data class HistoryResponse(
    val history: List<HistoryEntryDto>
)

@Serializable
data class HistoryEntryDto(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val metadata: MemoryMetadataDto? = null
)

@Serializable
data class MemoryMetadataDto(
    val model: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

@Serializable
data class UsageDto(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

@Serializable
data class StatsResponse(
    val totalEntries: Int,
    val userMessages: Int,
    val assistantMessages: Int,
    val oldestEntry: Long?,
    val newestEntry: Long?,
    val storageType: String? = null
)

@Serializable
data class SuccessResponse(
    val message: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

