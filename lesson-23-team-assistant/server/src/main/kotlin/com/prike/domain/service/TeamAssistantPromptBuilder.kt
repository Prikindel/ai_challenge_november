package com.prike.domain.service

import com.prike.domain.model.TeamContext
import org.slf4j.LoggerFactory

/**
 * Построитель промптов для ассистента команды
 * Формирует системные и пользовательские промпты для ответов на вопросы команды
 */
class TeamAssistantPromptBuilder {
    private val logger = LoggerFactory.getLogger(TeamAssistantPromptBuilder::class.java)
    
    /**
     * Результат построения промпта для команды
     */
    data class TeamPromptResult(
        val systemPrompt: String,
        val userPrompt: String
    )
    
    /**
     * Формирует промпт для команды
     * 
     * @param question вопрос команды
     * @param context контекст команды (статус проекта, задачи, RAG-контекст)
     * @return системный и пользовательский промпты
     */
    fun buildTeamPrompt(
        question: String,
        context: TeamContext
    ): TeamPromptResult {
        logger.debug("Building team prompt for question: ${question.take(100)}...")
        
        val systemPrompt = buildSystemPrompt(context)
        val userPrompt = buildUserPrompt(question, context)
        
        return TeamPromptResult(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
    }
    
    /**
     * Формирует системный промпт с ролью team assistant
     */
    fun buildSystemPrompt(context: TeamContext): String {
        return buildString {
            appendLine("Ты — опытный ассистент команды разработки, который помогает команде управлять задачами, анализировать статус проекта и давать рекомендации.")
            appendLine()
            appendLine("Твоя задача:")
            appendLine("- Отвечать на вопросы команды о статусе проекта и задачах")
            appendLine("- Анализировать задачи и предлагать приоритеты выполнения")
            appendLine("- Выявлять блокирующие задачи и зависимости")
            appendLine("- Давать рекомендации по управлению задачами")
            appendLine("- Использовать документацию проекта для контекста")
            appendLine("- Предлагать конкретные действия для улучшения работы команды")
            appendLine()
            appendLine("Правила ответа:")
            appendLine("- Отвечай на русском языке")
            appendLine("- Будь конкретным и полезным")
            appendLine("- Используй информацию о задачах для точных ответов")
            appendLine("- Анализируй зависимости между задачами")
            appendLine("- Учитывай приоритеты и сроки выполнения")
            appendLine("- Предлагай конкретные шаги для решения проблем")
            appendLine()
            
            // Добавляем информацию о статусе проекта, если доступна
            if (context.projectStatus != null) {
                appendLine("Текущий статус проекта:")
                appendLine("- Всего задач: ${context.projectStatus.totalTasks}")
                appendLine("- В работе: ${context.projectStatus.tasksInProgress}")
                appendLine("- Выполнено: ${context.projectStatus.tasksDone}")
                appendLine("- Заблокировано: ${context.projectStatus.blockedTasks}")
                
                if (context.projectStatus.tasksByStatus.isNotEmpty()) {
                    appendLine("- Распределение по статусам:")
                    context.projectStatus.tasksByStatus.forEach { (status, count) ->
                        appendLine("  • $status: $count")
                    }
                }
                
                if (context.projectStatus.tasksByPriority.isNotEmpty()) {
                    appendLine("- Распределение по приоритетам:")
                    context.projectStatus.tasksByPriority.forEach { (priority, count) ->
                        appendLine("  • $priority: $count")
                    }
                }
                appendLine()
            }
            
            appendLine("При анализе задач учитывай:")
            appendLine("- Блокирующие зависимости (задачи, которые блокируют другие)")
            appendLine("- Приоритеты задач (URGENT > HIGH > MEDIUM > LOW)")
            appendLine("- Сроки выполнения (dueDate)")
            appendLine("- Статус задач (BLOCKED требует особого внимания)")
            appendLine()
            appendLine("Формат ответа:")
            appendLine("- Давай структурированные ответы с конкретными рекомендациями")
            appendLine("- Указывай конкретные задачи, которые нужно выполнить первыми")
            appendLine("- Объясняй причины рекомендаций")
        }
    }
    
    /**
     * Формирует пользовательский промпт с вопросом и контекстом
     */
    fun buildUserPrompt(question: String, context: TeamContext): String {
        val prompt = StringBuilder()
        
        // Вопрос команды
        prompt.appendLine("Вопрос команды: $question")
        prompt.appendLine()
        
        // Контекст задач
        if (context.tasks.isNotEmpty()) {
            prompt.appendLine("Релевантные задачи (${context.tasks.size}):")
            context.tasks.forEachIndexed { index, task ->
                prompt.appendLine("${index + 1}. ${task.title}")
                prompt.appendLine("   ID: ${task.id}")
                prompt.appendLine("   Описание: ${task.description.take(150)}${if (task.description.length > 150) "..." else ""}")
                prompt.appendLine("   Статус: ${formatTaskStatus(task.status)}")
                prompt.appendLine("   Приоритет: ${formatPriority(task.priority)}")
                
                if (task.assignee != null) {
                    prompt.appendLine("   Исполнитель: ${task.assignee}")
                }
                
                if (task.dueDate != null) {
                    prompt.appendLine("   Срок выполнения: ${formatTimestamp(task.dueDate)}")
                }
                
                if (task.blockedBy.isNotEmpty()) {
                    prompt.appendLine("   ⚠️ Блокируется задачами: ${task.blockedBy.joinToString(", ")}")
                }
                
                if (task.blocks.isNotEmpty()) {
                    prompt.appendLine("   🔒 Блокирует задачи: ${task.blocks.joinToString(", ")}")
                }
                
                prompt.appendLine("   Создана: ${formatTimestamp(task.createdAt)}")
                prompt.appendLine("   Обновлена: ${formatTimestamp(task.updatedAt)}")
                prompt.appendLine()
            }
        } else {
            prompt.appendLine("Релевантные задачи не найдены.")
            prompt.appendLine()
        }
        
        // RAG-контекст из документации проекта
        if (context.ragContext.isNotEmpty()) {
            prompt.appendLine("Контекст из документации проекта:")
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
        prompt.appendLine("Ответь на вопрос команды, учитывая:")
        prompt.appendLine("- Информацию о задачах (статус, приоритет, зависимости)")
        prompt.appendLine("- Статус проекта (если доступен)")
        prompt.appendLine("- Информацию из документации проекта")
        prompt.appendLine()
        prompt.appendLine("В ответе:")
        prompt.appendLine("- Проанализируй задачи и их зависимости")
        prompt.appendLine("- Предложи, какие задачи нужно выполнить первыми и почему")
        prompt.appendLine("- Укажи блокирующие проблемы, если они есть")
        prompt.appendLine("- Дай конкретные рекомендации по приоритетам")
        
        return prompt.toString()
    }
    
    /**
     * Форматирует статус задачи
     */
    private fun formatTaskStatus(status: com.prike.domain.model.TaskStatus): String {
        return when (status) {
            com.prike.domain.model.TaskStatus.TODO -> "К выполнению"
            com.prike.domain.model.TaskStatus.IN_PROGRESS -> "В работе"
            com.prike.domain.model.TaskStatus.IN_REVIEW -> "На проверке"
            com.prike.domain.model.TaskStatus.DONE -> "Выполнено"
            com.prike.domain.model.TaskStatus.BLOCKED -> "Заблокировано"
        }
    }
    
    /**
     * Форматирует приоритет задачи
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

