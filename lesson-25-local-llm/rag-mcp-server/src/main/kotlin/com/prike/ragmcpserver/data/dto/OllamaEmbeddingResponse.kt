package com.prike.ragmcpserver.data.dto

import kotlinx.serialization.Serializable

/**
 * Запрос на генерацию эмбеддинга через Ollama API
 */
@Serializable
data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String
)

/**
 * Ответ от Ollama API с эмбеддингом
 */
@Serializable
data class OllamaEmbeddingResponse(
    val embedding: List<Float>
)

