package com.prike.domain.repository

import com.prike.domain.entity.LLMCompletionResult

/**
 * Репозиторий для взаимодействия с AI сервисом.
 */
interface AIRepository {

    /**
     * Получить ответ от LLM с указанной температурой и форматом.
     *
     * @param prompt текст запроса к модели
     * @param temperature температура генерации
     * @param responseFormat формат ожидаемого ответа (текст или JSON)
     */
    suspend fun getCompletion(
        prompt: String,
        temperature: Double,
        responseFormat: AIResponseFormat = AIResponseFormat.TEXT
    ): LLMCompletionResult
}

/**
 * Формат ответа от LLM.
 */
enum class AIResponseFormat {
    TEXT,
    JSON_OBJECT
}

