package com.prike.ragmcpserver.domain.model

import kotlinx.serialization.Serializable

/**
 * Результат теста для одного вопроса
 */
@Serializable
data class CitationTestResult(
    val question: String,
    val hasCitations: Boolean,
    val citationsCount: Int,
    val validCitationsCount: Int,
    val answer: String,
    val citations: List<Citation>
)

/**
 * Метрики тестирования цитат
 */
@Serializable
data class CitationMetrics(
    val totalQuestions: Int,
    val questionsWithCitations: Int,
    val averageCitationsPerAnswer: Double,
    val validCitationsPercentage: Double,
    val answersWithoutHallucinations: Int
)

/**
 * Отчёт о тестировании цитат
 */
@Serializable
data class CitationTestReport(
    val results: List<CitationTestResult>,
    val metrics: CitationMetrics
)

