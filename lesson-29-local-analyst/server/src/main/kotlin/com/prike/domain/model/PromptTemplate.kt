package com.prike.domain.model

/**
 * Шаблон промпта для LLM
 */
data class PromptTemplate(
    val id: String,
    val name: String,
    val description: String,
    val template: String,
    val systemPrompt: String? = null
)
