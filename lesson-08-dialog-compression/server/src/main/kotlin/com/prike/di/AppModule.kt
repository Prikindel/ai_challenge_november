package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.DialogOrchestrator
import com.prike.domain.agent.DialogConversationAgent
import com.prike.domain.agent.DialogSummarizationAgent
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
    private var dialogOrchestrator: DialogOrchestrator? = null
    private var dialogConversationAgent: DialogConversationAgent? = null
    private var dialogSummarizationAgent: DialogSummarizationAgent? = null
    
    /**
     * Получить директорию с клиентом
     */
    fun getClientDirectory(): File = File(Config.lessonRootPath, "client")
    
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

    fun createDialogOrchestrator(): DialogOrchestrator? {
        if (dialogOrchestrator != null) return dialogOrchestrator
        val conversationAgent = createDialogConversationAgent() ?: return null
        val summarizationAgent = createDialogSummarizationAgent() ?: return null
        val orchestrator = DialogOrchestrator(
            conversationAgent = conversationAgent,
            summarizationAgent = summarizationAgent,
            lessonConfig = Config.dialogCompressionConfig,
            tokenEstimator = TokenEstimator(),
            baseModel = Config.aiConfig.model
        )
        dialogOrchestrator = orchestrator
        return orchestrator
    }

    private fun createDialogConversationAgent(): DialogConversationAgent? {
        if (dialogConversationAgent != null) return dialogConversationAgent
        val repository = createAIRepository() ?: return null
        val agent = DialogConversationAgent(repository)
        dialogConversationAgent = agent
        return agent
    }

    private fun createDialogSummarizationAgent(): DialogSummarizationAgent? {
        if (dialogSummarizationAgent != null) return dialogSummarizationAgent
        val repository = createAIRepository() ?: return null
        val agent = DialogSummarizationAgent(repository, SummaryParser(), Config.dialogCompressionConfig)
        dialogSummarizationAgent = agent
        return agent
    }

    fun createDialogCompressionController(): DialogCompressionController? {
        val orchestrator = createDialogOrchestrator() ?: return null
        return DialogCompressionController(orchestrator, Config.dialogCompressionConfig)
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
     * Закрыть ресурсы (если нужно)
     */
    fun close() {
        openAIClient?.close()
        openAIClient = null
        aiRepository = null
        dialogOrchestrator = null
        dialogConversationAgent = null
        dialogSummarizationAgent = null
    }
}

