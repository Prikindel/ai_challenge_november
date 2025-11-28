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
    private val promptBuilder: PromptBuilder,
    private val llmService: LLMService,
    private val citationParser: CitationParser = CitationParser()
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
     * @return ответ ассистента с цитатами
     */
    suspend fun processMessage(
        sessionId: String,
        userMessage: String,
        topK: Int = 5,
        minSimilarity: Float = 0.4f,
        applyFilter: Boolean? = null,
        strategy: String? = null
    ): ChatMessage {
        logger.info("Processing message in session $sessionId: ${userMessage.take(50)}...")
        
        // 1. Проверяем существование сессии
        chatRepository.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        // 2. Получаем историю диалога
        val history = chatRepository.getHistory(sessionId)
        logger.debug("Retrieved ${history.size} messages from history")
        
        // 3. Сохраняем сообщение пользователя в историю
        chatRepository.saveMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = userMessage
        )
        
        // 4. Выполняем RAG-поиск для текущего вопроса
        val ragRequest = RAGRequest(
            question = userMessage,
            topK = topK,
            minSimilarity = minSimilarity
        )
        
        val ragResponse = ragService.query(
            request = ragRequest,
            applyFilter = applyFilter,
            strategy = strategy
        )
        
        logger.debug("RAG search completed: found ${ragResponse.contextChunks.size} chunks, ${ragResponse.citations.size} citations")
        
        // 5. Получаем историю для промпта (только сообщения ассистента и пользователя)
        // Используем последние N сообщений для контекста
        val historyForPrompt = history.takeLast(10) // Последние 10 сообщений
        
        // 6. Формируем промпт с историей и контекстом из RAG
        val promptResult = promptBuilder.buildChatPrompt(
            question = userMessage,
            history = historyForPrompt,
            chunks = ragResponse.contextChunks
        )
        
        logger.debug("Built chat prompt with ${historyForPrompt.size} history messages and ${ragResponse.contextChunks.size} chunks")
        
        // 7. Генерируем ответ через LLM
        val llmResponse = llmService.generateAnswer(
            question = promptResult.userMessage,
            systemPrompt = promptResult.systemPrompt
        )
        
        logger.info("Generated answer: length=${llmResponse.answer.length}, tokens=${llmResponse.tokensUsed}")
        
        // 8. Парсим цитаты из ответа
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
        
        // 9. Сохраняем ответ ассистента в историю
        val assistantMessage = chatRepository.saveMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = answerWithCitations.answer,
            citations = validatedCitations
        )
        
        logger.info("Message processed successfully: session=$sessionId, answerLength=${answerWithCitations.answer.length}, citations=${validatedCitations.size}")
        
        return assistantMessage
    }
    
    /**
     * Получает историю сообщений для сессии
     */
    fun getHistory(sessionId: String, limit: Int? = null): List<ChatMessage> {
        return chatRepository.getHistory(sessionId, limit)
    }
}

