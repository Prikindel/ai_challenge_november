package com.prike.domain.service

import com.prike.domain.model.AnswerWithCitations
import com.prike.domain.model.Citation
import com.prike.domain.model.TextPosition
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * Парсер цитат из ответов LLM
 * 
 * Поддерживает несколько форматов:
 * 1. Markdown: [Источник: название](путь)
 * 2. Нумерованные: [1] название (путь)
 * 3. Простые: Источник: путь
 */
class CitationParser {
    private val logger = LoggerFactory.getLogger(CitationParser::class.java)
    
    companion object {
        // Markdown формат: [Источник: название](путь)
        private val MARKDOWN_PATTERN = Pattern.compile(
            "\\[Источник:\\s*([^\\]]+)\\]\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
        )
        
        // Нумерованный формат: [1] название (путь) или [1] путь
        private val NUMBERED_PATTERN = Pattern.compile(
            "\\[\\d+\\]\\s*(?:([^(]+)\\s*)?\\(([^)]+)\\)|\\[\\d+\\]\\s+([^\\s]+)",
            Pattern.CASE_INSENSITIVE
        )
        
        // Простой формат: Источник: путь или Источник: название - путь
        private val SIMPLE_PATTERN = Pattern.compile(
            "(?:Источник|Source):\\s*(?:([^:\\-]+)(?:[-–]|:))?\\s*([^\\s,;.]+)",
            Pattern.CASE_INSENSITIVE
        )
    }
    
    /**
     * Парсит цитаты из ответа LLM
     * 
     * @param rawAnswer оригинальный ответ от LLM
     * @param availableDocuments список доступных документов из контекста (путь -> название)
     * @return ответ с извлечёнными цитатами
     */
    fun parseCitations(
        rawAnswer: String,
        availableDocuments: Map<String, String> = emptyMap()
    ): AnswerWithCitations {
        val citations = mutableListOf<Citation>()
        val answer = rawAnswer // Пока оставляем оригинальный ответ, можем очистить позже
        
        // Пробуем все форматы
        parseMarkdownCitations(rawAnswer, availableDocuments, citations)
        parseNumberedCitations(rawAnswer, availableDocuments, citations)
        parseSimpleCitations(rawAnswer, availableDocuments, citations)
        
        // Удаляем дубликаты (по пути документа)
        val uniqueCitations = citations.distinctBy { it.documentPath }
        
        logger.debug("Parsed ${uniqueCitations.size} citations from answer (total found: ${citations.size})")
        
        return AnswerWithCitations(
            answer = answer,
            citations = uniqueCitations,
            rawAnswer = rawAnswer
        )
    }
    
    /**
     * Парсит Markdown формат: [Источник: название](путь)
     */
    private fun parseMarkdownCitations(
        text: String,
        availableDocuments: Map<String, String>,
        citations: MutableList<Citation>
    ) {
        val matcher = MARKDOWN_PATTERN.matcher(text)
        
        while (matcher.find()) {
            val title = matcher.group(1)?.trim() ?: ""
            val path = matcher.group(2)?.trim() ?: ""
            
            if (path.isNotEmpty()) {
                val documentTitle = title.ifEmpty { 
                    availableDocuments[path] ?: extractTitleFromPath(path)
                }
                
                val citationText = matcher.group(0) ?: ""
                val start = matcher.start()
                val end = matcher.end()
                
                citations.add(
                    Citation(
                        text = citationText,
                        documentPath = path,
                        documentTitle = documentTitle,
                        position = TextPosition(start = start, end = end)
                    )
                )
            }
        }
    }
    
    /**
     * Парсит нумерованный формат: [1] название (путь) или [1] путь
     */
    private fun parseNumberedCitations(
        text: String,
        availableDocuments: Map<String, String>,
        citations: MutableList<Citation>
    ) {
        val matcher = NUMBERED_PATTERN.matcher(text)
        
        while (matcher.find()) {
            val title = matcher.group(1)?.trim()
            val pathInParens = matcher.group(2)?.trim()
            val pathSimple = matcher.group(3)?.trim()
            
            val path = pathInParens ?: pathSimple ?: continue
            
            val documentTitle = (title ?: availableDocuments[path] ?: extractTitleFromPath(path))
            val citationText = matcher.group(0) ?: ""
            val start = matcher.start()
            val end = matcher.end()
            
            citations.add(
                Citation(
                    text = citationText,
                    documentPath = path,
                    documentTitle = documentTitle,
                    position = TextPosition(start = start, end = end)
                )
            )
        }
    }
    
    /**
     * Парсит простой формат: Источник: путь или Источник: название - путь
     */
    private fun parseSimpleCitations(
        text: String,
        availableDocuments: Map<String, String>,
        citations: MutableList<Citation>
    ) {
        val matcher = SIMPLE_PATTERN.matcher(text)
        
        while (matcher.find()) {
            val title = matcher.group(1)?.trim()
            val path = matcher.group(2)?.trim() ?: continue
            
            val documentTitle = (title ?: availableDocuments[path] ?: extractTitleFromPath(path))
            val citationText = matcher.group(0) ?: ""
            val start = matcher.start()
            val end = matcher.end()
            
            citations.add(
                Citation(
                    text = citationText,
                    documentPath = path,
                    documentTitle = documentTitle,
                    position = TextPosition(start = start, end = end)
                )
            )
        }
    }
    
    /**
     * Извлекает название документа из пути
     */
    private fun extractTitleFromPath(path: String): String {
        // Пробуем извлечь имя файла без расширения
        val fileName = path.split("/").lastOrNull()?.split("\\.")?.firstOrNull() ?: path
        // Заменяем дефисы и подчёркивания на пробелы и делаем первую букву заглавной
        return fileName
            .replace(Regex("[-_]"), " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
    
    /**
     * Валидирует цитату - проверяет, что документ был в контексте
     */
    fun validateCitation(
        citation: Citation,
        availableDocuments: Set<String>
    ): Boolean {
        val normalizedPath = normalizePath(citation.documentPath)
        return availableDocuments.any { normalizePath(it) == normalizedPath }
    }
    
    /**
     * Нормализует путь для сравнения (удаляет лишние слэши, нормализует разделители)
     */
    private fun normalizePath(path: String): String {
        return path
            .replace("\\", "/")
            .replace(Regex("/+"), "/")
            .trim('/')
    }
}

