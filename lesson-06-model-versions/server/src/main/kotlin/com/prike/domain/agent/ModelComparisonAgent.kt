package com.prike.domain.agent

import com.prike.config.ModelComparisonLessonConfig
import com.prike.config.ModelDefinitionConfig
import com.prike.domain.exception.ValidationException
import com.prike.domain.repository.AIRepository
import com.prike.domain.repository.AIResponseFormat
import com.prike.domain.repository.ModelInvocationRequest
import kotlin.math.max
import kotlin.math.round

class ModelComparisonAgent(
    private val aiRepository: AIRepository,
    private val lessonConfig: ModelComparisonLessonConfig
) {

    fun getDefaultQuestion(): String = lessonConfig.defaultQuestion

    fun getDefaultModelIds(): List<String> = lessonConfig.defaultModelIds

    fun getAvailableModels(): List<ModelDefinitionConfig> = lessonConfig.models

    suspend fun compare(
        requestedQuestion: String?,
        requestedModelIds: List<String>?
    ): Result {
        val question = sanitizeQuestion(requestedQuestion)
        val models = resolveModels(requestedModelIds)

        val runs = models.map { model ->
            val settings = buildInvocationSettings(model)
            val completion = aiRepository.getCompletion(
                ModelInvocationRequest(
                    prompt = buildAnswerPrompt(question),
                    modelId = model.id,
                    endpoint = model.endpoint,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    systemPrompt = settings.systemPrompt,
                    responseFormat = AIResponseFormat.TEXT,
                    additionalParams = settings.additionalParams
                )
            )

            val costUsd = computeCostUsd(
                pricePer1kTokensUsd = model.pricePer1kTokensUsd,
                totalTokens = completion.meta.totalTokens
            )

            ModelRun(
                modelId = model.id,
                displayName = model.displayName,
                huggingFaceUrl = model.huggingFaceUrl,
                answer = completion.content,
                meta = ModelRunMeta(
                    durationMs = completion.meta.durationMs,
                    promptTokens = completion.meta.promptTokens,
                    completionTokens = completion.meta.completionTokens,
                    totalTokens = completion.meta.totalTokens,
                    costUsd = costUsd
                )
            )
        }

        val summary = runCatching {
            generateSummary(question, runs, models.first())
        }.getOrElse {
            fallbackSummary(runs)
        }

        val modelLinks = models.map { model ->
            ModelLink(
                modelId = model.id,
                huggingFaceUrl = model.huggingFaceUrl
            )
        }

        return Result(
            defaultQuestion = lessonConfig.defaultQuestion,
            defaultModelIds = lessonConfig.defaultModelIds,
            question = question,
            modelResults = runs,
            comparisonSummary = summary,
            modelLinks = modelLinks
        )
    }

    private fun sanitizeQuestion(requestedQuestion: String?): String {
        val normalized = requestedQuestion
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: lessonConfig.defaultQuestion

        if (normalized.length > MAX_QUESTION_LENGTH) {
            throw ValidationException("Вопрос слишком длинный (максимум $MAX_QUESTION_LENGTH символов)")
        }

        return normalized
    }

    private fun resolveModels(requestedModelIds: List<String>?): List<ModelDefinitionConfig> {
        val catalog = lessonConfig.models.associateBy { it.id }

        val uniqueRequested = requestedModelIds
            ?.mapNotNull { it?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: lessonConfig.defaultModelIds

        if (uniqueRequested.isEmpty()) {
            throw ValidationException("Не выбраны модели для запуска")
        }

        val resolved = uniqueRequested.map { id ->
            catalog[id] ?: throw ValidationException("Модель с идентификатором $id недоступна")
        }

        return resolved
    }

    private fun buildAnswerPrompt(question: String): String = buildString {
        appendLine("Ты — разговорный ассистент. Отвечай на русском языке.")
        appendLine("В ответе придерживайся структуры: краткий вывод, затем объяснение или детали.")
        appendLine()
        appendLine("Вопрос пользователя:")
        appendLine(question)
    }

    private suspend fun generateSummary(
        question: String,
        runs: List<ModelRun>,
        referenceModel: ModelDefinitionConfig
    ): String {
        val settings = buildInvocationSettings(referenceModel)
        val answersBlock = runs.joinToString(separator = "\n\n") { run ->
            """
            Модель: ${run.displayName} (${run.modelId})
            Ответ:
            ${run.answer}
            """.trimIndent()
        }

        val prompt = buildString {
            appendLine("Проанализируй ответы разных моделей на один вопрос.")
            appendLine("Сформулируй 2–4 предложения на русском языке: опиши ключевые различия, сильные и слабые стороны и порекомендуй, когда стоит выбрать каждую модель.")
            appendLine()
            appendLine("Вопрос:")
            appendLine(question)
            appendLine()
            appendLine("Ответы моделей:")
            appendLine(answersBlock)
        }

        val completion = aiRepository.getCompletion(
            ModelInvocationRequest(
                prompt = prompt,
                modelId = referenceModel.id,
                endpoint = referenceModel.endpoint,
                temperature = settings.temperature,
                maxTokens = max(settings.maxTokens ?: DEFAULT_SUMMARY_MAX_TOKENS, DEFAULT_SUMMARY_MAX_TOKENS),
                systemPrompt = settings.systemPrompt,
                responseFormat = AIResponseFormat.TEXT,
                additionalParams = settings.additionalParams
            )
        )

        return completion.content
    }

    private fun fallbackSummary(runs: List<ModelRun>): String {
        if (runs.isEmpty()) {
            return "Не удалось сформировать сравнение: нет результатов."
        }

        return runs.joinToString(
            separator = " ",
            postfix = " Сформируйте собственный вывод на основе приведённых ответов."
        ) { run ->
            val duration = run.meta.durationMs?.let { "${it} мс" } ?: "время неизвестно"
            "Модель ${run.displayName} ответила за $duration."
        }
    }

    private fun buildInvocationSettings(model: ModelDefinitionConfig): InvocationSettings {
        val params = model.defaultParams
        val temperature = (params["temperature"] as? Number)?.toDouble()
        val maxTokens = (params["max_tokens"] as? Number)?.toInt()
            ?: (params["maxTokens"] as? Number)?.toInt()
        val systemPrompt = params["system_prompt"] as? String
            ?: params["systemPrompt"] as? String

        val additionalParams = params.filterKeys { key ->
            key !in setOf("temperature", "max_tokens", "maxTokens", "system_prompt", "systemPrompt")
        }

        return InvocationSettings(
            temperature = temperature,
            maxTokens = maxTokens,
            systemPrompt = systemPrompt,
            additionalParams = additionalParams
        )
    }

    private fun computeCostUsd(
        pricePer1kTokensUsd: Double?,
        totalTokens: Int?
    ): Double? {
        if (pricePer1kTokensUsd == null || totalTokens == null) {
            return null
        }
        val raw = pricePer1kTokensUsd * (totalTokens.toDouble() / 1000.0)
        return round(raw * 100.0) / 100.0
    }

    data class Result(
        val defaultQuestion: String,
        val defaultModelIds: List<String>,
        val question: String,
        val modelResults: List<ModelRun>,
        val comparisonSummary: String,
        val modelLinks: List<ModelLink>
    )

    data class ModelRun(
        val modelId: String,
        val displayName: String,
        val huggingFaceUrl: String,
        val answer: String,
        val meta: ModelRunMeta
    )

    data class ModelRunMeta(
        val durationMs: Long?,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val totalTokens: Int?,
        val costUsd: Double?
    )

    data class ModelLink(
        val modelId: String,
        val huggingFaceUrl: String
    )

    private data class InvocationSettings(
        val temperature: Double?,
        val maxTokens: Int?,
        val systemPrompt: String?,
        val additionalParams: Map<String, Any?>
    )

    companion object {
        private const val MAX_QUESTION_LENGTH = 4000
        private const val DEFAULT_SUMMARY_MAX_TOKENS = 512
    }
}

