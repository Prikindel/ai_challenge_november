package com.prike.domain.service

import ai.koog.agents.core.agent.AIAgent
import com.prike.data.repository.ChatRepository
import com.prike.domain.model.ChatMessage
import com.prike.domain.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Сервис для обработки сообщений в чате с использованием Koog агента
 */
class ReviewsChatService(
    private val chatRepository: ChatRepository,
    private val koogAgent: AIAgent<String, String>
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
        
        // 4. Формируем промпт с учетом истории
        val prompt = buildPromptWithHistory(userMessage, history)
        
        // 5. Генерируем ответ через Koog агента
        val assistantResponse = runBlocking {
            koogAgent.run(prompt)
        }
        
        logger.info("Generated answer: length=${assistantResponse.length}")
        
        // 6. Сохраняем ответ ассистента в историю
        val assistantMessage = chatRepository.saveMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = assistantResponse
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
     * Формирует промпт с учетом истории диалога
     */
    private fun buildPromptWithHistory(
        currentMessage: String,
        history: List<ChatMessage>
    ): String {
        if (history.isEmpty()) {
            return currentMessage
        }

        val historyText = history.takeLast(10).joinToString("\n") { message ->
            when (message.role) {
                MessageRole.USER -> "Пользователь: ${message.content}"
                MessageRole.ASSISTANT -> "Ассистент: ${message.content}"
            }
        }

        return """
            |Контекст предыдущего диалога:
            |$historyText
            |
            |Текущий вопрос пользователя:
            |$currentMessage
        """.trimMargin()
    }
}

