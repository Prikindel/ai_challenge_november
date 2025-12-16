package com.voiceagent.domain.repository

import com.voiceagent.domain.entity.ChatMessage

/**
 * Репозиторий для взаимодействия с AI сервисом
 * Интерфейс из доменного слоя (не зависит от реализации)
 */
interface AIRepository {
    /**
     * Получить ответ от AI на пользовательское сообщение
     * @param userMessage сообщение пользователя
     * @return ответ от AI
     */
    suspend fun getAIResponse(userMessage: String): String
}

