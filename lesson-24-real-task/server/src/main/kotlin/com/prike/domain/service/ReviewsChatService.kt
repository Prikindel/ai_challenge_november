package com.prike.domain.service

import ai.koog.agents.core.agent.AIAgent
import com.prike.data.repository.ChatRepository
import com.prike.domain.model.ChatMessage
import com.prike.domain.model.Citation
import com.prike.domain.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Сервис для обработки сообщений в чате с использованием Koog агента и RAG
 */
class ReviewsChatService(
    private val chatRepository: ChatRepository,
    private val koogAgent: AIAgent<String, String>,
    private val ragService: ReviewSummaryRagService? = null
) {
    private val logger = LoggerFactory.getLogger(ReviewsChatService::class.java)

    /**
     * Обрабатывает сообщение пользователя в контексте сессии
     * 
     * @param sessionId ID сессии чата
     * @param userMessage сообщение пользователя
     * @return ответ ассистента
     */
    suspend fun processMessage(
        sessionId: String,
        userMessage: String
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
        
        // 4. Если доступен RAG, выполняем поиск по саммари отзывов
        val ragContext = if (ragService != null) {
            runBlocking {
                try {
                    val searchResults = ragService.search(
                        query = userMessage,
                        limit = 5,
                        minSimilarity = 0.3
                    )
                    
                    if (searchResults.isNotEmpty()) {
                        logger.debug("Found ${searchResults.size} relevant review summaries via RAG")
                        buildRagContext(searchResults)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    logger.warn("Error during RAG search: ${e.message}", e)
                    null
                }
            }
        } else {
            null
        }
        
        // 5. Формируем промпт с учетом истории и RAG контекста
        val prompt = buildPromptWithHistoryAndRag(userMessage, history, ragContext)
        
        // 6. Генерируем ответ через Koog агента
        val assistantResponse = runBlocking {
            koogAgent.run(prompt)
        }
        
        logger.info("Generated answer: length=${assistantResponse.length}")
        
        // 7. Формируем цитаты из RAG результатов
        val citations = if (ragContext != null && ragService != null) {
            runBlocking {
                try {
                    val searchResults = ragService.search(
                        query = userMessage,
                        limit = 5,
                        minSimilarity = 0.3
                    )
                    searchResults.map { result ->
                        Citation(
                            text = result.content,
                            documentPath = "review_summaries",
                            documentTitle = "Review Summary ${result.reviewId}",
                            chunkId = result.reviewId
                        )
                    }
                } catch (e: Exception) {
                    logger.warn("Error building citations: ${e.message}", e)
                    emptyList()
                }
            }
        } else {
            emptyList()
        }
        
        // 8. Сохраняем ответ ассистента в историю с цитатами
        val assistantMessage = chatRepository.saveMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = assistantResponse,
            citations = citations
        )
        
        return assistantMessage
    }

    /**
     * Получает историю сообщений сессии
     */
    fun getHistory(sessionId: String): List<ChatMessage> {
        return chatRepository.getHistory(sessionId)
    }

    /**
     * Формирует промпт с учетом истории диалога и RAG контекста
     */
    private fun buildPromptWithHistoryAndRag(
        currentMessage: String,
        history: List<ChatMessage>,
        ragContext: String?
    ): String {
        val historyText = if (history.isNotEmpty()) {
            history.takeLast(10).joinToString("\n") { message ->
                when (message.role) {
                    MessageRole.USER -> "Пользователь: ${message.content}"
                    MessageRole.ASSISTANT -> "Ассистент: ${message.content}"
                }
            }
        } else {
            null
        }

        val ragSection = if (ragContext != null) {
            """
            |Релевантная информация из саммари отзывов:
            |$ragContext
            |
            """.trimMargin()
        } else {
            ""
        }

        val historySection = if (historyText != null) {
            """
            |Контекст предыдущего диалога:
            |$historyText
            |
            """.trimMargin()
        } else {
            ""
        }

        return buildString {
            if (ragSection.isNotEmpty()) {
                append(ragSection)
            }
            if (historySection.isNotEmpty()) {
                append(historySection)
            }
            append("Текущий вопрос пользователя:\n")
            append(currentMessage)
        }
    }
    
    /**
     * Строит RAG контекст из результатов поиска
     */
    private fun buildRagContext(searchResults: List<com.prike.domain.service.ReviewSummarySearchResult>): String {
        return searchResults.joinToString("\n\n") { result ->
            """
            |[Саммари отзыва ${result.reviewId}, неделя ${result.weekStart}, сходство: ${String.format("%.2f", result.similarity)}]
            |${result.content}
            """.trimMargin()
        }
    }
}

