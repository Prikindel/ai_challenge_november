package com.prike.domain.model

/**
 * Ответ с извлечёнными цитатами
 */
data class AnswerWithCitations(
    val answer: String,
    val citations: List<Citation>,
    val rawAnswer: String = answer
)

