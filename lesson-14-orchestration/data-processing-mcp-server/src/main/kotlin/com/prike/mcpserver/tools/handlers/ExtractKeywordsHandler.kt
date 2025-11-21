package com.prike.mcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Обработчик для инструмента extract_keywords
 * Извлекает ключевые слова из текста на основе частотного анализа
 */
class ExtractKeywordsHandler : ToolHandler<ExtractKeywordsHandler.Params, List<String>>() {

    override val logger = LoggerFactory.getLogger(ExtractKeywordsHandler::class.java)

    override fun execute(params: Params): List<String> {
        logger.info("Извлечение ключевых слов из текста (длина: ${params.text.length} символов, запрошено: ${params.count})")
        
        // Простой алгоритм извлечения ключевых слов на основе частотного анализа
        val stopWords = setOf(
            "и", "в", "на", "с", "по", "для", "от", "до", "из", "к", "о", "об", "со", "за", "при",
            "а", "но", "или", "что", "как", "так", "это", "то", "же", "бы", "ли", "был", "была", "было",
            "были", "есть", "быть", "был", "не", "нет", "да", "он", "она", "они", "мы", "вы", "я", "ты",
            "его", "её", "их", "мой", "твой", "наш", "ваш", "свой", "этот", "тот", "такой", "какой",
            "где", "когда", "куда", "откуда", "почему", "зачем", "как", "сколько", "чей", "который"
        )
        
        // Разбиваем текст на слова, убираем знаки препинания и приводим к нижнему регистру
        val words = params.text
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
        
        // Подсчитываем частоту слов
        val wordFrequency = words.groupingBy { it }.eachCount()
        
        // Сортируем по частоте и берём топ-N
        val keywords = wordFrequency
            .entries
            .sortedByDescending { it.value }
            .take(params.count)
            .map { it.key }
        
        logger.info("Извлечено ключевых слов: ${keywords.size}")
        
        return keywords
    }

    override fun prepareResult(request: Params, result: List<String>): TextContent {
        val resultJson = buildJsonArray {
            result.forEach { keyword ->
                add(keyword)
            }
        }
        
        return TextContent(text = resultJson.toString())
    }

    data class Params(
        val text: String,
        val count: Int = 10
    )
}

