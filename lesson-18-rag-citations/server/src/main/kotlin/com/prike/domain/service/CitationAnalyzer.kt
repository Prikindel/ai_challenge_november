package com.prike.domain.service

import com.prike.domain.model.Citation
import com.prike.domain.model.CitationTestResult
import com.prike.domain.model.CitationMetrics
import com.prike.domain.model.CitationTestReport
import com.prike.domain.model.RAGRequest
import com.prike.domain.model.RAGResponse
import org.slf4j.LoggerFactory

/**
 * Анализатор цитат - тестирование и метрики
 */
class CitationAnalyzer(
    private val ragService: RAGService
) {
    private val logger = LoggerFactory.getLogger(CitationAnalyzer::class.java)
    
    /**
     * Тестирует систему на нескольких вопросах
     * 
     * @param questions список вопросов для тестирования
     * @param topK количество чанков для контекста
     * @param minSimilarity минимальное сходство
     * @param applyFilter применять ли фильтр
     * @param strategy стратегия фильтрации
     * @return отчёт о тестировании
     */
    suspend fun testCitations(
        questions: List<String>,
        topK: Int = 5,
        minSimilarity: Float = 0.4f,
        applyFilter: Boolean = true,
        strategy: String = "hybrid"
    ): CitationTestReport {
        logger.info("Starting citation test with ${questions.size} questions")
        
        val results = questions.map { question ->
            testQuestion(question, topK, minSimilarity, applyFilter, strategy)
        }
        
        val metrics = calculateMetrics(results)
        
        logger.info("Citation test completed: ${metrics.questionsWithCitations}/${metrics.totalQuestions} questions with citations")
        
        return CitationTestReport(
            results = results,
            metrics = metrics
        )
    }
    
    /**
     * Тестирует один вопрос
     */
    private suspend fun testQuestion(
        question: String,
        topK: Int,
        minSimilarity: Float,
        applyFilter: Boolean,
        strategy: String
    ): CitationTestResult {
        logger.debug("Testing question: $question")
        
        val request = RAGRequest(
            question = question,
            topK = topK,
            minSimilarity = minSimilarity
        )
        
        val response = ragService.query(
            request = request,
            applyFilter = applyFilter,
            strategy = strategy
        )
        
        val citations = response.citations
        val hasCitations = citations.isNotEmpty()
        val citationsCount = citations.size
        
        // Валидируем цитаты - проверяем, что все документы были в контексте
        val availableDocuments = response.contextChunks
            .mapNotNull { it.documentPath }
            .toSet()
        
        val validCitations = citations.filter { citation ->
            availableDocuments.any { docPath ->
                normalizePath(docPath) == normalizePath(citation.documentPath)
            }
        }
        
        val validCitationsCount = validCitations.size
        
        return CitationTestResult(
            question = question,
            hasCitations = hasCitations,
            citationsCount = citationsCount,
            validCitationsCount = validCitationsCount,
            answer = response.answer,
            citations = citations
        )
    }
    
    /**
     * Вычисляет метрики на основе результатов тестирования
     */
    private fun calculateMetrics(results: List<CitationTestResult>): CitationMetrics {
        val totalQuestions = results.size
        val questionsWithCitations = results.count { it.hasCitations }
        val totalCitations = results.sumOf { it.citationsCount }
        val averageCitationsPerAnswer = if (totalQuestions > 0) {
            totalCitations.toDouble() / totalQuestions
        } else {
            0.0
        }
        
        val totalValidCitations = results.sumOf { it.validCitationsCount }
        val validCitationsPercentage = if (totalCitations > 0) {
            (totalValidCitations.toDouble() / totalCitations) * 100.0
        } else {
            0.0
        }
        
        // Считаем ответы без галлюцинаций: есть цитаты, все валидны, минимум 2 цитаты
        val answersWithoutHallucinations = results.count { result ->
            result.hasCitations && 
            result.validCitationsCount == result.citationsCount && 
            result.citationsCount >= 2
        }
        
        return CitationMetrics(
            totalQuestions = totalQuestions,
            questionsWithCitations = questionsWithCitations,
            averageCitationsPerAnswer = averageCitationsPerAnswer,
            validCitationsPercentage = validCitationsPercentage,
            answersWithoutHallucinations = answersWithoutHallucinations
        )
    }
    
    /**
     * Нормализует путь для сравнения
     */
    private fun normalizePath(path: String): String {
        return path
            .replace("\\", "/")
            .replace(Regex("/+"), "/")
            .trim('/')
    }
}

