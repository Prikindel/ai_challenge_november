package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.ReasoningAgent
import java.io.File

/**
 * Dependency Injection модуль
 * Создает и связывает все зависимости приложения
 */
object AppModule {
    
    /**
     * Получить директорию с клиентом
     */
    fun getClientDirectory(): File {
        val lessonRoot = findLessonRoot()
        return File(lessonRoot, "client")
    }
    
    /**
     * Создать OpenAIClient (если конфигурация доступна)
     */
    fun createOpenAIClient(): OpenAIClient? {
        cachedClient?.let { return it }
        val config = Config.aiConfig ?: return null
        return OpenAIClient(
            apiKey = config.apiKey,
            apiUrl = config.apiUrl,
            model = config.model,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            requestTimeoutSeconds = config.requestTimeout,
            systemPrompt = config.systemPrompt
        ).also { cachedClient = it }
    }
    
    private var cachedClient: OpenAIClient? = null
    private var cachedRepository: AIRepository? = null
    private var cachedReasoningAgent: ReasoningAgent? = null

    /**
     * Создать AIRepository (если конфигурация доступна)
     */
    fun createAIRepository(): AIRepository? {
        cachedRepository?.let { return it }
        val client = createOpenAIClient() ?: return null
        cachedClient = client
        return AIRepository(client).also { cachedRepository = it }
    }

    fun createReasoningAgent(): ReasoningAgent? {
        cachedReasoningAgent?.let { return it }
        val repository = createAIRepository() ?: return null
        return ReasoningAgent(repository).also { cachedReasoningAgent = it }
    }
    
    /**
     * Пример: Создать специализированного агента
     * BaseAgent - абстрактный класс, нельзя создать напрямую
     * Создавайте специализированных агентов, наследуя BaseAgent
     * 
     * Пример:
     * fun createMyAgent(): MyAgent? {
     *     val aiRepository = createAIRepository() ?: return null
     *     return MyAgent(aiRepository)
     * }
     * 
     * class MyAgent(aiRepository: AIRepository) : BaseAgent(aiRepository) {
     *     // ваша логика
     * }
     */
    
    /**
     * Находит корень урока (папку lesson-XX-...)
     * Ищет папку, начинающуюся с "lesson-", идя вверх по директориям
     */
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        while (dir != null) {
            // Проверяем, является ли текущая директория корнем урока
            if (dir.name.matches(Regex("lesson-\\d+.*")) && dir.isDirectory) {
                return dir.absolutePath
            }
            
            // Проверяем, есть ли папка lesson-XX-... в текущей директории
            dir.listFiles()?.firstOrNull { 
                it.isDirectory && it.name.matches(Regex("lesson-\\d+.*"))
            }?.let { 
                return it.absolutePath 
            }
            
            // Идем на уровень выше
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }
        
        // Если не нашли, возвращаем текущую директорию
        return currentDir
    }
    
    /**
     * Закрыть ресурсы (если нужно)
     */
    fun close() {
        cachedClient?.close()
        cachedClient = null
        cachedRepository = null
        cachedReasoningAgent = null
    }
}

