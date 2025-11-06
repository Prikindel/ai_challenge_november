package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * DTO для ответа от LLM в процессе сбора ТЗ
 * Всегда возвращается в формате JSON с полями status и content
 */
@Serializable
data class TZResponse(
    val status: String,  // "ready" или "continue"
    val content: String  // Если status="ready", то это JSON TechnicalSpec, иначе - текст сообщения
)

