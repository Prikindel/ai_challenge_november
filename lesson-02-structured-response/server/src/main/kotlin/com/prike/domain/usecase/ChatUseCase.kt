package com.prike.domain.usecase

import com.prike.data.dto.AnimalEncyclopediaResponse
import com.prike.domain.exception.AIServiceException
import com.prike.domain.exception.ValidationException
import com.prike.domain.repository.AIRepository

/**
 * Use case для обработки чат-сообщений энциклопедии животных
 */
class ChatUseCase(
    private val aiRepository: AIRepository
) {
    /**
     * Обработать сообщение пользователя и получить структурированный JSON ответ от AI
     * @param userMessage текст сообщения пользователя
     * @return структурированный ответ от AI (энциклопедия животных)
     * @throws ValidationException если сообщение невалидно
     * @throws AIServiceException если произошла ошибка при обращении к AI или парсинге JSON
     */
    suspend fun processMessage(userMessage: String): AnimalEncyclopediaResponse {
        validateMessage(userMessage)

        return try {
            aiRepository.getStructuredAIResponse(userMessage.trim())
        } catch (e: Exception) {
            throw AIServiceException(
                "Не удалось получить структурированный ответ от AI: ${e.message}",
                e
            )
        }
    }
    
    /**
     * Валидация сообщения пользователя
     */
    private fun validateMessage(message: String) {
        when {
            message.isBlank() -> throw ValidationException("Сообщение не может быть пустым")
            message.length > 2000 -> throw ValidationException(
                "Сообщение слишком длинное (максимум 2000 символов)"
            )
        }
    }
}

