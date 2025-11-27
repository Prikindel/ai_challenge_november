package com.prike.domain.agent

import com.prike.data.repository.AIRepository

/**
 * Базовый абстрактный класс для агентов, работающих с LLM
 * Использует AIRepository для получения данных от LLM
 */
abstract class BaseAgent(
    protected val aiRepository: AIRepository
) {
    /**
     * Получить ответ от LLM по сообщению пользователя
     */
    suspend fun getMessage(userMessage: String): String {
        return aiRepository.getMessage(userMessage)
    }
}

