package com.prike.domain.service

import com.prike.config.RAGFilterConfig
import com.prike.config.AIConfig
import com.prike.data.repository.ChatRepository
import com.prike.domain.model.ChatMessage
import com.prike.domain.model.MessageRole
import com.prike.domain.model.RAGResponse
import com.prike.domain.model.Citation
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для обработки сообщений в чате с интеграцией RAG и истории диалога
 */
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatPromptBuilder: ChatPromptBuilder,
    private val llmService: LLMService,
    private val citationParser: CitationParser = CitationParser(),
    private val gitMCPService: GitMCPService? = null,
    private val ragMCPService: RagMCPService? = null,
    private val requestRouter: RequestRouterService? = null
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val ragResultParser = RagResultParser()
    
    /**
     * Обрабатывает сообщение пользователя в контексте сессии
     * 
     * @param sessionId ID сессии чата
     * @param userMessage сообщение пользователя
     * @param topK количество чанков для RAG-поиска
     * @param minSimilarity минимальное сходство для RAG-поиска
     * @param applyFilter применять ли фильтр/реранкер
     * @param strategy стратегия фильтрации
     * @param historyStrategy стратегия оптимизации истории ("sliding" | "token_limit" | "none")
     * @return ответ ассистента с цитатами
     */
    suspend fun processMessage(
        sessionId: String,
        userMessage: String,
        topK: Int = 5,
        minSimilarity: Float = 0.4f,
        applyFilter: Boolean? = null,
        strategy: String? = null,
        historyStrategy: String? = null
    ): ChatMessage {
        logger.info("Processing message in session $sessionId: ${userMessage.take(50)}...")
        
        // 1. Проверяем существование сессии
        chatRepository.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        // 2. Проверяем, является ли сообщение командой /help
        val isHelpCommand = userMessage.trim().startsWith("/help", ignoreCase = true)
        val actualQuestion = if (isHelpCommand) {
            // Извлекаем вопрос из команды /help [вопрос]
            val questionPart = userMessage.trim().removePrefix("/help").trim()
            if (questionPart.isBlank()) {
                "Что такое этот проект и как он работает?"
            } else {
                questionPart
            }
        } else {
            userMessage
        }

        // 3. Получаем историю диалога
        val history = chatRepository.getHistory(sessionId)
        logger.debug("Retrieved ${history.size} messages from history")

        // 4. Сохраняем сообщение пользователя в историю (сохраняем оригинальное сообщение)
        chatRepository.saveMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = userMessage
        )

        // 5. Динамический роутинг через LLM (если доступен RequestRouterService)
        var additionalContext: String? = null
        var ragResponse: RAGResponse
        
        if (requestRouter != null) {
            // Используем LLM для определения, что делать
            val routingDecision = requestRouter.route(actualQuestion)
            logger.info("Routing decision: ${routingDecision.action} (tool: ${routingDecision.toolName}, reasoning: ${routingDecision.reasoning})")
            
            when (routingDecision.action) {
                com.prike.domain.service.ActionType.RAG_SEARCH -> {
                    // Обычный RAG поиск по всем документам через MCP
                    if (ragMCPService == null) {
                        logger.error("RAG MCP service is not available for RAG_SEARCH")
                        ragResponse = RAGResponse(
                            question = actualQuestion,
                            answer = "",
                            contextChunks = emptyList(),
                            tokensUsed = null,
                            citations = emptyList()
                        )
                    } else {
                        val arguments = kotlinx.serialization.json.buildJsonObject {
                            put("query", JsonPrimitive(actualQuestion))
                            put("topK", JsonPrimitive(topK))
                            put("minSimilarity", JsonPrimitive(minSimilarity))
                        }
                        val toolResult = ragMCPService.callTool("rag_search", arguments)
                        
                        // Парсим результат в структурированные чанки
                        val chunks = ragResultParser.parseRagSearchResult(toolResult)
                        logger.debug("Parsed ${chunks.size} chunks from RAG search result")
                        
                        additionalContext = "Результат поиска:\n\n$toolResult"
                        ragResponse = RAGResponse(
                            question = actualQuestion,
                            answer = "",
                            contextChunks = chunks,
                            tokensUsed = null,
                            citations = emptyList()
                        )
                    }
                }
                
                com.prike.domain.service.ActionType.RAG_SEARCH_PROJECT -> {
                    // RAG поиск только в документации проекта через MCP
                    if (ragMCPService == null) {
                        logger.error("RAG MCP service is not available for RAG_SEARCH_PROJECT")
                        ragResponse = RAGResponse(
                            question = actualQuestion,
                            answer = "",
                            contextChunks = emptyList(),
                            tokensUsed = null,
                            citations = emptyList()
                        )
                    } else {
                        val arguments = kotlinx.serialization.json.buildJsonObject {
                            put("query", JsonPrimitive(actualQuestion))
                            put("topK", JsonPrimitive(maxOf(topK, 10)))
                            put("minSimilarity", JsonPrimitive(0.0f))
                        }
                        val toolResult = ragMCPService.callTool("rag_search_project_docs", arguments)
                        
                        // Парсим результат в структурированные чанки
                        val chunks = ragResultParser.parseRagSearchResult(toolResult)
                        logger.debug("Parsed ${chunks.size} chunks from RAG search project docs result")
                        
                        additionalContext = "Результат поиска в документации проекта:\n\n$toolResult"
                        ragResponse = RAGResponse(
                            question = actualQuestion,
                            answer = "",
                            contextChunks = chunks,
                            tokensUsed = null,
                            citations = emptyList()
                        )
                    }
                }
                
                com.prike.domain.service.ActionType.MCP_TOOL -> {
                    // Используем MCP инструмент
                    val toolName = routingDecision.toolName
                        ?: throw IllegalStateException("MCP_TOOL action without toolName")
                    val toolArguments = routingDecision.toolArguments
                        ?: kotlinx.serialization.json.buildJsonObject {}
                    
                    logger.info("Calling MCP tool: $toolName with arguments: $toolArguments")
                    
                    // Определяем, какой MCP сервис использовать
                    val toolResult = when {
                        toolName.startsWith("rag_") && ragMCPService != null -> {
                            ragMCPService.callTool(toolName, toolArguments)
                        }
                        gitMCPService != null -> {
                            gitMCPService.callTool(toolName, toolArguments)
                        }
                        else -> {
                            throw IllegalStateException("No MCP service available for tool: $toolName")
                        }
                    }
                    
                    additionalContext = "Результат выполнения инструмента $toolName:\n\n$toolResult"
                    
                    // Создаем пустой RAG ответ, так как использовали MCP инструмент
                    ragResponse = RAGResponse(
                        question = actualQuestion,
                        answer = "",
                        contextChunks = emptyList(),
                        tokensUsed = null,
                        citations = emptyList()
                    )
                }
                
                com.prike.domain.service.ActionType.DIRECT_ANSWER -> {
                    // Прямой ответ, но если указан toolName, используем инструмент
                    if (routingDecision.toolName != null) {
                        // Используем MCP инструмент, даже если action = DIRECT_ANSWER
                        val toolName = routingDecision.toolName
                        var toolArguments = routingDecision.toolArguments
                            ?: kotlinx.serialization.json.buildJsonObject {}
                        
                        // Исправляем неправильные параметры (например, "param" -> "path")
                        toolArguments = fixToolArguments(toolName, toolArguments)
                        
                        logger.info("Using MCP tool for DIRECT_ANSWER: $toolName with arguments: $toolArguments")
                        
                        // Определяем, какой MCP сервис использовать
                        val toolResult = when {
                            toolName.startsWith("rag_") && ragMCPService != null -> {
                                ragMCPService.callTool(toolName, toolArguments)
                            }
                            gitMCPService != null -> {
                                gitMCPService.callTool(toolName, toolArguments)
                            }
                            else -> {
                                throw IllegalStateException("No MCP service available for tool: $toolName")
                            }
                        }
                        
                        additionalContext = "Результат выполнения инструмента $toolName:\n\n$toolResult"
                        
                        ragResponse = RAGResponse(
                            question = actualQuestion,
                            answer = "",
                            contextChunks = emptyList(),
                            tokensUsed = null,
                            citations = emptyList()
                        )
                    } else {
                        // Прямой ответ без инструментов
                        ragResponse = RAGResponse(
                            question = actualQuestion,
                            answer = "",
                            contextChunks = emptyList(),
                            tokensUsed = null,
                            citations = emptyList()
                        )
                    }
                }
            }
        } else {
            // Fallback: если RequestRouterService недоступен, используем MCP инструменты напрямую
            logger.warn("RequestRouterService not available, using direct MCP tool calls")
            
            if (ragMCPService == null) {
                // Если RAG MCP недоступен, возвращаем пустой ответ
                logger.error("RAG MCP service is not available and RequestRouterService is null")
                ragResponse = RAGResponse(
                    question = actualQuestion,
                    answer = "",
                    contextChunks = emptyList(),
                    tokensUsed = null,
                    citations = emptyList()
                )
            } else {
                // Используем MCP инструмент напрямую
                val toolName = if (isHelpCommand) "rag_search_project_docs" else "rag_search"
                val arguments = kotlinx.serialization.json.buildJsonObject {
                    put("query", JsonPrimitive(actualQuestion))
                    put("topK", JsonPrimitive(if (isHelpCommand) maxOf(topK, 10) else topK))
                    put("minSimilarity", JsonPrimitive(if (isHelpCommand) 0.0f else minSimilarity))
                }
                
                val toolResult = ragMCPService.callTool(toolName, arguments)
                
                // Парсим результат в структурированные чанки
                val chunks = ragResultParser.parseRagSearchResult(toolResult)
                logger.debug("Parsed ${chunks.size} chunks from RAG search result")
                
                additionalContext = "Результат поиска:\n\n$toolResult"
                
                ragResponse = RAGResponse(
                    question = actualQuestion,
                    answer = "",
                    contextChunks = chunks,
                    tokensUsed = null,
                    citations = emptyList()
                )
            }
        }
        
        logger.debug("RAG search completed: found ${ragResponse.contextChunks.size} chunks, ${ragResponse.citations.size} citations")
        
        // 6. Всегда генерируем ответ один раз с учетом истории и контекста из RAG
        // Оптимизируем историю
        val optimizedHistory = chatPromptBuilder.optimizeHistory(history, strategy = historyStrategy)
        
        logger.debug("Generating answer with history (${optimizedHistory.size} messages) and ${ragResponse.contextChunks.size} chunks")
        
        val stats = chatPromptBuilder.getOptimizationStats(history, optimizedHistory)
        logger.debug("Built chat prompt (strategy: ${historyStrategy ?: "default"}): ${stats.originalMessagesCount} -> ${stats.optimizedMessagesCount} messages, ${stats.originalTokens} -> ${stats.optimizedTokens} tokens (saved: ${stats.tokensSaved})")
        
        // Получаем текущую ветку git (если доступен GitMCPService)
        val gitBranch = try {
            gitMCPService?.getCurrentBranch()
        } catch (e: Exception) {
            logger.warn("Failed to get git branch: ${e.message}")
            null
        }
        
        // Формируем промпт с оптимизированной историей и контекстом из RAG
        // Используем actualQuestion вместо userMessage для формирования промпта
        var promptResult = chatPromptBuilder.buildChatPrompt(
            question = actualQuestion,
            history = optimizedHistory,
            chunks = ragResponse.contextChunks,
            strategy = historyStrategy,
            gitBranch = gitBranch
        )
        
        // Если есть дополнительный контекст из MCP, добавляем его в системный промпт
        if (additionalContext != null) {
            val systemMessage = promptResult.messages.firstOrNull { it.role == "system" }
            if (systemMessage != null) {
                val updatedSystemMessage = systemMessage.copy(
                    content = systemMessage.content + "\n\n📄 Дополнительный контекст из файлов проекта:\n\n$additionalContext"
                )
                val updatedMessages = promptResult.messages.map { message ->
                    if (message.role == "system") updatedSystemMessage else message
                }
                promptResult = PromptBuilder.ChatPromptResult(messages = updatedMessages)
            }
        }
        
        // Генерируем ответ через LLM с историей в формате messages
        val llmResponse = llmService.generateAnswerWithMessages(promptResult.messages)
        
        logger.info("Generated answer: length=${llmResponse.answer.length}, tokens=${llmResponse.tokensUsed}")
        
        // Парсим цитаты из ответа
        val availableDocumentsMap = ragResponse.contextChunks
            .mapNotNull { chunk ->
                chunk.documentPath?.let { path ->
                    // Нормализуем путь для более гибкого сравнения
                    val normalizedPath = normalizePathForComparison(path)
                    normalizedPath to (chunk.documentTitle ?: extractFileName(path))
                }
            }
            .distinctBy { it.first }
            .toMap()
        
        // Создаем набор путей для валидации (включая оригинальные и нормализованные)
        val availableDocumentsPaths = mutableSetOf<String>()
        ragResponse.contextChunks.forEach { chunk ->
            chunk.documentPath?.let { path ->
                availableDocumentsPaths.add(path)
                availableDocumentsPaths.add(normalizePathForComparison(path))
                availableDocumentsPaths.add(extractFileName(path))
            }
        }
        
        val answerWithCitations = citationParser.parseCitations(
            rawAnswer = llmResponse.answer,
            availableDocuments = availableDocumentsMap
        )
        
        // Валидируем цитаты - проверяем, что документы были в контексте
        val validatedCitations = answerWithCitations.citations.filter { citation ->
            val isValid = citationParser.validateCitation(citation, availableDocumentsPaths)
            if (!isValid) {
                logger.warn("Invalid citation detected: ${citation.documentPath} (not in context)")
            }
            isValid
        }
        
        logger.debug("Parsed ${answerWithCitations.citations.size} citations, ${validatedCitations.size} are valid")
        
        val finalAnswer = answerWithCitations.answer
        val finalCitations = validatedCitations
        
        // 6. Сохраняем ответ ассистента в историю
        val assistantMessage = chatRepository.saveMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = finalAnswer,
            citations = finalCitations
        )
        
        logger.info("Message processed successfully: session=$sessionId, answerLength=${finalAnswer.length}, citations=${finalCitations.size}")
        
        return assistantMessage
    }
    
    /**
     * Нормализует путь для сравнения
     */
    private fun normalizePathForComparison(path: String): String {
        return path
            .replace("\\", "/")
            .replace(Regex("/+"), "/")
            .trim('/')
            .lowercase()
    }
    
    /**
     * Извлекает имя файла из пути
     */
    private fun extractFileName(path: String): String {
        return path.split("/").lastOrNull() ?: path
    }
    
    /**
     * Исправляет неправильные параметры инструментов
     */
    private fun fixToolArguments(toolName: String, arguments: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject {
        // Если инструмент list_directory или read_file, проверяем параметр "path"
        if (toolName == "list_directory" || toolName == "read_file") {
            // Если есть параметр "param", переименовываем в "path"
            val paramValue = arguments["param"]?.jsonPrimitive?.content
            if (paramValue != null && arguments["path"] == null) {
                return kotlinx.serialization.json.buildJsonObject {
                    put("path", kotlinx.serialization.json.JsonPrimitive(paramValue))
                }
            }
        }
        return arguments
    }
    
    /**
     * Получает историю сообщений для сессии
     */
    fun getHistory(sessionId: String, limit: Int? = null): List<ChatMessage> {
        return chatRepository.getHistory(sessionId, limit)
    }
}

