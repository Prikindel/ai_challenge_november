package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.ConversationAgent
import com.prike.domain.agent.MemoryOrchestrator
import com.prike.domain.service.MemoryService
import com.prike.presentation.controller.MemoryController
import java.io.File

/**
 * Dependency Injection модуль
 * Создает и связывает все зависимости приложения
 * 
 * TODO: На этапе 2 будет добавлена фабрика для создания репозитория памяти
 * по конфигурации (SQLite или JSON)
 */
object AppModule {
    private var openAIClient: OpenAIClient? = null
    private var aiRepository: AIRepository? = null
    private var memoryOrchestrator: MemoryOrchestrator? = null
    private var conversationAgent: ConversationAgent? = null
    private var memoryService: MemoryService? = null
    
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
     * Создать MemoryService
     * TODO: На этапе 2 будет добавлена фабрика для создания репозитория памяти
     */
    private fun createMemoryService(): MemoryService? {
        if (memoryService != null) return memoryService
        
        // TODO: Создать репозиторий памяти по конфигурации
        // val memoryRepository = MemoryRepositoryFactory.create(Config.memoryConfig)
        // val service = MemoryService(memoryRepository)
        // memoryService = service
        // return service
        
        // Временная заглушка - будет реализовано на этапе 2
        return null
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
     * Создать MemoryController
     */
    fun createMemoryController(): MemoryController? {
        val orchestrator = createMemoryOrchestrator() ?: return null
        return MemoryController(orchestrator)
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
        memoryService = null
    }
}

