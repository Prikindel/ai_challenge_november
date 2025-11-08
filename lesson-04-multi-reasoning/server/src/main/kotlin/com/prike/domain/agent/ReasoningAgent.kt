package com.prike.domain.agent

import com.prike.domain.agent.model.DebugInfo
import com.prike.domain.agent.model.ModeResult
import com.prike.domain.agent.model.ReasoningMode
import com.prike.domain.agent.model.ReasoningOutput
import com.prike.domain.agent.model.ReasoningContext
import com.prike.domain.agent.model.ExpertPanelResult
import com.prike.domain.agent.reasoning.ComparisonReasoningAgent
import com.prike.domain.agent.reasoning.DirectReasoningAgent
import com.prike.domain.agent.reasoning.ExpertPanelReasoningAgent
import com.prike.domain.agent.reasoning.PromptFromOtherAIAgent
import com.prike.domain.agent.reasoning.ReasoningStrategyAgent
import com.prike.domain.agent.reasoning.StepByStepReasoningAgent

class ReasoningAgent(
    private val defaultTask: String = DEFAULT_TASK,
    private val directAgent: DirectReasoningAgent,
    private val stepAgent: StepByStepReasoningAgent,
    private val promptAgent: PromptFromOtherAIAgent,
    private val expertAgent: ExpertPanelReasoningAgent,
    private val comparisonAgent: ComparisonReasoningAgent
) {

    fun getDefaultTask(): String = defaultTask

    suspend fun solve(
        question: String? = null,
        mode: ReasoningMode = ReasoningMode.ALL
    ): ReasoningAgentResult {
        val task = question?.trim()?.takeIf { it.isNotEmpty() } ?: defaultTask
        val cache = mutableMapOf<ReasoningMode, ReasoningOutput>()

        when (mode) {
            ReasoningMode.ALL -> {
                ensureAllResults(task, cache)
            }
            ReasoningMode.COMPARISON -> {
                ensureComparisonResults(task, cache)
            }
            else -> {
                ensureResult(mode, task, cache)
            }
        }

        return buildResult(task, mode, cache)
    }

    private fun strategyFor(mode: ReasoningMode): ReasoningStrategyAgent = when (mode) {
        ReasoningMode.DIRECT -> directAgent
        ReasoningMode.STEP_BY_STEP -> stepAgent
        ReasoningMode.PROMPT_FROM_OTHER_AI -> promptAgent
        ReasoningMode.EXPERT_PANEL -> expertAgent
        ReasoningMode.COMPARISON -> comparisonAgent
        ReasoningMode.ALL -> error("Mode ALL does not map to a single strategy")
    }

    private suspend fun ensureResult(
        mode: ReasoningMode,
        task: String,
        cache: MutableMap<ReasoningMode, ReasoningOutput>
    ) {
        if (cache.containsKey(mode)) return
        val agent = strategyFor(mode)
        val context = ReasoningContext(task = task, results = cache.toMap())
        cache[mode] = agent.execute(context)
    }

    private suspend fun ensureBaselineResults(
        task: String,
        cache: MutableMap<ReasoningMode, ReasoningOutput>
    ) {
        ensureResult(ReasoningMode.DIRECT, task, cache)
        ensureResult(ReasoningMode.STEP_BY_STEP, task, cache)
        ensureResult(ReasoningMode.PROMPT_FROM_OTHER_AI, task, cache)
        ensureResult(ReasoningMode.EXPERT_PANEL, task, cache)
    }

    private suspend fun ensureComparisonResults(
        task: String,
        cache: MutableMap<ReasoningMode, ReasoningOutput>
    ) {
        ensureBaselineResults(task, cache)
        ensureResult(ReasoningMode.COMPARISON, task, cache)
    }

    private suspend fun ensureAllResults(
        task: String,
        cache: MutableMap<ReasoningMode, ReasoningOutput>
    ) {
        ensureComparisonResults(task, cache)
    }

    private suspend fun buildResult(
        task: String,
        requestedMode: ReasoningMode,
        cache: Map<ReasoningMode, ReasoningOutput>
    ): ReasoningAgentResult {
        val direct = (cache[ReasoningMode.DIRECT] as? ReasoningOutput.Direct)?.result
        val step = (cache[ReasoningMode.STEP_BY_STEP] as? ReasoningOutput.StepByStep)?.result
        val prompt = cache[ReasoningMode.PROMPT_FROM_OTHER_AI] as? ReasoningOutput.PromptFromOtherAI
        val experts = (cache[ReasoningMode.EXPERT_PANEL] as? ReasoningOutput.ExpertPanel)?.panel
        val comparison = (cache[ReasoningMode.COMPARISON] as? ReasoningOutput.Comparison)?.comparison

        return ReasoningAgentResult(
            task = task,
            mode = requestedMode,
            direct = direct,
            stepByStep = step,
            promptFromOtherAI = prompt?.let {
                PromptFromOtherAIResult(
                    generatedPrompt = it.prompt.generatedPrompt,
                    answer = it.answer.answer,
                    notes = it.prompt.note,
                    usedFallback = it.prompt.usedFallback,
                    promptDebug = it.prompt.debug,
                    answerDebug = it.answer.debug
                )
            },
            expertPanel = experts,
            comparison = comparison?.summary,
            comparisonDebug = comparison?.debug
        )
    }

    data class ReasoningAgentResult(
        val task: String,
        val mode: ReasoningMode,
        val direct: ModeResult? = null,
        val stepByStep: ModeResult? = null,
        val promptFromOtherAI: PromptFromOtherAIResult? = null,
        val expertPanel: ExpertPanelResult? = null,
        val comparison: String? = null,
        val comparisonDebug: DebugInfo? = null
    )

    data class PromptFromOtherAIResult(
        val generatedPrompt: String,
        val answer: String,
        val notes: String,
        val usedFallback: Boolean,
        val promptDebug: DebugInfo,
        val answerDebug: DebugInfo
    )

    companion object {
        const val DEFAULT_TASK =
            "У нас есть три друга — Анна, Борис и Виктор. Они получили три разных подарка: книгу, игру и головоломку.\n" +
                "Известно, что Анна не получила игру, Борис не получил головоломку. Кто что получил?"
    }
}

