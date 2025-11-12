package com.prike.domain.agent

import com.prike.config.AIConfig
import com.prike.config.TokenUsageLessonConfig
import com.prike.config.TokenUsageScenarioConfig
import com.prike.domain.exception.AIServiceException
import com.prike.domain.repository.AIRepository
import com.prike.domain.repository.AIResponseFormat
import com.prike.domain.repository.ModelInvocationRequest
import com.prike.domain.service.TokenCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.max

class TokenUsageAgent(
    private val aiRepository: AIRepository,
    private val lessonConfig: TokenUsageLessonConfig,
    private val aiConfig: AIConfig,
    private val tokenCounter: TokenCounter
) {

    private val logger = LoggerFactory.getLogger(TokenUsageAgent::class.java)
    private val history: ArrayDeque<TokenUsageRun> = ArrayDeque()
    private val historyMutex = Mutex()
    private val historyLimit = max(lessonConfig.historyLimit, 0)

    fun getScenarioTemplates(): List<ScenarioTemplate> =
        lessonConfig.scenarios.map { config ->
            ScenarioTemplate(
                scenarioId = config.id,
                scenarioName = config.name,
                defaultPrompt = config.defaultPrompt,
                description = config.description
            )
        }

    fun getPromptTokenLimit(): Int = lessonConfig.promptTokenLimit

    fun getDefaultMaxResponseTokens(): Int = lessonConfig.defaultMaxResponseTokens

    fun getTokenEncoding(): String = lessonConfig.tokenEncoding

    suspend fun analyze(command: AnalyzeTokenUsageCommand): TokenUsageRun = coroutineScope {
        val overrides = command.scenarios.associate { it.scenarioId to it.promptText?.trim() }

        val plans = lessonConfig.scenarios.map { scenario ->
            val prompt = overrides[scenario.id]
                ?.takeIf { !it.isNullOrBlank() }
                ?: scenario.defaultPrompt
            ScenarioExecutionPlan(config = scenario, prompt = prompt)
        }

        val startedAt = Instant.now()

        val results = plans.map { plan ->
            async(Dispatchers.IO) {
                executeScenario(plan)
            }
        }.awaitAll()

        val finishedAt = Instant.now()
        val run = TokenUsageRun(
            runId = UUID.randomUUID().toString(),
            startedAt = startedAt,
            finishedAt = finishedAt,
            results = results
        )

        historyMutex.withLock {
            if (historyLimit > 0) {
                history.addFirst(run)
                while (history.size > historyLimit) {
                    history.removeLast()
                }
            }
        }

        run
    }

    suspend fun getHistory(): List<TokenUsageRun> = historyMutex.withLock {
        history.toList()
    }

    private suspend fun executeScenario(plan: ScenarioExecutionPlan): TokenUsageScenarioResult {
        val promptTokens = tokenCounter.count(plan.prompt)
        val maxResponseTokens = plan.config.maxResponseTokens
            ?: aiConfig.maxTokens
            ?: lessonConfig.defaultMaxResponseTokens
        val temperature = plan.config.temperature ?: aiConfig.temperature
        val modelId = aiConfig.model
            ?: throw IllegalStateException("В файле config/ai.yaml необходимо указать поле model для работы урока.")

        if (promptTokens > lessonConfig.promptTokenLimit) {
            return TokenUsageScenarioResult(
                scenarioId = plan.config.id,
                scenarioName = plan.config.name,
                promptText = plan.prompt,
                responseText = null,
                promptTokens = promptTokens,
                responseTokens = null,
                totalTokens = promptTokens,
                durationMs = 0,
                status = ScenarioStatus.ERROR,
                errorMessage = "Превышен лимит токенов (${lessonConfig.promptTokenLimit}) для выбранной модели."
            )
        }

        val startedAt = System.currentTimeMillis()
        return runCatching {
            val completion = aiRepository.getCompletion(
                ModelInvocationRequest(
                    prompt = plan.prompt,
                    modelId = modelId,
                    endpoint = aiConfig.apiUrl,
                    temperature = temperature,
                    maxTokens = maxResponseTokens,
                    systemPrompt = aiConfig.systemPrompt,
                    responseFormat = if (aiConfig.useJsonFormat) {
                        AIResponseFormat.JSON_OBJECT
                    } else {
                        AIResponseFormat.TEXT
                    }
                )
            )

            val duration = completion.meta.durationMs ?: (System.currentTimeMillis() - startedAt)
            val responseText = completion.content
            val responseTokens = completion.meta.completionTokens ?: tokenCounter.count(responseText)
            val totalTokens = completion.meta.totalTokens
                ?: (completion.meta.promptTokens ?: promptTokens) + responseTokens
            val status = when {
                totalTokens >= lessonConfig.promptTokenLimit -> ScenarioStatus.TRUNCATED
                completion.meta.completionTokens != null &&
                    completion.meta.completionTokens >= maxResponseTokens -> ScenarioStatus.TRUNCATED
                else -> ScenarioStatus.SUCCESS
            }

            TokenUsageScenarioResult(
                scenarioId = plan.config.id,
                scenarioName = plan.config.name,
                promptText = plan.prompt,
                responseText = responseText,
                promptTokens = promptTokens,
                responseTokens = responseTokens,
                totalTokens = totalTokens,
                durationMs = duration,
                status = status,
                errorMessage = null
            )
        }.getOrElse { throwable ->
            val duration = System.currentTimeMillis() - startedAt
            val message = throwable.message?.trim().orEmpty()
            val status = if (message.contains("maximum context length", ignoreCase = true) ||
                message.contains("context length", ignoreCase = true) ||
                message.contains("token limit", ignoreCase = true)
            ) {
                ScenarioStatus.TRUNCATED
            } else {
                ScenarioStatus.ERROR
            }

            if (throwable is AIServiceException) {
                logger.warn("API вернул ошибку по сценарию ${plan.config.id}: ${throwable.message}")
            } else {
                logger.error("Ошибка выполнения сценария ${plan.config.id}", throwable)
            }

            TokenUsageScenarioResult(
                scenarioId = plan.config.id,
                scenarioName = plan.config.name,
                promptText = plan.prompt,
                responseText = null,
                promptTokens = promptTokens,
                responseTokens = null,
                totalTokens = promptTokens,
                durationMs = duration,
                status = status,
                errorMessage = message.ifBlank { "Неизвестная ошибка" }
            )
        }
    }

    data class ScenarioTemplate(
        val scenarioId: String,
        val scenarioName: String,
        val defaultPrompt: String,
        val description: String?
    )

    data class AnalyzeTokenUsageCommand(
        val scenarios: List<ScenarioOverride>
    ) {
        companion object {
            val EMPTY = AnalyzeTokenUsageCommand(emptyList())
        }
    }

    data class ScenarioOverride(
        val scenarioId: String,
        val promptText: String?
    )

    data class TokenUsageRun(
        val runId: String,
        val startedAt: Instant,
        val finishedAt: Instant,
        val results: List<TokenUsageScenarioResult>
    )

    data class TokenUsageScenarioResult(
        val scenarioId: String,
        val scenarioName: String,
        val promptText: String,
        val responseText: String?,
        val promptTokens: Int,
        val responseTokens: Int?,
        val totalTokens: Int,
        val durationMs: Long,
        val status: ScenarioStatus,
        val errorMessage: String?
    )

    enum class ScenarioStatus {
        SUCCESS,
        TRUNCATED,
        ERROR
    }

    private data class ScenarioExecutionPlan(
        val config: TokenUsageScenarioConfig,
        val prompt: String
    )
}

