package com.prike.domain.service

import org.slf4j.LoggerFactory
import kotlin.text.buildString

/**
 * Построитель промптов для ревью кода
 * Формирует системные и пользовательские промпты для анализа кода через LLM
 */
class CodeReviewPromptBuilder {
    private val logger = LoggerFactory.getLogger(CodeReviewPromptBuilder::class.java)
    
    companion object {
        /**
         * Системный промпт для code reviewer
         */
        private fun getSystemPrompt(): String {
            return """
                Ты — опытный code reviewer, который анализирует изменения в коде и выдаёт структурированное ревью.
                
                Твоя задача:
                1. Найти потенциальные баги и проблемы безопасности
                2. Проверить соответствие стилю кода и best practices
                3. Выявить проблемы производительности
                4. Предложить улучшения кода
                
                Формат ответа (строго JSON):
                {
                    "summary": "Краткое резюме ревью",
                    "overallScore": "approve" | "request_changes" | "comment",
                    "issues": [
                        {
                            "type": "BUG" | "SECURITY" | "PERFORMANCE" | "STYLE" | "LOGIC" | "DOCUMENTATION",
                            "severity": "critical" | "high" | "medium" | "low",
                            "file": "путь/к/файлу",
                            "line": номер_строки или null,
                            "message": "Описание проблемы",
                            "suggestion": "Предложение по исправлению" или null
                        }
                    ],
                    "suggestions": [
                        {
                            "file": "путь/к/файлу",
                            "line": номер_строки или null,
                            "message": "Описание предложения",
                            "priority": "high" | "medium" | "low"
                        }
                    ]
                }
                
                Важно:
                - Будь конкретным и конструктивным
                - Указывай точные номера строк, если возможно
                - Предлагай конкретные исправления
                - Не критикуй без предложения решения
                - Учитывай контекст из документации проекта, если он предоставлен
            """.trimIndent()
        }
    }
    
    /**
     * Результат построения промпта для ревью
     */
    data class ReviewPromptResult(
        val systemPrompt: String,
        val userPrompt: String
    )
    
