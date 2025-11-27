package com.prike.domain.model

/**
 * Цитата из ответа LLM
 */
data class Citation(
    /**
     * Полный текст цитаты, как он появился в ответе
     */
    val text: String,
    
    /**
     * Путь к документу
     */
    val documentPath: String,
    
    /**
     * Название документа
     */
    val documentTitle: String,
    
    /**
     * ID чанка, если известен
     */
    val chunkId: String? = null,
    
    /**
     * Позиция цитаты в ответе (индексы начала и конца)
     */
    val position: TextPosition? = null
)

/**
 * Позиция текста в ответе
 */
data class TextPosition(
    /**
     * Начальная позиция (индекс первого символа)
     */
    val start: Int,
    
    /**
     * Конечная позиция (индекс символа после последнего)
     */
    val end: Int
)

