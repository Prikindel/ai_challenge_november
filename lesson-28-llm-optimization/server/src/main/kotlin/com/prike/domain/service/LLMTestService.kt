package com.prike.domain.service

import com.prike.domain.model.LLMParameters
import com.prike.domain.model.LLMTest
import com.prike.domain.model.QuestionResult
import com.prike.domain.model.TestResult
import org.slf4j.LoggerFactory

/**
 * Сервис для тестирования LLM с разными конфигурациями
 */
class LLMTestService(
    private val llmService: LLMService
) {
    private val logger = LoggerFactory.getLogger(LLMTestService::class.java)
    
    /**
     * Запускает тест с указанной конфигурацией
     * 
     * @param test Тест для запуска
     * @param parameters Параметры LLM
     * @param templateId Идентификатор шаблона промпта
     * @return Результаты тестирования
     */
    suspend fun runTest(
        test: LLMTest,
        parameters: LLMParameters,
        templateId: String
    ): TestResult {
        logger.info("Running test '${test.name}' with template '$templateId' and parameters: temp=${parameters.temperature}, maxTokens=${parameters.maxTokens}")
        
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<QuestionResult>()
        
        // Запускаем тест для каждого вопроса
        for (question in test.questions) {
            val questionStartTime = System.currentTimeMillis()
            
            try {
                // Используем generateResponse с указанными параметрами и шаблоном
                val response = llmService.generateResponse(
                    userMessage = question,
                    templateId = templateId,
                    parameters = parameters
                )
                
                val questionResponseTime = System.currentTimeMillis() - questionStartTime
                
                results.add(QuestionResult(
                    question = question,
                    answer = response.answer,
                    responseTime = questionResponseTime,
                    tokenCount = response.tokensUsed
                ))
                
                logger.debug("Question answered in ${questionResponseTime}ms (tokens: ${response.tokensUsed})")
            } catch (e: Exception) {
                logger.error("Failed to answer question: $question", e)
                val questionResponseTime = System.currentTimeMillis() - questionStartTime
                
                results.add(QuestionResult(
                    question = question,
                    answer = "Ошибка: ${e.message}",
                    responseTime = questionResponseTime,
                    tokenCount = null
                ))
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        val averageTime = results.map { it.responseTime }.average().toLong()
        
        // Формируем описание конфигурации
        val configuration = buildString {
            append("temp=${parameters.temperature}")
            append(", maxTokens=${parameters.maxTokens}")
            append(", topP=${parameters.topP}")
            append(", topK=${parameters.topK}")
            append(", repeatPenalty=${parameters.repeatPenalty}")
            append(", contextWindow=${parameters.contextWindow}")
            if (parameters.seed != null) {
                append(", seed=${parameters.seed}")
            }
            append(", template=$templateId")
        }
        
        logger.info("Test '${test.name}' completed in ${totalTime}ms (avg: ${averageTime}ms per question)")
        
        return TestResult(
            testId = test.id,
            configuration = configuration,
            parameters = parameters,
            templateId = templateId,
            results = results,
            totalTime = totalTime,
            averageTime = averageTime
        )
    }
}