    /**
     * Формирует промпт для ревью кода
     * 
     * @param diff diff между ветками
     * @param changedFiles список изменённых файлов
     * @param analysisResult результат базового анализа (статистика)
     * @param ragContext контекст из документации проекта (опционально)
     * @param maxDiffLength максимальная длина diff в символах (по умолчанию 8000)
     * @return системный и пользовательский промпты
     */
    fun buildReviewPrompt(
        diff: String,
        changedFiles: List<String>,
        analysisResult: Map<String, Any>,
        ragContext: String? = null,
        maxDiffLength: Int = 8000
    ): ReviewPromptResult {
        logger.debug("Building review prompt for ${changedFiles.size} files")
        
        val systemPrompt = buildSystemPrompt(ragContext)
        val userPrompt = buildUserPrompt(diff, changedFiles, analysisResult, ragContext, maxDiffLength)
        
        return ReviewPromptResult(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
    }
    
    /**
     * Формирует системный промпт с учётом RAG-контекста
     */
    private fun buildSystemPrompt(ragContext: String?): String {
        val basePrompt = getSystemPrompt()
        
        if (ragContext != null && ragContext.isNotBlank()) {
            return buildString {
                appendLine(basePrompt)
                appendLine()
                appendLine("Дополнительный контекст из документации проекта:")
                appendLine("Используй эту информацию для проверки соответствия кода правилам проекта, стилю и архитектуре.")
            }
        }
        
        return basePrompt
    }
    
    /**
     * Формирует пользовательский промпт с контекстом изменений
     */
    private fun buildUserPrompt(
        diff: String,
        changedFiles: List<String>,
        analysisResult: Map<String, Any>,
        ragContext: String?,
        maxDiffLength: Int
    ): String {
        val sb = StringBuilder()
        
        // Заголовок
        sb.appendLine("Проанализируй следующие изменения в коде:")
        sb.appendLine()
        
        // Список изменённых файлов
        sb.appendLine("Изменённые файлы (${changedFiles.size}):")
        changedFiles.forEach { file ->
            sb.appendLine("  - $file")
        }
        sb.appendLine()
        
        // Статистика изменений
        sb.appendLine("Статистика изменений:")
        val addedLines = analysisResult["addedLines"] as? Int ?: 0
        val removedLines = analysisResult["removedLines"] as? Int ?: 0
        val fileTypes = analysisResult["fileTypes"] as? Map<*, *> ?: emptyMap<Any, Any>()
        
        sb.appendLine("  - Добавлено строк: $addedLines")
        sb.appendLine("  - Удалено строк: $removedLines")
        sb.appendLine("  - Типы файлов: ${formatFileTypes(fileTypes)}")
        sb.appendLine()
        
        // RAG-контекст из документации
        if (ragContext != null && ragContext.isNotBlank()) {
            sb.appendLine("Контекст из документации проекта:")
            sb.appendLine("---")
            // Ограничиваем размер RAG-контекста
            val contextPreview = if (ragContext.length > 2000) {
                ragContext.take(2000) + "\n\n... (контекст обрезан, показаны первые 2000 символов)"
            } else {
                ragContext
            }
            sb.appendLine(contextPreview)
            sb.appendLine("---")
            sb.appendLine()
        }
        
        // Diff изменений
        sb.appendLine("Diff изменений:")
        sb.appendLine("---")
        val diffPreview = if (diff.length > maxDiffLength) {
            diff.take(maxDiffLength) + "\n\n... (diff обрезан, показаны первые $maxDiffLength символов)"
        } else {
            diff
        }
        sb.appendLine(diffPreview)
        sb.appendLine("---")
        
        return sb.toString()
    }
    
    /**
     * Форматирует типы файлов для отображения
     */
    private fun formatFileTypes(fileTypes: Map<*, *>): String {
        if (fileTypes.isEmpty()) {
            return "неизвестно"
        }
        
        return fileTypes.entries.joinToString(", ") { (type, count) ->
            "$type: $count"
        }
    }
    
    /**
     * Формирует промпт для ревью конкретного файла
     * 
     * @param filePath путь к файлу
     * @param fileContent содержимое файла
     * @param diffFragment фрагмент diff для этого файла
     * @param ragContext контекст из документации (опционально)
     * @return промпт для ревью файла
     */
    fun buildFileReviewPrompt(
        filePath: String,
        fileContent: String,
        diffFragment: String,
        ragContext: String? = null
    ): ReviewPromptResult {
        logger.debug("Building file review prompt for: $filePath")
        
        val systemPrompt = buildSystemPrompt(ragContext)
        
        val userPrompt = buildString {
            appendLine("Проанализируй изменения в файле: $filePath")
            appendLine()
            
            if (ragContext != null && ragContext.isNotBlank()) {
                appendLine("Контекст из документации проекта:")
                appendLine("---")
                appendLine(ragContext.take(2000))
                appendLine("---")
                appendLine()
            }
            
            appendLine("Полное содержимое файла:")
            appendLine("---")
            // Ограничиваем размер файла (100KB максимум)
            val contentPreview = if (fileContent.length > 50000) {
                fileContent.take(50000) + "\n\n... (файл обрезан, показаны первые 50000 символов)"
            } else {
                fileContent
            }
            appendLine(contentPreview)
            appendLine("---")
            appendLine()
            
            appendLine("Изменения в файле (diff):")
            appendLine("---")
            appendLine(diffFragment)
            appendLine("---")
        }
        
        return ReviewPromptResult(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
    }
    
    /**
     * Формирует промпт для быстрого ревью (без полного diff)
     * Используется для больших PR
     * 
     * @param changedFiles список изменённых файлов
     * @param analysisResult результат базового анализа
     * @param ragContext контекст из документации (опционально)
     * @return промпт для быстрого ревью
     */
    fun buildQuickReviewPrompt(
        changedFiles: List<String>,
        analysisResult: Map<String, Any>,
        ragContext: String? = null
    ): ReviewPromptResult {
        logger.debug("Building quick review prompt for ${changedFiles.size} files")
        
        val systemPrompt = buildSystemPrompt(ragContext)
        
        val userPrompt = buildString {
            appendLine("Проанализируй список изменённых файлов и дай общую оценку:")
            appendLine()
            
            appendLine("Изменённые файлы (${changedFiles.size}):")
            changedFiles.forEach { file ->
                appendLine("  - $file")
            }
            appendLine()
            
            appendLine("Статистика изменений:")
            val addedLines = analysisResult["addedLines"] as? Int ?: 0
            val removedLines = analysisResult["removedLines"] as? Int ?: 0
            val fileTypes = analysisResult["fileTypes"] as? Map<*, *> ?: emptyMap<Any, Any>()
            
            appendLine("  - Добавлено строк: $addedLines")
            appendLine("  - Удалено строк: $removedLines")
            appendLine("  - Типы файлов: ${formatFileTypes(fileTypes)}")
            appendLine()
            
            if (ragContext != null && ragContext.isNotBlank()) {
                appendLine("Контекст из документации проекта:")
                appendLine("---")
                appendLine(ragContext.take(2000))
                appendLine("---")
                appendLine()
            }
            
            appendLine("Обрати внимание на:")
            appendLine("- Соответствие стилю кода проекта")
            appendLine("- Потенциальные проблемы безопасности")
            appendLine("- Общую архитектуру изменений")
            appendLine("- Соответствие best practices")
        }
        
        return ReviewPromptResult(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
    }
}

