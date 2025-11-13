package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository

class DialogConversationAgent(
    aiRepository: AIRepository
) : BaseAgent(aiRepository) {

    suspend fun respond(
        messages: List<MessageDto>,
        options: AIRepository.ChatCompletionOptions = AIRepository.ChatCompletionOptions()
    ): AIRepository.MessageResult {
        return aiRepository.getMessageWithHistory(messages, options)
    }
}
