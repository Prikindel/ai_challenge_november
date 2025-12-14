package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Параметры для настройки локальной LLM
 * 
 * @param temperature Креативность генерации (0.0-2.0, по умолчанию 0.7)
 * @param maxTokens Максимальная длина ответа в токенах (по умолчанию 2048)
 * @param topP Nucleus sampling (0.0-1.0, по умолчанию 0.9)
 * @param topK Top-k sampling (по умолчанию 40)
 * @param repeatPenalty Штраф за повторения (по умолчанию 1.1)
 * @param contextWindow Размер контекстного окна (по умолчанию 4096)
 * @param seed Seed для воспроизводимости результатов (null = случайный)
 */
@Serializable
data class LLMParameters(
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val topP: Double = 0.9,
    val topK: Int = 40,
    val repeatPenalty: Double = 1.1,
    val contextWindow: Int = 4096,
    val seed: Int? = null
)
