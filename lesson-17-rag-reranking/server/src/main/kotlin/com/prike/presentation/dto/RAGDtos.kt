package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для RAG-запроса
 */
@Serializable
data class RAGQueryRequestDto(
    val question: String,
    val topK: Int = 3,
    val minSimilarity: Float = 0.7f
)

/**
 * DTO для извлечённого чанка
 */
@Serializable
data class RetrievedChunkDto(
    val chunkId: String,
    val documentPath: String?,
    val documentTitle: String?,
    val content: String,
    val similarity: Float,
    val chunkIndex: Int
)

/**
 * DTO для RAG-ответа
 */
@Serializable
data class RAGQueryResponseDto(
    val question: String,
    val answer: String,
    val contextChunks: List<RetrievedChunkDto>,
    val tokensUsed: Int? = null
)

/**
 * DTO для обычного ответа
 */
@Serializable
data class StandardResponseDto(
    val question: String,
    val answer: String,
    val tokensUsed: Int
)

/**
 * DTO для результата сравнения
 */
@Serializable
data class ComparisonResponseDto(
    val question: String,
    val ragResponse: RAGQueryResponseDto,
    val standardResponse: StandardResponseDto
)

