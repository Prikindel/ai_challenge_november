package com.prike.ragmcpserver.domain.model

/**
 * Ответ LLM с извлечёнными цитатами
 */
data class AnswerWithCitations(
    /**
     * Очищенный ответ (без цитат в исходном формате, но с markdown ссылками)
     */
    val answer: String,
    
    /**
     * Список извлечённых цитат
     */
    val citations: List<Citation>,
    
    /**
     * Оригинальный ответ от LLM (без изменений)
     */
    val rawAnswer: String
)

