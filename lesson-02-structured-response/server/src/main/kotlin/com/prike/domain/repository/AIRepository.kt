package com.prike.domain.repository

import com.prike.data.dto.AnimalEncyclopediaResponse

/**
 * Репозиторий для взаимодействия с AI сервисом
 * Интерфейс из доменного слоя (не зависит от реализации)
 */
interface AIRepository {
    /**
     * Получить структурированный JSON ответ от AI для энциклопедии животных
     * @param userMessage сообщение пользователя
     * @return структурированный ответ от AI
     */
    suspend fun getStructuredAIResponse(userMessage: String): AnimalEncyclopediaResponse
}

