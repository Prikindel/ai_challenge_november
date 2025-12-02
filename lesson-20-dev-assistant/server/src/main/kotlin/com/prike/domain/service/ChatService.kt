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
        
        val ragRequest = RAGRequest(
            question = actualQuestion,
            topK = helpTopK,
            minSimilarity = helpMinSimilarity
        )
        
        val ragResponse = if (isHelpCommand) {
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
        
        logger.debug("RAG search completed: found ${ragResponse.contextChunks.size} chunks, ${ragResponse.citations.size} citations")
        
        // 5. Всегда генерируем ответ один раз с учетом истории и контекста из RAG
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
        val promptResult = chatPromptBuilder.buildChatPrompt(
            question = actualQuestion,
            history = optimizedHistory,
            chunks = ragResponse.contextChunks,
            strategy = historyStrategy,
            gitBranch = gitBranch
        )
        
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
     * Получает историю сообщений для сессии
     */
    fun getHistory(sessionId: String, limit: Int? = null): List<ChatMessage> {
        return chatRepository.getHistory(sessionId, limit)
    }
}

