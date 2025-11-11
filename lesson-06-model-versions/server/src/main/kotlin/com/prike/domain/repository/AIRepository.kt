package com.prike.domain.repository

import com.prike.domain.entity.LLMCompletionResult

interface AIRepository {
    suspend fun getCompletion(request: ModelInvocationRequest): LLMCompletionResult
}

enum class AIResponseFormat {
    TEXT,
    JSON_OBJECT
}

data class ModelInvocationRequest(
    val prompt: String,
    val modelId: String,
    val endpoint: String,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null,
    val responseFormat: AIResponseFormat = AIResponseFormat.TEXT,
    val additionalParams: Map<String, Any?> = emptyMap()
)
