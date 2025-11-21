package com.prike.mcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Обработчик для инструмента analyze_sentiment
 * Анализирует тональность текста (позитивная, негативная, нейтральная)
 */
class AnalyzeSentimentHandler : ToolHandler<AnalyzeSentimentHandler.Params, AnalyzeSentimentHandler.SentimentResult>() {

    override val logger = LoggerFactory.getLogger(AnalyzeSentimentHandler::class.java)

    override fun execute(params: Params): SentimentResult {
        logger.info("Анализ тональности текста (длина: ${params.text.length} символов)")
        
        // Простой алгоритм анализа тональности на основе ключевых слов
        val positiveWords = listOf(
            "хорошо", "отлично", "прекрасно", "замечательно", "великолепно",
            "рад", "доволен", "нравится", "люблю", "спасибо", "благодарю",
            "супер", "класс", "круто", "здорово", "ура", "победа", "успех",
            "хороший", "отличный", "прекрасный", "замечательный", "лучший"
        )
        
        val negativeWords = listOf(
            "плохо", "ужасно", "отвратительно", "кошмар", "беда", "проблема",
            "недоволен", "не нравится", "ненавижу", "злюсь", "разочарован",
            "ужасный", "плохой", "отвратительный", "кошмарный", "проблемный",
            "ошибка", "не работает", "сломалось", "не могу", "не получается"
        )
        
        val textLower = params.text.lowercase()
        val positiveCount = positiveWords.count { textLower.contains(it) }
        val negativeCount = negativeWords.count { textLower.contains(it) }
        
        val sentiment = when {
            positiveCount > negativeCount * 1.5 -> "positive"
            negativeCount > positiveCount * 1.5 -> "negative"
            else -> "neutral"
        }
        
        val totalWords = positiveCount + negativeCount
        val score = when {
            totalWords == 0 -> 0.5
            sentiment == "positive" -> 0.5 + (positiveCount.toDouble() / (totalWords * 2))
            sentiment == "negative" -> 0.5 - (negativeCount.toDouble() / (totalWords * 2))
            else -> 0.5
        }.coerceIn(0.0, 1.0)
        
        val explanation = when {
            sentiment == "positive" -> "Текст содержит преимущественно позитивные слова (найдено $positiveCount позитивных и $negativeCount негативных слов)"
            sentiment == "negative" -> "Текст содержит преимущественно негативные слова (найдено $negativeCount негативных и $positiveCount позитивных слов)"
            else -> "Текст имеет нейтральную тональность (найдено $positiveCount позитивных и $negativeCount негативных слов)"
        }
        
        logger.info("Результат анализа: $sentiment (score: $score)")
        
        return SentimentResult(
            sentiment = sentiment,
            score = score,
            explanation = explanation
        )
    }

    override fun prepareResult(request: Params, result: SentimentResult): TextContent {
        val resultJson = buildJsonObject {
            put("sentiment", result.sentiment)
            put("score", result.score)
            put("explanation", result.explanation)
        }
        
        return TextContent(text = resultJson.toString())
    }

    data class Params(
        val text: String
    )
    
    data class SentimentResult(
        val sentiment: String,  // "positive", "negative", "neutral"
        val score: Double,  // 0.0 - 1.0
        val explanation: String
    )
}

