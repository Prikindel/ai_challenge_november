package com.prike.domain.service

import com.prike.config.RAGFilterConfig
import com.prike.config.AIConfig
import com.prike.data.repository.ChatRepository
import com.prike.domain.model.ChatMessage
import com.prike.domain.model.MessageRole
import com.prike.domain.model.RAGRequest
import com.prike.domain.model.RAGResponse
import com.prike.domain.model.Citation
import org.slf4j.LoggerFactory

/**
 * Сервис для обработки сообщений в чате с интеграцией RAG и истории диалога
 */
class ChatService(
    private val chatRepository: ChatRepository,
    private val ragService: RAGService,
    private val chatPromptBuilder: ChatPromptBuilder,
    private val llmService: LLMService,
    private val citationParser: CitationParser = CitationParser(),
    private val gitMCPService: com.prike.domain.service.GitMCPService? = null
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    
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

        // 5. Умная логика определения типа запроса ПЕРЕД RAG
        // Определяем тип запроса для всех вопросов (не только /help)
        val requestType = determineRequestType(actualQuestion)
        logger.info("Request type determined: $requestType for question: $actualQuestion")
        
        var additionalContext: String? = null
        var shouldSkipRAG = false
        
        // Если запрос требует MCP инструментов, используем их сразу, пропуская RAG
        if (gitMCPService != null) {
            when (requestType) {
                RequestType.LIST_DIRECTORY -> {
                    // Запрос на список файлов в директории - используем MCP, пропускаем RAG
                    shouldSkipRAG = true
                    val dirPath = extractDirectoryPathFromQuestion(actualQuestion) ?: "project/docs"
                    val listing = gitMCPService.listDirectory(dirPath)
                    if (listing != null && !listing.startsWith("Ошибка")) {
                        additionalContext = "Список файлов в директории $dirPath:\n\n$listing"
                        logger.info("Successfully listed directory $dirPath via MCP")
                    } else {
                        logger.warn("Failed to list directory $dirPath via MCP")
                    }
                }
                
                RequestType.READ_FILE -> {
                    // Запрос на чтение файла - используем MCP, пропускаем RAG
                    shouldSkipRAG = true
                    val filePath = extractFilePathFromQuestion(actualQuestion)
                    
                    if (filePath != null) {
                        // Пытаемся прочитать указанный файл
                        val fileContent = gitMCPService.readFile(filePath)
                        if (fileContent != null && !fileContent.startsWith("Ошибка")) {
                            additionalContext = "Содержимое файла $filePath:\n\n$fileContent"
                            logger.info("Successfully read $filePath via MCP (${fileContent.length} chars)")
                        } else {
                            logger.warn("Failed to read file $filePath via MCP")
                        }
                    } else {
                        // Пытаемся прочитать api.md, если вопрос про API
                        if (actualQuestion.contains("API", ignoreCase = true) || actualQuestion.contains("api", ignoreCase = true)) {
                            val apiContent = gitMCPService.readFile("project/docs/api.md")
                            if (apiContent != null && !apiContent.startsWith("Ошибка")) {
                                additionalContext = "Содержимое файла project/docs/api.md:\n\n$apiContent"
                                logger.info("Successfully read api.md via MCP (${apiContent.length} chars)")
                            }
                        }
                        
                        // Если не нашли специфичный файл, пробуем README
                        if (additionalContext == null) {
                            val readmeContent = gitMCPService.readFile("project/README.md")
                            if (readmeContent != null && !readmeContent.startsWith("Ошибка")) {
                                additionalContext = "Содержимое файла project/README.md:\n\n$readmeContent"
                                logger.info("Successfully read README.md via MCP (${readmeContent.length} chars)")
                            }
                        }
                    }
                }
                
                RequestType.RAG -> {
                    // Обычный RAG-запрос - выполняем RAG как обычно
                    shouldSkipRAG = false
                }
            }
        }
        
        // 5. Выполняем RAG-поиск для текущего вопроса
        // Если это команда /help, ищем только в документации проекта
        // Для /help снижаем minSimilarity до 0.0 и увеличиваем topK, чтобы гарантировать результаты
        // (семантический поиск может не находить релевантные чанки из-за формулировки вопроса)
        val helpMinSimilarity = if (isHelpCommand) {
            0.0f  // Для /help используем 0.0, чтобы найти любые чанки из документации проекта
        } else {
            minSimilarity
        }
        
        val helpTopK = if (isHelpCommand) {
            maxOf(topK, 10)  // Для /help увеличиваем topK до минимум 10, чтобы больше чанков попало в выборку
        } else {
            topK
        }
        
        // Для /help отключаем реранкер по умолчанию, так как он может отфильтровать все чанки
        val helpStrategy = if (isHelpCommand && strategy == null) {
            "none"  // Для /help без явной стратегии отключаем фильтрацию
        } else {
            strategy
        }
        
        // 6. Выполняем RAG-поиск только если не пропустили его
        val ragResponse = if (shouldSkipRAG) {
            // Пропускаем RAG, создаем пустой ответ
            logger.info("Skipping RAG search due to request type: $requestType")
            RAGResponse(
                question = actualQuestion,
                answer = "",
                contextChunks = emptyList(),
                tokensUsed = null,
                citations = emptyList()
            )
        } else {
            // Выполняем RAG-поиск для текущего вопроса
            val ragRequest = RAGRequest(
                question = actualQuestion,
                topK = helpTopK,
                minSimilarity = helpMinSimilarity
            )
            
            if (isHelpCommand) {
                // Для команды /help ищем только в документации проекта
                ragService.queryProjectDocs(
                    request = ragRequest,
                    applyFilter = applyFilter,
                    strategy = helpStrategy,
                    skipGeneration = true  // ChatService сам генерирует ответ с учетом истории
                )
            } else {
                // Обычный поиск во всех документах
                ragService.query(
                    request = ragRequest,
                    applyFilter = applyFilter,
                    strategy = strategy,
                    skipGeneration = true  // ChatService сам генерирует ответ с учетом истории
                )
            }
        }
        
        logger.debug("RAG search completed: found ${ragResponse.contextChunks.size} chunks, ${ragResponse.citations.size} citations (skipped: $shouldSkipRAG)")
        
        // 7. Fallback для RAG: если не нашли чанки и не использовали MCP, пробуем MCP
        if (!shouldSkipRAG && ragResponse.contextChunks.isEmpty() && gitMCPService != null && isHelpCommand) {
            logger.info("RAG found no chunks for /help, using MCP fallback")
            val readmeContent = gitMCPService.readFile("project/README.md")
            if (readmeContent != null && !readmeContent.startsWith("Ошибка")) {
                additionalContext = "Содержимое файла project/README.md:\n\n$readmeContent"
                logger.info("Successfully read README.md via MCP fallback (${readmeContent.length} chars)")
            }
        }
        
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
                    path to (chunk.documentTitle ?: path)
                }
            }
            .distinctBy { it.first }
            .toMap()
        
        val availableDocumentsPaths = availableDocumentsMap.keys.toSet()
        
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
     * Тип запроса пользователя
     */
    private enum class RequestType {
        LIST_DIRECTORY,  // Запрос на список файлов в директории
        READ_FILE,       // Запрос на чтение файла
        RAG              // Обычный RAG-запрос
    }
    
    /**
     * Определяет тип запроса на основе вопроса пользователя
     * 
     * @param question вопрос пользователя
     * @return тип запроса
     */
    private fun determineRequestType(question: String): RequestType {
        val lowerQuestion = question.lowercase()
        
        // Проверяем, является ли запрос запросом на список файлов/директорий
        val listDirectoryKeywords = listOf(
            "какие файлы",
            "список файлов",
            "что в директории",
            "что в папке",
            "какие файлы есть",
            "какие файлы в",  // Добавлено для "какие файлы в project/src"
            "покажи файлы",
            "покажи список",
            "перечисли файлы",
            "list files",
            "show files",
            "what files",
            "directory listing",
            "структура директории",
            "структура папки",
            "файлы в",  // Добавлено для "файлы в project/src"
            "файлы есть в"  // Добавлено для "файлы есть в project/src"
        )
        
        if (listDirectoryKeywords.any { lowerQuestion.contains(it) }) {
            return RequestType.LIST_DIRECTORY
        }
        
        // Проверяем, является ли запрос запросом на чтение файла
        val readFileKeywords = listOf(
            "покажи содержимое",
            "прочитай файл",
            "содержимое файла",
            "покажи файл",
            "прочитай",
            "покажи",
            "read file",
            "show content",
            "file content",
            "содержимое"
        )
        
        // Проверяем наличие .md в вопросе вместе с ключевыми словами
        val hasFileExtension = question.contains(".md", ignoreCase = true) ||
                question.contains("файл", ignoreCase = true) ||
                question.contains("file", ignoreCase = true)
        
        if (readFileKeywords.any { lowerQuestion.contains(it) } && hasFileExtension) {
            return RequestType.READ_FILE
        }
        
        // Если есть явное упоминание файла с расширением
        if (hasFileExtension && (lowerQuestion.contains("покажи") || lowerQuestion.contains("прочитай"))) {
            return RequestType.READ_FILE
        }
        
        // По умолчанию используем RAG
        return RequestType.RAG
    }
    
    /**
     * Извлекает путь к директории из вопроса пользователя
     * 
     * @param question вопрос пользователя
     * @return путь к директории или null, если не удалось извлечь
     */
    private fun extractDirectoryPathFromQuestion(question: String): String? {
        val lowerQuestion = question.lowercase()
        
        // Паттерны для поиска пути к директории
        // Более гибкие паттерны для извлечения пути после "в", "in", "директории" и т.д.
        val patterns = listOf(
            // "какие файлы в project/src" или "файлы в project/docs"
            Regex("""(?:в|in)\s+(project/[a-zA-Z0-9_/-]+|project|docs?)""", RegexOption.IGNORE_CASE),
            // "project/src" или "project/docs" напрямую
            Regex("""(project/[a-zA-Z0-9_/-]+)""", RegexOption.IGNORE_CASE),
            // "директории project/src" или "папке project/docs"
            Regex("""(?:директории|папке|directory|folder)\s+(project/[a-zA-Z0-9_/-]+|project|docs?)""", RegexOption.IGNORE_CASE),
            // Просто "project" или "docs"
            Regex("""\b(project|docs?)\b""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(question)
            if (match != null) {
                val dirPath = match.groupValues.lastOrNull()
                if (dirPath != null) {
                    // Нормализуем путь
                    return when {
                        dirPath.startsWith("project/") -> dirPath
                        dirPath == "project" -> "project"
                        dirPath == "docs" -> "project/docs"
                        dirPath == "doc" -> "project/docs"
                        else -> dirPath  // Возвращаем как есть, если это уже полный путь
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Извлекает путь к файлу из вопроса пользователя
     * 
     * @param question вопрос пользователя
     * @return путь к файлу или null, если не удалось извлечь
     */
    private fun extractFilePathFromQuestion(question: String): String? {
        // Паттерны для поиска имени файла
        val patterns = listOf(
            Regex("""(?:покажи|прочитай|содержимое|файл|file)\s+(?:файла\s+)?([a-zA-Z0-9_-]+\.md)""", RegexOption.IGNORE_CASE),
            Regex("""([a-zA-Z0-9_-]+\.md)""", RegexOption.IGNORE_CASE),
            Regex("""project/docs/([a-zA-Z0-9_-]+\.md)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(question)
            if (match != null) {
                val fileName = match.groupValues.lastOrNull()
                if (fileName != null) {
                    // Если файл уже содержит путь, возвращаем как есть
                    if (fileName.startsWith("project/")) {
                        return fileName
                    }
                    // Иначе добавляем путь к документации проекта
                    return "project/docs/$fileName"
                }
            }
        }
        
        return null
    }
    
    /**
     * Получает историю сообщений для сессии
     */
    fun getHistory(sessionId: String, limit: Int? = null): List<ChatMessage> {
        return chatRepository.getHistory(sessionId, limit)
    }
}

