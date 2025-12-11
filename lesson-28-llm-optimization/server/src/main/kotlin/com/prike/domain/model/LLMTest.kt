package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Тест для проверки качества LLM
 * 
 * @param id Уникальный идентификатор теста
 * @param name Название теста
 * @param questions Список тестовых вопросов
 * @param expectedTopics Ожидаемые темы в ответе (опционально)
 */
data class LLMTest(
    val id: String,
    val name: String,
    val questions: List<String>,
    val expectedTopics: List<String>? = null
)

/**
 * Результат тестирования для одного вопроса
 * 
 * @param question Вопрос
 * @param answer Ответ от LLM
 * @param responseTime Время ответа в миллисекундах
 * @param tokenCount Количество использованных токенов (опционально)
 */
@Serializable
data class QuestionResult(
    val question: String,
    val answer: String,
    val responseTime: Long,
    val tokenCount: Int? = null
)

/**
 * Результат тестирования конфигурации
 * 
 * @param testId Идентификатор теста
 * @param configuration Описание конфигурации (параметры)
 * @param parameters Параметры LLM, использованные в тесте
 * @param templateId Идентификатор использованного шаблона
 * @param results Результаты для каждого вопроса
 * @param totalTime Общее время теста в миллисекундах
 * @param averageTime Среднее время ответа в миллисекундах
 */
@Serializable
data class TestResult(
    val testId: String,
    val configuration: String,
    val parameters: LLMParameters,
    val templateId: String,
    val results: List<QuestionResult>,
    val totalTime: Long,
    val averageTime: Long
)

