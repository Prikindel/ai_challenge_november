package com.prike.domain.service

import com.prike.domain.model.*
import com.prike.data.repository.InteractionHistoryRepository
import org.slf4j.LoggerFactory

/**
 * Сервис для обучения персонализации на основе истории взаимодействий
 */
class PersonalizationLearningService(
    private val historyRepository: InteractionHistoryRepository
) {
    private val logger = LoggerFactory.getLogger(PersonalizationLearningService::class.java)
    
    /**
     * Анализирует предпочтения пользователя на основе истории взаимодействий
     */
    suspend fun analyzePreferences(userId: String): UserPreferences {
        val interactions = historyRepository.getRecentInteractions(userId, 50)
        val feedback = historyRepository.getFeedback(userId)
        
        logger.debug("Analyzing preferences for user $userId: ${interactions.size} interactions, ${feedback.size} feedback items")
        
        // Определение предпочтений на основе истории
        // Например: если пользователь часто просит краткие ответы → ResponseFormat.BRIEF
        
        // Анализируем длину ответов, на которые пользователь дал положительный фидбек
        val preferredResponseLength = analyzePreferredResponseLength(interactions, feedback)
        val responseFormat = when {
            preferredResponseLength < 300 -> ResponseFormat.BRIEF
            preferredResponseLength > 1000 -> ResponseFormat.DETAILED
            else -> ResponseFormat.DETAILED  // По умолчанию
        }
        
        // Анализируем стиль общения на основе взаимодействий
        // Здесь можно добавить более сложную логику
        
        // Возвращаем предпочтения (можно расширить анализ)
        return UserPreferences(
            language = "ru",  // Пока используем значение по умолчанию
            responseFormat = responseFormat,
            timezone = "Europe/Moscow",
            dateFormat = "dd.MM.yyyy"
        )
    }
    
    /**
     * Анализирует предпочтительную длину ответов на основе фидбека
     */
    private fun analyzePreferredResponseLength(
        interactions: List<com.prike.domain.model.InteractionHistory>,
        feedback: List<com.prike.domain.model.Feedback>
    ): Int {
        // Находим взаимодействия с положительным фидбеком (rating >= 4)
        val positiveInteractions = interactions.filter { interaction ->
            interaction.feedback?.rating?.let { it >= 4 } ?: false
        }
        
        if (positiveInteractions.isEmpty()) {
            return 500  // Среднее значение по умолчанию
        }
        
        // Вычисляем среднюю длину положительных ответов
        val avgLength = positiveInteractions
            .map { it.answer.length }
            .average()
            .toInt()
        
        return avgLength
    }
    
    /**
     * Предлагает обновления профиля на основе истории взаимодействий
     */
    suspend fun suggestProfileUpdates(userId: String): Map<String, Any> {
        val preferences = analyzePreferences(userId)
        val interactions = historyRepository.getRecentInteractions(userId, 50)
        
        val suggestions = mutableMapOf<String, Any>()
        
        // Предлагаем обновить формат ответа, если анализ показал предпочтение
        suggestions["responseFormat"] = preferences.responseFormat.name.lowercase()
        
        // Можно добавить другие предложения на основе анализа
        
        return suggestions
    }
}

