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
     * Ищет папку lesson-XX-* вверх по дереву директорий от текущей директории
     */
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        // Идем вверх по директориям, пока не найдем папку lesson-XX-*
        while (dir != null) {
            // Проверяем, является ли сама директория корнем урока (lesson-XX-*)
            if (dir.name.matches(Regex("lesson-\\d+.*"))) {
                return dir.absolutePath
            }
            
            // Идем на уровень выше
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                // Дошли до корня файловой системы
                break
            }
            dir = parent
        }
        
        // Если не нашли, возвращаем текущую директорию
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
