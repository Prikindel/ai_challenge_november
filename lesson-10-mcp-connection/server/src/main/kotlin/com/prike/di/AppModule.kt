package com.prike.di

import com.prike.Config
import com.prike.config.MCPConfigLoader
import com.prike.data.client.MCPClient
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.data.repository.MCPRepository
import com.prike.domain.agent.MCPConnectionAgent
import com.prike.presentation.controller.MCPController
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
        val clientDir = File(lessonRoot, "client")
        return clientDir
    }
    
    /**
     * Создать OpenAIClient (если конфигурация доступна)
     */
    fun createOpenAIClient(): OpenAIClient? {
        val config = Config.aiConfig ?: return null
        return OpenAIClient(
            apiKey = config.apiKey,
            apiUrl = config.apiUrl,
            model = config.model,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            requestTimeoutSeconds = config.requestTimeout,
            systemPrompt = config.systemPrompt
        )
    }
    
    /**
     * Создать AIRepository (если конфигурация доступна)
     */
    fun createAIRepository(): AIRepository? {
        val client = createOpenAIClient() ?: return null
        return AIRepository(client)
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
     * Использует путь к классу для определения правильной директории
     */
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        // Если мы в папке server/, то корень урока - это родительская директория
        if (dir.name == "server") {
            val parent = dir.parentFile
            if (parent != null && parent.name.matches(Regex("lesson-\\d+.*"))) {
                return parent.absolutePath
            }
        }
        
        // Если текущая директория - это корень урока
        if (dir.name.matches(Regex("lesson-\\d+.*")) && dir.isDirectory) {
            return dir.absolutePath
        }
        
        // Идем вверх по директориям, ищем папку lesson-XX-*
        while (dir != null) {
            if (dir.name.matches(Regex("lesson-\\d+.*")) && dir.isDirectory) {
                val clientDir = File(dir, "client")
                if (clientDir.exists()) {
                    return dir.absolutePath
                }
                return dir.absolutePath
            }
            
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }
        
        // Ищем lesson-10-mcp-connection специально (приоритет)
        val lesson10Path = File(currentDir, "lesson-10-mcp-connection")
        if (lesson10Path.exists() && lesson10Path.isDirectory) {
            val clientDir = File(lesson10Path, "client")
            if (clientDir.exists()) {
                return lesson10Path.absolutePath
            }
        }
        
        // Ищем любую папку lesson-XX-* с client/ внутри
        val currentDirFile = File(currentDir)
        currentDirFile.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name.matches(Regex("lesson-\\d+.*"))) {
                val clientDir = File(file, "client")
                if (clientDir.exists()) {
                    return file.absolutePath
                }
            }
        }
        
        return currentDir
    }
    
    /**
     * Создать MCP зависимости
     */
    fun createMCPClient(): MCPClient {
        return MCPClient()
    }
    
    fun createMCPRepository(): MCPRepository {
        val client = createMCPClient()
        return MCPRepository(client)
    }
    
    fun createMCPConnectionAgent(): MCPConnectionAgent {
        val repository = createMCPRepository()
        return MCPConnectionAgent(repository)
    }
    
    fun createMCPController(): MCPController {
        val agent = createMCPConnectionAgent()
        val configLoader = MCPConfigLoader()
        val lessonRoot = findLessonRoot()
        return MCPController(agent, configLoader, lessonRoot)
    }
    
    /**
     * Закрыть ресурсы (если нужно)
     */
    fun close() {
        // Закрываем ресурсы здесь при необходимости
        // Например, закрыть OpenAIClient
    }
}

