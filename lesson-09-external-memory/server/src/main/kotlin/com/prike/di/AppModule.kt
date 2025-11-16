package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.data.repository.MemoryRepositoryFactory
import com.prike.domain.agent.ConversationAgent
import com.prike.domain.agent.SummarizationAgent
import com.prike.domain.agent.MemoryOrchestrator
import com.prike.domain.orchestrator.SummarizationCoordinator
import com.prike.domain.repository.MemoryRepository
import com.prike.domain.service.MemoryService
import com.prike.presentation.controller.MemoryController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Dependency Injection модуль
 * Создает и связывает все зависимости приложения
 */
object AppModule {
    private var openAIClient: OpenAIClient? = null
    private var aiRepository: AIRepository? = null
    private var memoryOrchestrator: MemoryOrchestrator? = null
    private var conversationAgent: ConversationAgent? = null
    private var summarizationAgent: SummarizationAgent? = null
    private var summarizationCoordinator: SummarizationCoordinator? = null
    private var memoryService: MemoryService? = null
    private var memoryRepository: MemoryRepository? = null
    private val backgroundScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    
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
    
    /**
     * Создать ConversationAgent
     */
    private fun createConversationAgent(): ConversationAgent? {
        if (conversationAgent != null) return conversationAgent
        val repository = createAIRepository() ?: return null
        val agent = ConversationAgent(repository)
        conversationAgent = agent
        return agent
    }

    /**
     * Создать SummarizationAgent
     */
    private fun createSummarizationAgent(): SummarizationAgent? {
        if (summarizationAgent != null) return summarizationAgent
        val repository = createAIRepository() ?: return null
        val agent = SummarizationAgent(repository, Config.memoryConfig.summarization)
        summarizationAgent = agent
        return agent
    }
    
    /**
     * Создать MemoryRepository на основе конфигурации
     */
    private fun createMemoryRepository(): MemoryRepository? {
        if (memoryRepository != null) return memoryRepository
        
        return try {
            val repository = MemoryRepositoryFactory.create(
                config = Config.memoryConfig,
                lessonRoot = Config.lessonRootPath
            )
            memoryRepository = repository
            repository
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем работу приложения
            // Репозиторий может быть null, если конфигурация неверна
            null
        }
    }
    
    /**
     * Создать MemoryService
     */
    private fun createMemoryService(): MemoryService? {
        if (memoryService != null) return memoryService
        
        val repository = createMemoryRepository() ?: return null
        val service = MemoryService(
            repository = repository,
            summarizationConfig = Config.memoryConfig.summarization
        )
        memoryService = service
        return service
    }
    
    /**
     * Создать MemoryOrchestrator
     */
    fun createMemoryOrchestrator(): MemoryOrchestrator? {
        if (memoryOrchestrator != null) return memoryOrchestrator
        val conversationAgent = createConversationAgent() ?: return null
        val memoryService = createMemoryService() ?: return null
        
        val orchestrator = MemoryOrchestrator(
            conversationAgent = conversationAgent,
            memoryService = memoryService
        )
        memoryOrchestrator = orchestrator
        return orchestrator
    }

    /**
     * Создать SummarizationCoordinator
     */
    private fun createSummarizationCoordinator(): SummarizationCoordinator? {
        if (summarizationCoordinator != null) return summarizationCoordinator
        val memoryService = createMemoryService() ?: return null
        val summarizationAgent = createSummarizationAgent() ?: return null
        val coordinator = SummarizationCoordinator(
            memoryService = memoryService,
            summarizationAgent = summarizationAgent,
            config = Config.memoryConfig.summarization,
            backgroundScope = backgroundScope
        )
        summarizationCoordinator = coordinator
        return coordinator
    }
    
    /**
     * Создать MemoryController
     */
    fun createMemoryController(): MemoryController? {
        val orchestrator = createMemoryOrchestrator() ?: return null
        val summarizationCoordinator = createSummarizationCoordinator()
        return MemoryController(orchestrator, summarizationCoordinator)
    }
    
    /**
     * Закрыть ресурсы (если нужно)
     */
    fun close() {
        openAIClient?.close()
        openAIClient = null
        aiRepository = null
        memoryOrchestrator = null
        conversationAgent = null
        summarizationAgent = null
        summarizationCoordinator = null
        memoryService = null
        memoryRepository = null
    }
}

