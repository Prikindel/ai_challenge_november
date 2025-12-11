package com.prike.domain.model

/**
 * Шаблон промпта для разных задач
 * 
 * @param id Уникальный идентификатор шаблона
 * @param name Название шаблона
 * @param description Описание шаблона
 * @param template Шаблон с плейсхолдерами {user_message}, {context}, etc.
 * @param systemPrompt Системный промпт (опционально)
 */
data class PromptTemplate(
    val id: String,
    val name: String,
    val description: String,
    val template: String,
    val systemPrompt: String? = null
)

