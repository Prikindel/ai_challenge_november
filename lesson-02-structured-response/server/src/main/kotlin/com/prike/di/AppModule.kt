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
            systemPrompt = aiConfig.systemPrompt,
            useJsonFormat = aiConfig.useJsonFormat
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
     * Находит корень урока (lesson-XX-*)
     */
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        while (dir != null) {
            // Проверяем, является ли сама директория корнем урока
            if (dir.name.matches(Regex("lesson-\\d+.*"))) {
                return dir.absolutePath
            }
            
            // Ищем папку lesson-XX-* в текущей директории
            val lessonDirs = dir.listFiles { file ->
                file.isDirectory && file.name.matches(Regex("lesson-\\d+.*"))
            }
            if (lessonDirs != null && lessonDirs.isNotEmpty()) {
                // Если мы находимся в корне проекта, ищем lesson-02-structured-response
                val lesson02 = lessonDirs.find { it.name.contains("lesson-02") }
                if (lesson02 != null) {
                    return lesson02.absolutePath
                }
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
