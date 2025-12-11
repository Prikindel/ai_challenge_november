package com.prike.domain.service

import com.prike.domain.model.PromptTemplate
import com.prike.domain.repository.PromptTemplateRepository
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с шаблонами промптов
 */
class PromptTemplateService(
    private val repository: PromptTemplateRepository
) {
    private val logger = LoggerFactory.getLogger(PromptTemplateService::class.java)
    
    /**
     * Применяет шаблон к пользовательскому сообщению
     * 
     * @param templateId Идентификатор шаблона
     * @param userMessage Сообщение пользователя
     * @param context Контекст (опционально)
     * @return Сформированный промпт
     */
    fun applyTemplate(
        templateId: String,
        userMessage: String,
        context: String? = null
    ): String {
        val template = repository.getTemplate(templateId) 
            ?: repository.getTemplate("default") 
            ?: throw IllegalArgumentException("Template not found: $templateId and default template is missing")
        
        var result = template.template
        result = result.replace("{user_message}", userMessage)
        
        if (context != null) {
            result = result.replace("{context}", context)
        } else {
            // Удаляем плейсхолдер контекста, если он есть, но контекст не предоставлен
            result = result.replace("{context}", "")
        }
        
        logger.debug("Applied template '$templateId': ${result.take(200)}...")
        return result
    }
    
    /**
     * Получает шаблон по идентификатору
     */
    fun getTemplate(templateId: String): PromptTemplate? {
        return repository.getTemplate(templateId)
    }
    
    /**
     * Получает все доступные шаблоны
     */
    fun getAllTemplates(): List<PromptTemplate> {
        return repository.getAllTemplates()
    }
    
    /**
     * Получает системный промпт из шаблона (если есть)
     */
    fun getSystemPrompt(templateId: String): String? {
        val template = repository.getTemplate(templateId) ?: repository.getTemplate("default")
        return template?.systemPrompt
    }
}

