package com.prike.domain.service

import com.prike.domain.model.CodeReview
import com.prike.domain.model.ReviewIssue
import com.prike.domain.model.ReviewIssueType
import com.prike.domain.model.ReviewSuggestion
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Сервис для автоматического ревью кода
 * Использует Git MCP для получения diff, RAG для контекста и LLM для анализа
 */
class CodeReviewService(
    private val gitMCPService: GitMCPService,
    private val ragMCPService: RagMCPService?,
    private val llmService: LLMService
) {
    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)
    
    /**
     * Выполнить ревью PR между двумя ветками
     * 
     * @param base базовая ветка (например, "main")
     * @param head целевая ветка (например, "feature-branch")
     * @return результат ревью
     */
    suspend fun reviewPR(base: String, head: String): CodeReview {
        logger.info("Starting code review for PR: $base..$head")
        
        // 1. Получаем diff между ветками
        val diff = gitMCPService.getDiff(base, head)
        if (diff == null || diff.contains("Ошибка")) {
            throw IllegalStateException("Не удалось получить diff между ветками $base и $head")
        }
        
        // 2. Получаем список изменённых файлов
        val changedFiles = gitMCPService.getChangedFiles(base, head)
        if (changedFiles == null || changedFiles.isEmpty()) {
            logger.warn("No changed files found between $base and $head")
            return CodeReview(
                reviewId = UUID.randomUUID().toString(),
                baseBranch = base,
                headBranch = head,
                changedFiles = emptyList(),
                issues = emptyList(),
                suggestions = emptyList(),
                summary = "Нет изменений для ревью между ветками $base и $head"
            )
        }
        
        logger.info("Found ${changedFiles.size} changed files")
        
        // 3. Анализируем diff
        val analysisResult = analyzeDiff(diff, changedFiles)
        
        // 4. Проверяем против документации через RAG (если доступен)
        val ragContext = if (ragMCPService != null && ragMCPService.isConnected()) {
            checkAgainstDocs(diff, changedFiles)
        } else {
            logger.warn("RAG MCP service is not available, skipping documentation check")
            null
        }
        
        // 5. Генерируем ревью через LLM
        val review = generateReview(
            diff = diff,
            changedFiles = changedFiles,
            analysisResult = analysisResult,
            ragContext = ragContext,
            baseBranch = base,
            headBranch = head
        )
        
        logger.info("Code review completed: ${review.issues.size} issues, ${review.suggestions.size} suggestions")
        
        return review
    }
    
    /**
     * Анализ diff для извлечения базовой информации
     * 
     * @param diff diff между ветками
     * @param changedFiles список изменённых файлов
     * @return базовая информация об изменениях
     */
    private fun analyzeDiff(diff: String, changedFiles: List<String>): Map<String, Any> {
        logger.debug("Analyzing diff (${diff.length} chars, ${changedFiles.size} files)")
        
        // Подсчитываем статистику
        val addedLines = diff.lines().count { it.startsWith("+") && !it.startsWith("+++") }
        val removedLines = diff.lines().count { it.startsWith("-") && !it.startsWith("---") }
        
        // Определяем типы изменённых файлов
        val fileTypes = changedFiles.map { file ->
            when {
                file.endsWith(".kt") -> "kotlin"
                file.endsWith(".java") -> "java"
                file.endsWith(".js") -> "javascript"
                file.endsWith(".ts") -> "typescript"
                file.endsWith(".py") -> "python"
                file.endsWith(".md") -> "markdown"
                file.endsWith(".yaml") || file.endsWith(".yml") -> "yaml"
                else -> "other"
            }
        }.groupingBy { it }.eachCount()
        
        return mapOf(
            "addedLines" to addedLines,
            "removedLines" to removedLines,
            "fileTypes" to fileTypes,
            "totalFiles" to changedFiles.size
        )
    }
    
    /**
     * Проверка кода против документации через RAG
     * 
     * @param diff diff между ветками
     * @param changedFiles список изменённых файлов
     * @return контекст из документации или null
     */
    private suspend fun checkAgainstDocs(diff: String, changedFiles: List<String>): String? {
        if (ragMCPService == null || !ragMCPService.isConnected()) {
            return null
        }
        
        return try {
            logger.debug("Checking code against documentation via RAG")
            
            // Формируем запрос для поиска в документации
            // Ищем информацию о стиле кода, архитектуре, best practices
            val query = "стиль кода, архитектура проекта, best practices, правила разработки"
            
            val arguments = kotlinx.serialization.json.buildJsonObject {
                put("query", JsonPrimitive(query))
                put("topK", JsonPrimitive(5))
            }
            
            val ragResult = ragMCPService.callTool("rag_search_project_docs", arguments)
            logger.debug("RAG context retrieved: ${ragResult.length} chars")
            
            ragResult
        } catch (e: Exception) {
            logger.warn("Failed to get RAG context: ${e.message}", e)
            null
        }
    }
    
    /**
     * Генерация ревью через LLM
     * 
     * @param diff diff между ветками
     * @param changedFiles список изменённых файлов
     * @param analysisResult результат базового анализа
     * @param ragContext контекст из документации (опционально)
     * @param baseBranch базовая ветка
     * @param headBranch целевая ветка
     * @return результат ревью
     */
    private suspend fun generateReview(
        diff: String,
        changedFiles: List<String>,
        analysisResult: Map<String, Any>,
        ragContext: String?,
        baseBranch: String,
        headBranch: String
    ): CodeReview {
        logger.debug("Generating review via LLM")
        
        // Формируем промпт для LLM
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(diff, changedFiles, analysisResult, ragContext)
        
        // Генерируем ответ через LLM
        val llmResponse = llmService.generateAnswer(
            question = userPrompt,
            systemPrompt = systemPrompt,
            temperature = 0.3  // Низкая температура для более детерминированных результатов
        )
        
        // Парсим ответ LLM в структуру CodeReview
        val review = parseLLMResponse(
            llmResponse.answer,
            baseBranch = baseBranch,
            headBranch = headBranch,
            changedFiles = changedFiles
        )
        
        return review
    }
    
    /**
     * Формирование системного промпта для ревью кода
     */
    private fun buildSystemPrompt(): String {
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
        """.trimIndent()
    }
    
    /**
     * Формирование пользовательского промпта с контекстом
     */
    private fun buildUserPrompt(
        diff: String,
        changedFiles: List<String>,
        analysisResult: Map<String, Any>,
        ragContext: String?
    ): String {
        val sb = StringBuilder()
        
        sb.appendLine("Проанализируй следующие изменения в коде:")
        sb.appendLine()
        
        sb.appendLine("Изменённые файлы (${changedFiles.size}):")
        changedFiles.forEach { file ->
            sb.appendLine("  - $file")
        }
        sb.appendLine()
        
        sb.appendLine("Статистика изменений:")
        sb.appendLine("  - Добавлено строк: ${analysisResult["addedLines"]}")
        sb.appendLine("  - Удалено строк: ${analysisResult["removedLines"]}")
        sb.appendLine("  - Типы файлов: ${analysisResult["fileTypes"]}")
        sb.appendLine()
        
        if (ragContext != null) {
            sb.appendLine("Контекст из документации проекта:")
            sb.appendLine(ragContext)
            sb.appendLine()
        }
        
        sb.appendLine("Diff изменений:")
        // Ограничиваем размер diff (первые 8000 символов)
        val diffPreview = if (diff.length > 8000) {
            diff.take(8000) + "\n\n... (diff обрезан, показаны первые 8000 символов)"
        } else {
            diff
        }
        sb.appendLine(diffPreview)
        
        return sb.toString()
    }
    
    /**
     * Парсинг ответа LLM в структуру CodeReview
     */
    private fun parseLLMResponse(
        llmAnswer: String,
        baseBranch: String,
        headBranch: String,
        changedFiles: List<String>
    ): CodeReview {
        return try {
            // Пытаемся найти JSON в ответе
            val jsonStart = llmAnswer.indexOf('{')
            val jsonEnd = llmAnswer.lastIndexOf('}') + 1
            
            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                logger.warn("No JSON found in LLM response, creating fallback review")
                return createFallbackReview(llmAnswer, baseBranch, headBranch, changedFiles)
            }
            
            val jsonString = llmAnswer.substring(jsonStart, jsonEnd)
            val json = Json.parseToJsonElement(jsonString).jsonObject
            
            val summary = json["summary"]?.jsonPrimitive?.content ?: "Ревью завершено"
            val overallScore = json["overallScore"]?.jsonPrimitive?.content
            
            val issues = json["issues"]?.jsonArray?.mapNotNull { issueJson ->
                try {
                    val issueObj = issueJson.jsonObject
                    ReviewIssue(
                        type = ReviewIssueType.valueOf(issueObj["type"]?.jsonPrimitive?.content ?: "STYLE"),
                        severity = issueObj["severity"]?.jsonPrimitive?.content ?: "medium",
                        file = issueObj["file"]?.jsonPrimitive?.content ?: "unknown",
                        line = issueObj["line"]?.jsonPrimitive?.content?.toIntOrNull(),
                        message = issueObj["message"]?.jsonPrimitive?.content ?: "",
                        suggestion = issueObj["suggestion"]?.jsonPrimitive?.content
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to parse issue: ${e.message}")
                    null
                }
            } ?: emptyList()
            
            val suggestions = json["suggestions"]?.jsonArray?.mapNotNull { suggestionJson ->
                try {
                    val suggestionObj = suggestionJson.jsonObject
                    ReviewSuggestion(
                        file = suggestionObj["file"]?.jsonPrimitive?.content ?: "unknown",
                        line = suggestionObj["line"]?.jsonPrimitive?.content?.toIntOrNull(),
                        message = suggestionObj["message"]?.jsonPrimitive?.content ?: "",
                        priority = suggestionObj["priority"]?.jsonPrimitive?.content ?: "medium"
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to parse suggestion: ${e.message}")
                    null
                }
            } ?: emptyList()
            
            CodeReview(
                reviewId = UUID.randomUUID().toString(),
                baseBranch = baseBranch,
                headBranch = headBranch,
                changedFiles = changedFiles,
                issues = issues,
                suggestions = suggestions,
                summary = summary,
                overallScore = overallScore
            )
        } catch (e: Exception) {
            logger.error("Failed to parse LLM response: ${e.message}", e)
            createFallbackReview(llmAnswer, baseBranch, headBranch, changedFiles)
        }
    }
    
    /**
     * Создание fallback ревью, если парсинг не удался
     */
    private fun createFallbackReview(
        llmAnswer: String,
        baseBranch: String,
        headBranch: String,
        changedFiles: List<String>
    ): CodeReview {
        return CodeReview(
            reviewId = UUID.randomUUID().toString(),
            baseBranch = baseBranch,
            headBranch = headBranch,
            changedFiles = changedFiles,
            issues = emptyList(),
            suggestions = emptyList(),
            summary = "Ревью завершено, но не удалось распарсить структурированный ответ. Ответ LLM: ${llmAnswer.take(500)}"
        )
    }
}

