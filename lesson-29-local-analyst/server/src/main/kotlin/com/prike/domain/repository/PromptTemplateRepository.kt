package com.prike.domain.repository

import com.prike.domain.model.PromptTemplate

/**
 * Репозиторий для работы с шаблонами промптов
 */
class PromptTemplateRepository {
    
    /**
     * Получает шаблон по идентификатору
     */
    fun getTemplate(templateId: String): PromptTemplate? {
        return when (templateId) {
            "analyst" -> PromptTemplate(
                id = "analyst",
                name = "Аналитик данных",
                description = "Для анализа данных и ответов на аналитические вопросы",
                template = """Ты аналитик данных. Анализируй предоставленные данные и отвечай на вопросы точно и структурированно.

Данные:
{data}

Вопрос: {user_message}

Ответ (структурированный, с цифрами и фактами):""",
                systemPrompt = "Ты опытный аналитик данных. Твоя задача - анализировать данные и отвечать на вопросы точно, структурированно, с конкретными цифрами и фактами."
            )
            "default" -> PromptTemplate(
                id = "default",
                name = "По умолчанию",
                description = "Стандартный шаблон",
                template = "{user_message}",
                systemPrompt = null
            )
            else -> null
        }
    }
    
    /**
     * Получает все доступные шаблоны
     */
    fun getAllTemplates(): List<PromptTemplate> {
        return listOfNotNull(
            getTemplate("analyst"),
            getTemplate("default")
        )
    }
}
