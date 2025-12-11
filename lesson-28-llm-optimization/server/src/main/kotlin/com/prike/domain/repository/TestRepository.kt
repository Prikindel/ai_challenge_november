package com.prike.domain.repository

import com.prike.domain.model.LLMTest

/**
 * Репозиторий для работы с тестами
 */
interface TestRepository {
    fun getTest(id: String): LLMTest?
    fun getAllTests(): List<LLMTest>
}

/**
 * In-memory реализация репозитория тестов
 */
class InMemoryTestRepository : TestRepository {
    private val tests = mutableMapOf<String, LLMTest>()
    
    init {
        // Предустановленные тесты
        addTest(LLMTest(
            id = "code_questions",
            name = "Вопросы о программировании",
            questions = listOf(
                "Что такое рефакторинг?",
                "Объясни разницу между async и await",
                "Как работает garbage collection в Java?",
                "Что такое замыкание (closure) в программировании?",
                "Объясни принцип SOLID"
            ),
            expectedTopics = listOf("программирование", "код", "разработка")
        ))
        
        addTest(LLMTest(
            id = "general_qa",
            name = "Общие вопросы",
            questions = listOf(
                "Что такое искусственный интеллект?",
                "Объясни принцип работы нейронных сетей",
                "Какие есть типы машинного обучения?",
                "Что такое градиентный спуск?",
                "Чем отличается supervised learning от unsupervised learning?"
            ),
            expectedTopics = listOf("ИИ", "машинное обучение", "нейронные сети")
        ))
        
        addTest(LLMTest(
            id = "short_answers",
            name = "Короткие ответы",
            questions = listOf(
                "Что такое API?",
                "Что такое REST?",
                "Что такое JSON?",
                "Что такое Git?",
                "Что такое Docker?"
            ),
            expectedTopics = listOf("технологии", "разработка")
        ))
    }
    
    private fun addTest(test: LLMTest) {
        tests[test.id] = test
    }
    
    override fun getTest(id: String): LLMTest? {
        return tests[id]
    }
    
    override fun getAllTests(): List<LLMTest> {
        return tests.values.toList()
    }
}

