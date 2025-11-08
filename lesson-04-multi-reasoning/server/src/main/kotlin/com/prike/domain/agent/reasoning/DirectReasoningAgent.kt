package com.prike.domain.agent.reasoning

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.model.ModeResult
import com.prike.domain.agent.model.ReasoningContext
import com.prike.domain.agent.model.ReasoningMode
import com.prike.domain.agent.model.ReasoningOutput

class DirectReasoningAgent(
    aiRepository: AIRepository
) : ReasoningStrategyAgent(aiRepository) {

    override val mode: ReasoningMode = ReasoningMode.DIRECT

    override suspend fun execute(context: ReasoningContext): ReasoningOutput {
        val prompt = ReasoningPromptTemplates.directPrompt(context.task)
        val result = getMessageWithHistory(
            listOf(
                MessageDto(role = "system", content = ReasoningPromptTemplates.BASE_SYSTEM_PROMPT.trim()),
                MessageDto(role = "user", content = prompt)
            )
        )
        val modeResult = ModeResult(
            prompt = prompt,
            answer = result.message,
            debug = result.toDebugInfo()
        )
        return ReasoningOutput.Direct(modeResult)
    }
}


