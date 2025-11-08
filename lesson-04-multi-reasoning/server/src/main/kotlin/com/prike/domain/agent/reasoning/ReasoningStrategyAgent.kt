package com.prike.domain.agent.reasoning

import com.prike.data.repository.AIRepository
import com.prike.domain.agent.BaseAgent
import com.prike.domain.agent.model.DebugInfo
import com.prike.domain.agent.model.ReasoningContext
import com.prike.domain.agent.model.ReasoningMode
import com.prike.domain.agent.model.ReasoningOutput
import kotlinx.serialization.json.Json

abstract class ReasoningStrategyAgent(
    aiRepository: AIRepository
) : BaseAgent(aiRepository) {

    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    abstract val mode: ReasoningMode

    abstract suspend fun execute(context: ReasoningContext): ReasoningOutput

    protected fun AIRepository.MessageResult.toDebugInfo(): DebugInfo =
        DebugInfo(
            llmRequest = requestJson,
            llmResponse = responseJson
        )
}


