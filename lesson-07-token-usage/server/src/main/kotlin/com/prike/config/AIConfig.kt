package com.prike.config

/**
 * Конфигурация AI API
 */
data class AIConfig(
    val apiKey: String,
    val apiUrl: String = "https://api.openai.com/v1/chat/completions",
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val requestTimeout: Int = 60,
    val systemPrompt: String? = null,
    val useJsonFormat: Boolean = false // Включить JSON режим для структурированных ответов
)

