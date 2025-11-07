package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
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
        // Закрываем ресурсы здесь при необходимости
        // Например, закрыть OpenAIClient
    }
}

