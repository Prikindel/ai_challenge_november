package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepositoryImpl
import com.prike.domain.usecase.ChatUseCase
import com.prike.presentation.controller.ServerController
import java.io.File

/**
 * Dependency Injection модуль
 * Создает и связывает все зависимости приложения
 */
object AppModule {
    
    // Храним клиент для закрытия ресурсов
    private var aiClient: OpenAIClient? = null
    
    /**
     * Создать контроллер чата со всеми зависимостями
     */
    fun createChatController(): ServerController {
        val aiConfig = Config.aiConfig
        val client = OpenAIClient(
            apiKey = aiConfig.apiKey,
            apiUrl = aiConfig.apiUrl,
            model = aiConfig.model,
            temperature = aiConfig.temperature,
            maxTokens = aiConfig.maxTokens,
            requestTimeoutSeconds = aiConfig.requestTimeout,
            systemPrompt = aiConfig.systemPrompt
        ).also { aiClient = it }
        
        val repository = AIRepositoryImpl(client)
        val chatUseCase = ChatUseCase(repository)
        return ServerController(chatUseCase)
    }
    
    /**
     * Получить директорию с клиентом
     */
    fun getClientDirectory(): File {
        val lessonRoot = findLessonRoot()
        return File(lessonRoot, "client")
    }
    
    /**
     * Находит корень урока (папку lesson-01-simple-chat-agent)
     */
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        while (dir != null) {
            val lessonDir = File(dir, "lesson-01-simple-chat-agent")
            if (lessonDir.exists() && lessonDir.isDirectory) {
                return lessonDir.absolutePath
            }
            
            if (dir.name == "lesson-01-simple-chat-agent") {
                return dir.absolutePath
            }
            
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }
        
        return currentDir
    }
    
    /**
     * Закрыть ресурсы (HTTP клиент)
     */
    fun close() {
        aiClient?.close()
        aiClient = null
    }
}
