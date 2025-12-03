package com.prike.domain.service

import com.prike.domain.model.SupportContext
import org.slf4j.LoggerFactory

/**
 * Построитель промптов для поддержки пользователей
 * Формирует системные и пользовательские промпты для ответов на вопросы поддержки
 */
class SupportPromptBuilder {
    private val logger = LoggerFactory.getLogger(SupportPromptBuilder::class.java)
    
    /**
     * Результат построения промпта для поддержки
     */
    data class SupportPromptResult(
        val systemPrompt: String,
        val userPrompt: String
    )
    
    /**
     * Формирует промпт для поддержки пользователя
     * 
     * @param question вопрос пользователя
     * @param context контекст поддержки (пользователь, тикет, RAG-контекст)
     * @return системный и пользовательский промпты
     */
    fun buildSupportPrompt(
        question: String,
        context: SupportContext
    ): SupportPromptResult {
        logger.debug("Building support prompt for question: ${question.take(100)}...")
        
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(question, context)
        
        return SupportPromptResult(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
    }
    
    /**
     * Формирует системный промпт с ролью support agent
     */
    fun buildSystemPrompt(): String {
        return """
            Ты — опытный агент поддержки, который помогает пользователям решать проблемы.
            
            Твоя задача:
            - Отвечать на вопросы пользователей о продукте
            - Использовать документацию и FAQ для точных ответов
            - Учитывать контекст тикета (история обращений, статус пользователя)
            - Предлагать конкретные шаги для решения проблемы
            - Быть вежливым и профессиональным
            - Если проблема требует дополнительной помощи, предложи создать тикет или связаться с поддержкой
            
            Правила ответа:
            - Отвечай на русском языке
            - Будь кратким и по делу
            - Используй информацию из документации для точных ответов
            - Если в документации нет ответа, честно скажи об этом
            - Предлагай конкретные шаги для решения проблемы
            - Учитывай статус пользователя и историю тикета при ответе
            - Если пользователь уже обращался с этой проблемой, учитывай предыдущие ответы
        """.trimIndent()
    }
    
    /**
     * Формирует пользовательский промпт с вопросом и контекстом
     */
    fun buildUserPrompt(question: String, context: SupportContext): String {
        val prompt = StringBuilder()
        
        // Вопрос пользователя
        prompt.appendLine("Вопрос пользователя: $question")
        prompt.appendLine()
        
        // Контекст пользователя
        if (context.user != null) {
            prompt.appendLine("Контекст пользователя:")
            prompt.appendLine("- Имя: ${context.user.name ?: "Не указано"}")
            prompt.appendLine("- Email: ${context.user.email}")
            prompt.appendLine("- Статус аккаунта: ${formatUserStatus(context.user.status)}")
            
            if (context.user.subscription != null) {
                prompt.appendLine("- Подписка: ${context.user.subscription.plan}")
                context.user.subscription.expiresAt?.let { expiresAt ->
                    val daysLeft = (expiresAt - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
                    if (daysLeft > 0) {
                        prompt.appendLine("  (действует ещё $daysLeft дней)")
                    } else {
                        prompt.appendLine("  (истекла)")
                    }
                }
            } else {
                prompt.appendLine("- Подписка: нет")
            }
            prompt.appendLine()
        }
        
        // Контекст тикета
        if (context.ticket != null) {
            prompt.appendLine("Контекст тикета:")
            prompt.appendLine("- ID тикета: ${context.ticket.id}")
            prompt.appendLine("- Тема: ${context.ticket.subject}")
            prompt.appendLine("- Статус: ${formatTicketStatus(context.ticket.status)}")
            prompt.appendLine("- Приоритет: ${formatPriority(context.ticket.priority)}")
            prompt.appendLine("- Создан: ${formatTimestamp(context.ticket.createdAt)}")
            prompt.appendLine("- Обновлён: ${formatTimestamp(context.ticket.updatedAt)}")
            
            if (context.ticket.messages.isNotEmpty()) {
                prompt.appendLine("- История сообщений (последние ${context.ticket.messages.size}):")
                context.ticket.messages.takeLast(5).forEachIndexed { index, message ->
                    val authorLabel = if (message.author == "user") "Пользователь" else "Поддержка"
                    prompt.appendLine("  ${index + 1}. [$authorLabel, ${formatTimestamp(message.timestamp)}]: ${message.content.take(200)}${if (message.content.length > 200) "..." else ""}")
                }
            }
            prompt.appendLine()
        }
        
        // История тикетов пользователя
        if (context.userTickets != null && context.userTickets.isNotEmpty()) {
            prompt.appendLine("История обращений пользователя (${context.userTickets.size} тикетов):")
            context.userTickets.take(3).forEach { ticket ->
                prompt.appendLine("- ${ticket.subject} (${formatTicketStatus(ticket.status)}, ${formatTimestamp(ticket.createdAt)})")
            }
            if (context.userTickets.size > 3) {
                prompt.appendLine("  ... и ещё ${context.userTickets.size - 3} тикетов")
            }
            prompt.appendLine()
        }
        
        // RAG-контекст из документации
        if (context.ragContext != null && context.ragContext.isNotBlank()) {
            prompt.appendLine("Информация из документации и FAQ:")
            prompt.appendLine("---")
            // Ограничиваем размер RAG-контекста
            val contextPreview = if (context.ragContext.length > 3000) {
                context.ragContext.take(3000) + "\n\n... (контекст обрезан, показаны первые 3000 символов)"
            } else {
                context.ragContext
            }
            prompt.appendLine(contextPreview)
            prompt.appendLine("---")
            prompt.appendLine()
        }
        
        // Инструкции для ответа
        prompt.appendLine("Ответь на вопрос пользователя, учитывая:")
        prompt.appendLine("- Контекст тикета (если указан)")
        prompt.appendLine("- Информацию о пользователе (если доступна)")
        prompt.appendLine("- Информацию из документации и FAQ")
        prompt.appendLine("- Историю обращений (если есть)")
        prompt.appendLine()
        prompt.appendLine("Если проблема требует дополнительной помощи или не решается стандартными способами, предложи создать тикет или связаться с поддержкой.")
        
        return prompt.toString()
    }
    
    /**
     * Форматирует статус пользователя
     */
    private fun formatUserStatus(status: com.prike.domain.model.UserStatus): String {
        return when (status) {
            com.prike.domain.model.UserStatus.ACTIVE -> "Активен"
            com.prike.domain.model.UserStatus.SUSPENDED -> "Заблокирован"
            com.prike.domain.model.UserStatus.DELETED -> "Удалён"
        }
    }
    
    /**
     * Форматирует статус тикета
     */
    private fun formatTicketStatus(status: com.prike.domain.model.TicketStatus): String {
        return when (status) {
            com.prike.domain.model.TicketStatus.OPEN -> "Открыт"
            com.prike.domain.model.TicketStatus.IN_PROGRESS -> "В работе"
            com.prike.domain.model.TicketStatus.RESOLVED -> "Решён"
            com.prike.domain.model.TicketStatus.CLOSED -> "Закрыт"
        }
    }
    
    /**
     * Форматирует приоритет тикета
     */
    private fun formatPriority(priority: com.prike.domain.model.Priority): String {
        return when (priority) {
            com.prike.domain.model.Priority.LOW -> "Низкий"
            com.prike.domain.model.Priority.MEDIUM -> "Средний"
            com.prike.domain.model.Priority.HIGH -> "Высокий"
            com.prike.domain.model.Priority.URGENT -> "Срочный"
        }
    }
    
    /**
     * Форматирует timestamp в читаемый формат
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
        return format.format(date)
    }
}
