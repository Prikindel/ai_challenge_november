package com.prike.ragmcpserver.domain.model

import kotlinx.serialization.Serializable

/**
 * Цитата из ответа LLM
 */
@Serializable
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
@Serializable
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

