package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Профиль пользователя для персонализации агента
 */
@Serializable
data class UserProfile(
    val id: String = "default",
    val name: String = "Пользователь",
    val preferences: UserPreferences,
    val workStyle: WorkStyle,
    val communicationStyle: CommunicationStyle,
    val context: UserContext
)

/**
 * Предпочтения пользователя
 */
@Serializable
data class UserPreferences(
    val language: String = "ru",  // ru, en
    val responseFormat: ResponseFormat = ResponseFormat.DETAILED,
    val timezone: String = "Europe/Moscow",
    val dateFormat: String = "dd.MM.yyyy"
)

/**
 * Формат ответа агента
 */
@Serializable
enum class ResponseFormat {
    BRIEF,      // Краткие ответы
    DETAILED,   // Подробные ответы
    STRUCTURED  // Структурированные (списки, таблицы)
}

/**
 * Стиль работы пользователя
 */
@Serializable
data class WorkStyle(
    val preferredWorkingHours: String? = null,  // "09:00-18:00"
    val focusAreas: List<String> = emptyList(),  // ["backend", "frontend", "devops"]
    val tools: List<String> = emptyList(),       // ["git", "docker", "kubernetes"]
    val projects: List<String> = emptyList(),    // Названия проектов
    val extraFields: Map<String, String> = emptyMap()  // Дополнительные параметры для расширяемости
)

/**
 * Стиль общения пользователя
 */
@Serializable
data class CommunicationStyle(
    val tone: Tone = Tone.PROFESSIONAL,  // PROFESSIONAL, CASUAL, FRIENDLY
    val detailLevel: DetailLevel = DetailLevel.MEDIUM,  // LOW, MEDIUM, HIGH
    val useExamples: Boolean = true,
    val useEmojis: Boolean = false
)

/**
 * Тон общения
 */
@Serializable
enum class Tone {
    PROFESSIONAL,  // Деловой стиль
    CASUAL,        // Неформальный
    FRIENDLY       // Дружелюбный
}

/**
 * Уровень детализации
 */
@Serializable
enum class DetailLevel {
    LOW,     // Минимум деталей
    MEDIUM,  // Средний уровень
    HIGH     // Максимум деталей
}

/**
 * Контекст пользователя
 */
@Serializable
data class UserContext(
    val currentProject: String? = null,
    val role: String? = null,  // "developer", "manager", "analyst"
    val team: String? = null,
    val goals: List<String> = emptyList()  // Цели пользователя
)

