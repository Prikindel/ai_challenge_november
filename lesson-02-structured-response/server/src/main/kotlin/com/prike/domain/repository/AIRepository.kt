package com.prike.domain.repository

import com.prike.presentation.dto.ChatResponseResult

/**
 * Репозиторий для взаимодействия с AI сервисом
 * Интерфейс из доменного слоя (не зависит от реализации)
 */
interface AIRepository {
    /**
     * Получить структурированный JSON ответ от AI для энциклопедии животных
     * @param userMessage сообщение пользователя
     * @return результат со структурированным ответом от AI и debug информацией (JSON запрос и ответ от LLM)
     */
    suspend fun getStructuredAIResponse(userMessage: String): ChatResponseResult
}

