package com.prike.domain.agent

import com.prike.domain.entity.LLMCompletionMeta
import com.prike.domain.exception.ValidationException
import com.prike.domain.repository.AIRepository
import com.prike.domain.repository.AIResponseFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.round
import java.util.Locale

class TemperatureAgent(
    private val aiRepository: AIRepository,
    private val defaultQuestion: String = DEFAULT_QUESTION,
    private val defaultTemperatures: List<Double> = DEFAULT_TEMPERATURES,
    private val comparisonTemperature: Double = DEFAULT_COMPARISON_TEMPERATURE
) {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun analyze(
        requestedQuestion: String?,
        requestedTemperatures: List<Double>?
    ): Result {
        val trimmedQuestion = requestedQuestion?.trim()
        val question = if (trimmedQuestion.isNullOrEmpty()) {
            defaultQuestion
        } else {
            validateQuestion(trimmedQuestion)
            trimmedQuestion
        }

        val temperatures = sanitizeTemperatures(requestedTemperatures)

        val runResults = temperatures.map { temperature ->
            val completion = aiRepository.getCompletion(
                prompt = buildTaskPrompt(question),
                temperature = temperature,
                responseFormat = AIResponseFormat.TEXT
            )
            TemperatureRun(
                mode = buildModeLabel(temperature),
                temperature = temperature,
                answer = completion.content,
                meta = completion.meta
            )
        }

        val comparison = buildComparison(question, runResults)

        return Result(
            defaultQuestion = defaultQuestion,
            defaultTemperatures = defaultTemperatures.toList(),
            question = question,
            results = runResults,
            comparison = comparison
        )
    }

    fun getDefaultQuestion(): String = defaultQuestion

    fun getDefaultTemperatures(): List<Double> = defaultTemperatures.toList()

    private fun sanitizeTemperatures(input: List<Double>?): List<Double> {
        val sanitized = (input ?: defaultTemperatures)
            .mapNotNull { temperature ->
                val bounded = temperature.takeIf { !it.isNaN() && it.isFinite() }?.let {
                    it.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
                }
                bounded
            }
            .map { round(it * 100) / 100.0 }

        if (sanitized.isEmpty()) {
            return defaultTemperatures
        }

        val distinct = mutableListOf<Double>()
        sanitized.forEach { temperature ->
            if (distinct.none { nearlyEqual(it, temperature) }) {
                distinct.add(temperature)
            }
        }
        return distinct
    }

    private suspend fun buildComparison(
        question: String,
        runs: List<TemperatureRun>
    ): ComparisonResult {
        val prompt = buildComparisonPrompt(question, runs)

        val analysis = runCatching {
            val completion = aiRepository.getCompletion(
                prompt = prompt,
                temperature = comparisonTemperature,
                responseFormat = AIResponseFormat.JSON_OBJECT
            )
            jsonParser.decodeFromString<ComparisonAnalysis>(completion.content)
        }.getOrNull()

        if (analysis == null) {
            return fallbackComparison(runs)
        }

        val recommendations = runs.map { run ->
            val entry = analysis.perTemperature
                .minByOrNull { abs(it.temperature - run.temperature) }
                ?.takeIf { abs(it.temperature - run.temperature) <= TEMPERATURE_MATCH_EPS }

            entry?.toRecommendation() ?: defaultRecommendation(run.temperature, run.mode)
        }

        return ComparisonResult(
            summary = analysis.summary,
            perTemperature = recommendations
        )
    }

    private fun buildTaskPrompt(question: String): String = buildString {
        appendLine("Ты аналитический ассистент, который решает логические и аналитические задачи.")
        appendLine("Отвечай на русском языке. Сначала кратко сформулируй итоговый ответ, затем приведи пошаговое объяснение рассуждений.")
        appendLine()
        appendLine("Задача:")
        appendLine(question)
    }

    private fun buildComparisonPrompt(
        question: String,
        runs: List<TemperatureRun>
    ): String {
        val answersBlock = runs.joinToString(separator = "\n\n") { run ->
            """
            Температура: ${formatTemperature(run.temperature)}
            Режим: ${run.mode}
            Ответ:
            ${run.answer}
            """.trimIndent()
        }

        return buildString {
            appendLine("Ты эксперт по аналитике LLM. Сравни ответы модели на один и тот же вопрос при разных температурах.")
            appendLine("Определи, как температура влияет на точность, креативность и разнообразие.")
            appendLine("Ответ должен быть JSON с полями:")
            appendLine("""{
  "summary": "краткий обзор различий и ключевых выводов",
  "perTemperature": [
    {
      "temperature": <число>,
      "mode": "текстовое название режима",
      "accuracy": "оценка точности и фактической корректности",
      "creativity": "оценка креативности и вариативности",
      "diversity": "оценка разнообразия идей или структуры",
      "recommendation": "когда полезно использовать такую температуру"
    }
  ]
}""")
            appendLine("Важно: верни строго валидный JSON без комментариев и дополнительного текста.")
            appendLine()
            appendLine("Вопрос пользователя:")
            appendLine(question)
            appendLine()
            appendLine("Ответы модели:")
            appendLine(answersBlock)
        }
    }

    private fun fallbackComparison(runs: List<TemperatureRun>): ComparisonResult {
        val summary = buildString {
            appendLine("Не удалось получить автоматический анализ от модели. Сформирована рекомендация по умолчанию на основе температуры.")
        }.trim()

        val recommendations = runs.map { run ->
            defaultRecommendation(run.temperature, run.mode)
        }

        return ComparisonResult(
            summary = summary,
            perTemperature = recommendations
        )
    }

    private fun defaultRecommendation(
        temperature: Double,
        mode: String
    ): TemperatureRecommendation {
        val category = categorizeTemperature(temperature)
        val (accuracy, creativity, diversity, recommendation) = when (category) {
            TemperatureCategory.LOW -> RecommendationTemplates.low
            TemperatureCategory.MEDIUM -> RecommendationTemplates.medium
            TemperatureCategory.HIGH -> RecommendationTemplates.high
        }
        return TemperatureRecommendation(
            temperature = temperature,
            mode = mode,
            accuracy = accuracy,
            creativity = creativity,
            diversity = diversity,
            recommendation = recommendation
        )
    }

    private fun buildModeLabel(temperature: Double): String =
        "Температура ${formatTemperature(temperature)}"

    private fun formatTemperature(value: Double): String =
        String.format(Locale.US, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')

    private fun nearlyEqual(a: Double, b: Double): Boolean =
        abs(a - b) <= TEMPERATURE_MATCH_EPS

    private fun categorizeTemperature(temperature: Double): TemperatureCategory = when {
        temperature < 0.35 -> TemperatureCategory.LOW
        temperature < 0.85 -> TemperatureCategory.MEDIUM
        else -> TemperatureCategory.HIGH
    }

    data class Result(
        val defaultQuestion: String,
        val defaultTemperatures: List<Double>,
        val question: String,
        val results: List<TemperatureRun>,
        val comparison: ComparisonResult
    )

    data class TemperatureRun(
        val mode: String,
        val temperature: Double,
        val answer: String,
        val meta: LLMCompletionMeta
    )

    data class ComparisonResult(
        val summary: String,
        val perTemperature: List<TemperatureRecommendation>
    )

    data class TemperatureRecommendation(
        val temperature: Double,
        val mode: String,
        val accuracy: String,
        val creativity: String,
        val diversity: String,
        val recommendation: String
    )

    @Serializable
    private data class ComparisonAnalysis(
        val summary: String,
        val perTemperature: List<ComparisonEntry>
    )

    @Serializable
    private data class ComparisonEntry(
        val temperature: Double,
        val mode: String,
        val accuracy: String,
        val creativity: String,
        val diversity: String,
        val recommendation: String
    ) {
        fun toRecommendation(): TemperatureRecommendation = TemperatureRecommendation(
            temperature = temperature,
            mode = mode,
            accuracy = accuracy,
            creativity = creativity,
            diversity = diversity,
            recommendation = recommendation
        )
    }

    private enum class TemperatureCategory { LOW, MEDIUM, HIGH }

    private object RecommendationTemplates {
        val low = RecommendationDetails(
            accuracy = "Максимально точные и предсказуемые ответы, минимальный риск фантазий.",
            creativity = "Низкая креативность: модель следует наиболее вероятным формулировкам.",
            diversity = "Минимальное разнообразие: ответы лаконичны и повторяемы.",
            recommendation = "Используйте для задач, требующих точности: отчеты, проверка фактов, подготовка инструкций."
        )

        val medium = RecommendationDetails(
            accuracy = "Баланс фактической корректности и гибкости формулировок.",
            creativity = "Умеренная креативность и способность предложить несколько подходов.",
            diversity = "Среднее разнообразие: ответы развернуты и структурированы.",
            recommendation = "Подходит для аналитических задач, генерации объяснений и контента, где важны ясность и вариативность."
        )

        val high = RecommendationDetails(
            accuracy = "Ответ может содержать неточности, так как модель смелее фантазирует.",
            creativity = "Высокая креативность, нестандартные идеи и неожиданные ракурсы.",
            diversity = "Большое разнообразие формул и сценариев.",
            recommendation = "Используйте для мозговых штурмов, генерации идей, творческих концепций."
        )
    }

    private data class RecommendationDetails(
        val accuracy: String,
        val creativity: String,
        val diversity: String,
        val recommendation: String
    )

    companion object {
        private const val MIN_TEMPERATURE = 0.0
        private const val MAX_TEMPERATURE = 2.0
        private const val TEMPERATURE_MATCH_EPS = 0.05
        private const val DEFAULT_COMPARISON_TEMPERATURE = 0.4
        private const val MAX_QUESTION_LENGTH = 2000

        private val DEFAULT_TEMPERATURES = listOf(0.0, 0.7, 1.2)

        private val DEFAULT_QUESTION = """
            У нас есть три друга — Анна, Борис и Виктор. Они получили три разных подарка: книгу, игру и головоломку.

            Известно, что:
            1. Анна не получила игру.
            2. Борис не получил головоломку.

            Кто какой подарок получил? Объясни ход рассуждений.
        """.trimIndent()
    }

    private fun validateQuestion(question: String) {
        if (question.length > MAX_QUESTION_LENGTH) {
            throw ValidationException("Вопрос слишком длинный (максимум $MAX_QUESTION_LENGTH символов)")
        }
    }
}

