package com.prike.domain.agent

/**
 * Базовый интерфейс для агентов, работающих с LLM
 * Определяет общий контракт для всех агентов
 */
interface BaseAgent {
    /**
     * Обработать сообщение пользователя и получить ответ от LLM
     * @param userMessage сообщение пользователя
     * @return ответ от LLM (сырой текст или структурированные данные)
     */
    suspend fun processMessage(userMessage: String): String
}

