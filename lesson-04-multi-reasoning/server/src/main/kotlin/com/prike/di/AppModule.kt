package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.ReasoningAgent
import com.prike.domain.agent.reasoning.ComparisonReasoningAgent
import com.prike.domain.agent.reasoning.DirectReasoningAgent
import com.prike.domain.agent.reasoning.ExpertPanelReasoningAgent
import com.prike.domain.agent.reasoning.PromptFromOtherAIAgent
import com.prike.domain.agent.reasoning.StepByStepReasoningAgent
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

        val direct = DirectReasoningAgent(repository)
        val step = StepByStepReasoningAgent(repository)
        val prompt = PromptFromOtherAIAgent(repository)
        val experts = ExpertPanelReasoningAgent(repository)
        val comparison = ComparisonReasoningAgent(repository)

        return ReasoningAgent(
            defaultTask = ReasoningAgent.DEFAULT_TASK,
            directAgent = direct,
            stepAgent = step,
            promptAgent = prompt,
            expertAgent = experts,
            comparisonAgent = comparison
        ).also { cachedReasoningAgent = it }
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
        resolveFromClassLocation()?.let { return it }
        resolveFromWorkingDirectory()?.let { return it }
        return System.getProperty("user.dir")
    }

    private fun resolveFromClassLocation(): String? {
        val regex = Regex("lesson-\\d+.*")
        val url = AppModule::class.java.protectionDomain.codeSource.location ?: return null
        var dir = File(url.toURI())
        if (!dir.isDirectory) {
            dir = dir.parentFile
        }
        while (dir != null) {
            if (dir.isDirectory && dir.name.matches(regex)) {
                return dir.absolutePath
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun resolveFromWorkingDirectory(): String? {
        val regex = Regex("lesson-\\d+.*")
        var dir = File(System.getProperty("user.dir"))
        while (dir != null) {
            if (dir.isDirectory && dir.name.matches(regex)) {
                return dir.absolutePath
            }
            val lessonDir = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.matches(regex) }
            if (lessonDir != null) {
                return lessonDir.absolutePath
            }
            val parent = dir.parentFile ?: break
            if (parent == dir) break
            dir = parent
        }
        return null
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

