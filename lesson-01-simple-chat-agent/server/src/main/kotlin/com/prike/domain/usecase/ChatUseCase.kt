package com.prike.domain.usecase

import com.prike.domain.entity.ChatMessage
import com.prike.domain.entity.MessageRole
import com.prike.domain.exception.AIServiceException
import com.prike.domain.exception.ValidationException
import com.prike.domain.repository.AIRepository

/**
 * Use case для обработки чат-сообщений
 */
class ChatUseCase(
    private val aiRepository: AIRepository
) {
    /**
     * Обработать сообщение пользователя и получить ответ от AI
     * @param userMessage текст сообщения пользователя
     * @return ответ от AI
     * @throws ValidationException если сообщение невалидно
     * @throws AIServiceException если произошла ошибка при обращении к AI
     */
    suspend fun processMessage(userMessage: String): String {
        // Валидация входных данных
        validateMessage(userMessage)
        
        // Преобразуем в доменную сущность
        val message = ChatMessage(
            content = userMessage.trim(),
            role = MessageRole.USER
        )
        
        // Получаем ответ от AI через репозиторий
        return try {
            aiRepository.getAIResponse(message.content)
        } catch (e: Exception) {
            throw AIServiceException(
                "Не удалось получить ответ от AI: ${e.message}",
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

