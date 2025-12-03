package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Тип проблемы в ревью кода
 */
@Serializable
enum class ReviewIssueType {
    BUG,           // Потенциальные баги (утечки ресурсов, null checks)
    SECURITY,      // Проблемы безопасности (SQL injection, XSS)
    PERFORMANCE,   // Проблемы производительности (неоптимальные алгоритмы)
    STYLE,         // Стиль кода (именование, длина функций)
    LOGIC,         // Логические ошибки (неправильная бизнес-логика)
    DOCUMENTATION  // Документация (отсутствие комментариев)
}

/**
 * Проблема, найденная в коде
 */
@Serializable
data class ReviewIssue(
    val type: ReviewIssueType,
    val severity: String,  // "critical", "high", "medium", "low"
    val file: String,      // Путь к файлу
    val line: Int? = null, // Номер строки (если известен)
    val message: String,   // Описание проблемы
    val suggestion: String? = null  // Предложение по исправлению
)

/**
 * Предложение по улучшению кода
 */
@Serializable
data class ReviewSuggestion(
    val file: String,      // Путь к файлу
    val line: Int? = null, // Номер строки (если известен)
    val message: String,   // Описание предложения
    val priority: String   // "high", "medium", "low"
)

/**
 * Результат ревью кода
 */
@Serializable
data class CodeReview(
    val reviewId: String,              // Уникальный ID ревью
    val baseBranch: String,            // Базовая ветка
    val headBranch: String,             // Целевая ветка
    val changedFiles: List<String>,     // Список изменённых файлов
    val issues: List<ReviewIssue>,      // Найденные проблемы
    val suggestions: List<ReviewSuggestion>, // Предложения по улучшению
    val summary: String,                // Общее резюме ревью
    val overallScore: String? = null,   // Общая оценка ("approve", "request_changes", "comment")
    val timestamp: Long = System.currentTimeMillis() // Время создания ревью
)

