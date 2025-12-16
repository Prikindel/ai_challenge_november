package com.voiceagent.config

/**
 * Конфигурация AI API
 */
data class AIConfig(
    val apiKey: String? = null,
    val apiUrl: String = "https://api.openai.com/v1/chat/completions",
    val model: String = "gpt-3.5-turbo",
    val temperature: Double = 0.7,
    val maxTokens: Int = 500,
    val requestTimeout: Int = 60,
    val systemPrompt: String? = null,
    val authType: String? = null,
    val authUser: String? = null,
    val authPassword: String? = null
)

