package com.prike.domain.service

import com.prike.domain.model.UserProfile
import com.prike.domain.model.Tone
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с шаблонами промптов с учетом персонализации
 */
class PromptTemplateService(
    private val userProfileService: UserProfileService
) {
    private val logger = LoggerFactory.getLogger(PromptTemplateService::class.java)
    
    /**
     * Применяет шаблон промпта с учетом персонализации профиля
     */
    fun applyTemplate(
        templateId: String,
        userMessage: String,
        context: String? = null,
        userId: String = "default"
    ): String {
        val template = getTemplate(templateId)
        val profile = userProfileService.getProfile(userId)
        
        var result = template.replace("{user_message}", userMessage)
        if (context != null) {
            result = result.replace("{context}", context)
        }
        
        // Добавляем персонализацию
        result = addPersonalization(result, profile)
        
        return result
    }
    
    /**
     * Получить шаблон по ID
     */
    private fun getTemplate(templateId: String): String {
        // Простые шаблоны - в реальном приложении можно хранить в БД или конфиге
        return when (templateId) {
            "default" -> """
                {context}
                
                Текущий вопрос пользователя:
                {user_message}
            """.trimIndent()
            "analysis" -> """
                {context}
                
                Проанализируй следующий запрос пользователя:
                {user_message}
                
                Предоставь детальный анализ.
            """.trimIndent()
            "chat" -> """
                {context}
                
                Пользователь спрашивает:
                {user_message}
                
                Ответь на вопрос пользователя.
            """.trimIndent()
            else -> getTemplate("default")
        }
    }
    
    /**
     * Добавляет персонализацию к промпту
     */
    private fun addPersonalization(
        prompt: String,
        profile: UserProfile
    ): String {
        val personalization = buildString {
            appendLine("\nПерсонализация:")
            appendLine("- Имя пользователя: ${profile.name}")
            if (profile.context.currentProject != null) {
                appendLine("- Работаю над проектом: ${profile.context.currentProject}")
            }
            if (profile.workStyle.focusAreas.isNotEmpty()) {
                appendLine("- Интересуюсь: ${profile.workStyle.focusAreas.joinToString(", ")}")
            }
            appendLine("- Предпочитаю ${profile.preferences.responseFormat.name.lowercase()} ответы")
            appendLine("- Стиль общения: ${profile.communicationStyle.tone.name.lowercase()}")
            
            // Добавляем тон общения в инструкции
            when (profile.communicationStyle.tone) {
                Tone.PROFESSIONAL -> appendLine("- Используй профессиональный деловой стиль")
                Tone.CASUAL -> appendLine("- Используй неформальный повседневный стиль")
                Tone.FRIENDLY -> appendLine("- Используй дружелюбный теплый стиль общения")
            }
            
            if (profile.communicationStyle.useExamples) {
                appendLine("- Используй примеры для иллюстрации объяснений")
            }
            if (profile.communicationStyle.useEmojis) {
                appendLine("- Можно использовать эмодзи для выразительности")
            }
        }
        
        return "$prompt\n$personalization"
    }
}

