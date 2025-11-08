package com.prike.domain.agent.model

enum class ReasoningMode {
    ALL,
    DIRECT,
    STEP_BY_STEP,
    PROMPT_FROM_OTHER_AI,
    EXPERT_PANEL,
    COMPARISON;

    companion object {
        fun fromString(raw: String?): ReasoningMode {
            return when (raw?.trim()?.lowercase()) {
                "direct" -> DIRECT
                "step", "step_by_step", "step-by-step" -> STEP_BY_STEP
                "prompt", "prompt_from_other_ai", "prompt-from-other-ai" -> PROMPT_FROM_OTHER_AI
                "experts", "expert_panel", "expert-panel" -> EXPERT_PANEL
                "comparison", "compare" -> COMPARISON
                else -> ALL
            }
        }
    }
}

data class ReasoningContext(
    val task: String,
    val results: Map<ReasoningMode, ReasoningOutput>
)

sealed class ReasoningOutput {
    data class Direct(val result: ModeResult) : ReasoningOutput()
    data class StepByStep(val result: ModeResult) : ReasoningOutput()
    data class PromptFromOtherAI(val prompt: PromptResult, val answer: ModeResult) : ReasoningOutput()
    data class ExpertPanel(val panel: ExpertPanelResult) : ReasoningOutput()
    data class Comparison(val comparison: ComparisonResult) : ReasoningOutput()
}

data class ModeResult(
    val prompt: String,
    val answer: String,
    val debug: DebugInfo
)

data class PromptResult(
    val generatedPrompt: String,
    val note: String,
    val usedFallback: Boolean,
    val debug: DebugInfo
)

data class ExpertPanelEntry(
    val name: String,
    val style: String,
    val answer: String,
    val reasoning: String,
    val debug: DebugInfo
)

data class ExpertPanelResult(
    val experts: List<ExpertPanelEntry>,
    val summary: String,
    val summaryDebug: DebugInfo
)

data class ComparisonResult(
    val summary: String,
    val debug: DebugInfo
)

data class DebugInfo(
    val llmRequest: String,
    val llmResponse: String
)


