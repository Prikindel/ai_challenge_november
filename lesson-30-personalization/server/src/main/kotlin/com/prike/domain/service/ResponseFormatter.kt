package com.prike.domain.service

import com.prike.domain.model.ResponseFormat
import org.slf4j.LoggerFactory

/**
 * Сервис для форматирования ответов агента под предпочтения пользователя
 */
class ResponseFormatter(
    private val userProfileService: UserProfileService
) {
    private val logger = LoggerFactory.getLogger(ResponseFormatter::class.java)
    
    /**
     * Форматирует ответ на основе профиля пользователя
     */
    fun formatResponse(
        response: String,
        userId: String = "default"
    ): String {
        val profile = userProfileService.getProfile(userId)
        
        return when (profile.preferences.responseFormat) {
            ResponseFormat.BRIEF -> formatBrief(response)
            ResponseFormat.DETAILED -> response
            ResponseFormat.STRUCTURED -> formatStructured(response)
        }
    }
    
    /**
     * Форматирует ответ в краткий формат (только ключевые моменты)
     */
    private fun formatBrief(text: String): String {
        // Разбиваем на предложения
        val sentences = text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        // Берем первые 3 предложения или первые 200 символов
        if (sentences.size <= 3) {
            return text
        }
        
        val briefText = sentences.take(3).joinToString(". ") + "."
        
        // Если текст все еще слишком длинный, обрезаем до 200 символов
        return if (briefText.length > 200) {
            briefText.take(197) + "..."
        } else {
            briefText
        }
    }
    
    /**
     * Форматирует ответ в структурированный формат (списки, таблицы)
     */
    private fun formatStructured(text: String): String {
        // Простое форматирование в списки
        // В реальном приложении можно использовать более сложную логику
        
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (lines.size <= 1) {
            return text
        }
        
        // Если есть пронумерованные элементы или список, сохраняем их
        val result = StringBuilder()
        var inList = false
        
        for (line in lines) {
            // Проверяем, является ли строка элементом списка
            val isListItem = line.matches(Regex("^[-•*]\\s+.*")) ||
                    line.matches(Regex("^\\d+[.)]\\s+.*")) ||
                    line.matches(Regex("^[-]\\s+.*"))
            
            if (isListItem) {
                if (!inList) {
                    result.append("\n")
                    inList = true
                }
                result.append(line).append("\n")
            } else {
                if (inList) {
                    inList = false
                }
                result.append(line).append("\n")
            }
        }
        
        return result.toString().trim()
    }
    
    /**
     * Добавляет форматирование на основе стиля общения
     */
    fun applyCommunicationStyle(
        text: String,
        userId: String = "default"
    ): String {
        val profile = userProfileService.getProfile(userId)
        
        var result = text
        
        // Добавляем примеры, если нужно
        if (profile.communicationStyle.useExamples && !text.contains("например") && !text.contains("например,")) {
            // В реальном приложении можно добавить логику для вставки примеров
            // Здесь просто возвращаем исходный текст
        }
        
        // Эмодзи уже должны быть добавлены через промпт, но можно добавить дополнительную обработку
        // здесь не изменяем текст, так как эмодзи уже могут быть в ответе от LLM
        
        return result
    }
}

