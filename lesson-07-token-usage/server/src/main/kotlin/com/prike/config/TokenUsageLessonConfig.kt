package com.prike.config

data class TokenUsageLessonConfig(
    val promptTokenLimit: Int,
    val defaultMaxResponseTokens: Int,
    val historyLimit: Int = 10,
    val tokenEncoding: String = "cl100k_base",
    val scenarios: List<TokenUsageScenarioConfig>
)

data class TokenUsageScenarioConfig(
    val id: String,
    val name: String,
    val defaultPrompt: String,
    val description: String? = null,
    val temperature: Double? = null,
    val maxResponseTokens: Int? = null
)
