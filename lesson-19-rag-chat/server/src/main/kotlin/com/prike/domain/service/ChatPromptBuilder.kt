package com.prike.domain.service

import com.prike.config.ChatHistoryConfig
import com.prike.domain.indexing.TokenCounter
import com.prike.domain.model.ChatMessage
import com.prike.domain.model.MessageRole
import com.prike.domain.model.RetrievedChunk
import org.slf4j.LoggerFactory

/**
 * Построитель промптов для чата с оптимизацией истории диалога
 * Поддерживает стратегии: sliding window, token limit
 */
class ChatPromptBuilder(
    private val historyConfig: ChatHistoryConfig,
    private val basePromptBuilder: PromptBuilder
) {
    private val logger = LoggerFactory.getLogger(ChatPromptBuilder::class.java)
    private val tokenCounter = TokenCounter()
    
    /**
     * Оптимизирует историю диалога согласно стратегии
     * 
     * @param history история диалога
     * @param strategy стратегия оптимизации (если null, используется из конфигурации)
     */
    fun optimizeHistory(history: List<ChatMessage>, strategy: String? = null): List<ChatMessage> {
        if (history.isEmpty()) {
            return emptyList()
        }
        
        val strategyToUse = strategy ?: historyConfig.strategy
        
        return when (strategyToUse) {
            "sliding" -> optimizeWithSlidingWindow(history)
            "token_limit" -> optimizeWithTokenLimit(history)
            "none" -> history
            else -> {
                logger.warn("Unknown history strategy: $strategyToUse, using sliding window")
                optimizeWithSlidingWindow(history)
            }
        }
    }
    
    /**
     * Оптимизация с помощью sliding window (последние N сообщений)
     */
    private fun optimizeWithSlidingWindow(history: List<ChatMessage>): List<ChatMessage> {
        val maxMessages = historyConfig.maxMessages.coerceAtLeast(1)
        
        if (history.size <= maxMessages) {
            return history
        }
        
        // Берем последние N сообщений
        val optimized = history.takeLast(maxMessages)
        logger.debug("Sliding window: ${history.size} -> ${optimized.size} messages")
        return optimized
    }
    
    /**
     * Оптимизация с помощью ограничения по токенам
     */
    private fun optimizeWithTokenLimit(history: List<ChatMessage>): List<ChatMessage> {
        val maxTokens = historyConfig.maxTokens.coerceAtLeast(100)
        
        // Подсчитываем токены для каждого сообщения
        val messagesWithTokens = history.map { message ->
            val tokens = tokenCounter.countTokens(message.content)
            message to tokens
        }
        
        // Начинаем с последних сообщений и добавляем, пока не превысим лимит
        val optimized = mutableListOf<ChatMessage>()
        var totalTokens = 0
        
        for (i in messagesWithTokens.size - 1 downTo 0) {
            val (message, tokens) = messagesWithTokens[i]
            
            if (totalTokens + tokens <= maxTokens) {
                optimized.add(0, message) // Добавляем в начало, чтобы сохранить порядок
                totalTokens += tokens
            } else {
                // Если даже одно сообщение превышает лимит, оставляем его
                if (optimized.isEmpty()) {
                    optimized.add(0, message)
                }
                break
            }
        }
        
        logger.debug("Token limit: ${history.size} messages (${tokenCounter.countTokens(history.joinToString { it.content })} tokens) -> ${optimized.size} messages ($totalTokens tokens)")
        return optimized
    }
    
    /**
     * Формирует промпт для чата с оптимизированной историей
     * 
     * @param question текущий вопрос
     * @param history история диалога
     * @param chunks релевантные чанки из RAG
     * @param strategy стратегия оптимизации истории (если null, используется из конфигурации)
     */
    fun buildChatPrompt(
        question: String,
        history: List<ChatMessage> = emptyList(),
        chunks: List<RetrievedChunk> = emptyList(),
        strategy: String? = null
    ): PromptBuilder.ChatPromptResult {
        // Оптимизируем историю с учетом переданной стратегии
        val optimizedHistory = optimizeHistory(history, strategy = strategy)
        
        // Используем базовый PromptBuilder для формирования промпта
        return basePromptBuilder.buildChatPrompt(
            question = question,
            history = optimizedHistory,
            chunks = chunks
        )
    }
    
    /**
     * Получает статистику оптимизации истории
     */
    fun getOptimizationStats(originalHistory: List<ChatMessage>, optimizedHistory: List<ChatMessage>): OptimizationStats {
        val originalTokens = tokenCounter.countTokens(originalHistory.joinToString { it.content })
        val optimizedTokens = tokenCounter.countTokens(optimizedHistory.joinToString { it.content })
        
        return OptimizationStats(
            originalMessagesCount = originalHistory.size,
            optimizedMessagesCount = optimizedHistory.size,
            originalTokens = originalTokens,
            optimizedTokens = optimizedTokens,
            tokensSaved = originalTokens - optimizedTokens
        )
    }
}

/**
 * Статистика оптимизации истории
 */
data class OptimizationStats(
    val originalMessagesCount: Int,
    val optimizedMessagesCount: Int,
    val originalTokens: Int,
    val optimizedTokens: Int,
    val tokensSaved: Int
)

