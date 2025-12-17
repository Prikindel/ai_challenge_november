package com.prike.domain.service

import com.prike.domain.model.*
import com.prike.data.repository.UserProfileRepository
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с профилем пользователя и персонализации промптов
 */
class UserProfileService(
    private val repository: UserProfileRepository
) {
    private val logger = LoggerFactory.getLogger(UserProfileService::class.java)
    /**
     * Сохранить профиль
     */
    fun saveProfile(profile: UserProfile) {
        repository.saveProfile(profile)
    }
    /**
     * Получить профиль пользователя
     */
    fun getProfile(userId: String = "default"): UserProfile {
        return repository.getProfile(userId)
    }
    
    /**
     * Построить персонализированный промпт на основе базового промпта и профиля
     */
    fun buildPersonalizedPrompt(
        basePrompt: String,
        userId: String = "default"
    ): String {
        val profile = getProfile(userId)
        return personalizePrompt(basePrompt, profile)
    }
    
    /**
     * Персонализация промпта на основе профиля пользователя
     */
    private fun personalizePrompt(
        prompt: String,
        profile: UserProfile
    ): String {
        val personalization = buildString {
            appendLine("Контекст пользователя:")
            appendLine("- Имя: ${profile.name}")
            if (profile.context.currentProject != null) {
                appendLine("- Текущий проект: ${profile.context.currentProject}")
            }
            if (profile.context.role != null) {
                appendLine("- Роль: ${profile.context.role}")
            }
            if (profile.workStyle.focusAreas.isNotEmpty()) {
                appendLine("- Области интересов: ${profile.workStyle.focusAreas.joinToString(", ")}")
            }
            // Добавляем дополнительные поля из workStyle, если они есть
            if (profile.workStyle.extraFields.isNotEmpty()) {
                profile.workStyle.extraFields.forEach { (key, value) ->
                    appendLine("- ${key}: $value")
                }
            }
            if (profile.communicationStyle.tone != Tone.PROFESSIONAL) {
                appendLine("- Стиль общения: ${profile.communicationStyle.tone.name.lowercase()}")
            }
            appendLine("- Формат ответа: ${profile.preferences.responseFormat.name.lowercase()}")
            appendLine("- Уровень детализации: ${profile.communicationStyle.detailLevel.name.lowercase()}")
            
            // Добавляем информацию о предпочтениях стиля общения
            if (profile.communicationStyle.useExamples) {
                appendLine("- Используй примеры при объяснении")
            }
            if (profile.communicationStyle.useEmojis) {
                appendLine("- Можно использовать эмодзи для выразительности")
            }
        }
        
        val personalizedPrompt = """
        $personalization
        
        $prompt
        
        Учти предпочтения пользователя при ответе.
        """.trimIndent()
        
        // Логируем персонализацию для отладки
        logger.debug("Added personalization to prompt:\n$personalization")
        
        return personalizedPrompt
    }
    
    /**
     * Получить настройки для персонализации на основе профиля
     */
    fun getPersonalizationSettings(userId: String = "default"): PersonalizationSettings {
        val profile = getProfile(userId)
        return PersonalizationSettings(
            maxTokens = getMaxTokensForFormat(profile.preferences.responseFormat),
            temperature = getTemperatureForDetailLevel(profile.communicationStyle.detailLevel),
            tone = profile.communicationStyle.tone,
            useExamples = profile.communicationStyle.useExamples,
            useEmojis = profile.communicationStyle.useEmojis
        )
    }
    
    private fun getMaxTokensForFormat(format: ResponseFormat): Int {
        return when (format) {
            ResponseFormat.BRIEF -> 512
            ResponseFormat.DETAILED -> 2048
            ResponseFormat.STRUCTURED -> 1024
        }
    }
    
    private fun getTemperatureForDetailLevel(level: DetailLevel): Double {
        return when (level) {
            DetailLevel.LOW -> 0.5  // Более детерминированные ответы
            DetailLevel.MEDIUM -> 0.7
            DetailLevel.HIGH -> 0.8  // Более разнообразные ответы
        }
    }
}

/**
 * Настройки персонализации для генерации ответа
 */
data class PersonalizationSettings(
    val maxTokens: Int,
    val temperature: Double,
    val tone: Tone,
    val useExamples: Boolean,
    val useEmojis: Boolean
)

