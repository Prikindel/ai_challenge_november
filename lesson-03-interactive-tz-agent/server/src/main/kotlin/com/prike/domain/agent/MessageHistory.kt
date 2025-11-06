package com.prike.domain.agent

import com.prike.data.dto.MessageDto

/**
 * Управление историей сообщений для агента
 * Хранит контекст разговора для передачи в LLM
 */
class MessageHistory(
    private val systemPrompt: String? = null,
    private val maxHistorySize: Int = 50
) {
    private val messages = mutableListOf<MessageDto>()
    
    init {
        systemPrompt?.let {
            messages.add(MessageDto(role = "system", content = it))
        }
    }
    
    /**
     * Добавить сообщение пользователя
     */
    fun addUserMessage(content: String) {
        messages.add(MessageDto(role = "user", content = content))
        trimHistory()
    }
    
    /**
     * Добавить ответ ассистента
     */
    fun addAssistantMessage(content: String) {
        messages.add(MessageDto(role = "assistant", content = content))
        trimHistory()
    }
    
    /**
     * Получить все сообщения для отправки в LLM
     */
    fun getMessages(): List<MessageDto> {
        return messages.toList()
    }
    
    /**
     * Очистить историю (кроме system prompt)
     */
    fun clear() {
        messages.clear()
        systemPrompt?.let {
            messages.add(MessageDto(role = "system", content = it))
        }
    }
    
    /**
     * Обрезать историю, если она слишком длинная
     * Оставляет system prompt и последние N сообщений
     */
    private fun trimHistory() {
        if (messages.size > maxHistorySize) {
            val systemMessage = messages.firstOrNull { it.role == "system" }
            val recentMessages = messages
                .filter { it.role != "system" }
                .takeLast(maxHistorySize - 1)
            
            messages.clear()
            systemMessage?.let { messages.add(it) }
            messages.addAll(recentMessages)
        }
    }
    
    /**
     * Получить количество сообщений (без system prompt)
     */
    fun getMessageCount(): Int {
        return messages.count { it.role != "system" }
    }
}

