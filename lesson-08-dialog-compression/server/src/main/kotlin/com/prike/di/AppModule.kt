package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.DialogCompressionAgent
import com.prike.domain.service.SummaryParser
import com.prike.domain.service.TokenEstimator
import com.prike.presentation.controller.DialogCompressionController
import java.io.File

/**
 * Dependency Injection модуль
 * Создает и связывает все зависимости приложения
 */
object AppModule {
    private var openAIClient: OpenAIClient? = null
    private var aiRepository: AIRepository? = null
    private var dialogCompressionAgent: DialogCompressionAgent? = null

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
        if (openAIClient != null) return openAIClient
        val config = Config.aiConfig
        val client = OpenAIClient(
            apiKey = config.apiKey,
            apiUrl = config.apiUrl,
            model = config.model,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            requestTimeoutSeconds = config.requestTimeout,
            systemPrompt = config.systemPrompt,
            defaultUseJsonResponse = config.useJsonFormat
        )
        openAIClient = client
        return client
    }
    
    /**
     * Создать AIRepository (если конфигурация доступна)
     */
    fun createAIRepository(): AIRepository? {
        if (aiRepository != null) return aiRepository
        val client = createOpenAIClient() ?: return null
        val repository = AIRepository(client)
        aiRepository = repository
        return repository
    }

    fun createDialogCompressionAgent(): DialogCompressionAgent? {
        if (dialogCompressionAgent != null) return dialogCompressionAgent
        val repository = createAIRepository() ?: return null
        val agent = DialogCompressionAgent(
            aiRepository = repository,
            lessonConfig = Config.dialogCompressionConfig,
            tokenEstimator = TokenEstimator(),
            summaryParser = SummaryParser(),
            baseModel = Config.aiConfig.model
        )
        dialogCompressionAgent = agent
        return agent
    }

    fun createDialogCompressionController(): DialogCompressionController? {
        val agent = createDialogCompressionAgent() ?: return null
        return DialogCompressionController(agent, Config.dialogCompressionConfig)
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
        openAIClient?.close()
        openAIClient = null
        aiRepository = null
        dialogCompressionAgent = null
    }
}

