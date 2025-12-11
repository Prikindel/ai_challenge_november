package com.prike.domain.repository

import com.prike.domain.model.PromptTemplate

/**
 * Репозиторий для работы с шаблонами промптов
 */
interface PromptTemplateRepository {
    fun getTemplate(id: String): PromptTemplate?
    fun getAllTemplates(): List<PromptTemplate>
    fun addTemplate(template: PromptTemplate)
}

/**
 * In-memory реализация репозитория шаблонов
 */
class InMemoryPromptTemplateRepository : PromptTemplateRepository {
    private val templates = mutableMapOf<String, PromptTemplate>()
    
    init {
        // Предустановленные шаблоны
        addTemplate(PromptTemplate(
            id = "default",
            name = "По умолчанию",
            description = "Стандартный промпт",
            template = "{user_message}"
        ))
        
        addTemplate(PromptTemplate(
            id = "code_assistant",
            name = "Ассистент кода",
            description = "Для вопросов о программировании",
            template = """Ты опытный программист. Отвечай кратко и по делу.

Вопрос: {user_message}

Ответ:""",
            systemPrompt = "Ты помощник для программирования. Отвечай на русском языке."
        ))
        
        addTemplate(PromptTemplate(
            id = "qa_assistant",
            name = "Вопрос-ответ",
            description = "Для точных ответов на вопросы",
            template = """Используй следующую информацию для ответа:

{context}

Вопрос: {user_message}

Ответ (только факты, без выдумывания):"""
        ))
    }
    
    override fun getTemplate(id: String): PromptTemplate? {
        return templates[id]
    }
    
    override fun getAllTemplates(): List<PromptTemplate> {
        return templates.values.toList()
    }
    
    override fun addTemplate(template: PromptTemplate) {
        templates[template.id] = template
    }
}

