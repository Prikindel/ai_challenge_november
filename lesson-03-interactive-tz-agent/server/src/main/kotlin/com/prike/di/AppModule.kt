package com.prike.di

import com.prike.Config
import com.prike.config.TZPromptBuilder
import com.prike.data.client.OpenAIClient
import com.prike.data.mapper.TZMapper
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.TZAgent
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
     * Создать агента для сбора ТЗ
     */
    fun createTZAgent(): TZAgent? {
        val aiRepository = createAIRepository() ?: return null
        val tzMapper = TZMapper()
        val systemPrompt = TZPromptBuilder.build()
        return TZAgent(aiRepository, tzMapper, systemPrompt)
    }
    
    /**
     * Находит корень урока (папку lesson-XX-...)
     * Использует путь к классам для определения текущего урока
     */
    private fun findLessonRoot(): String {
        // Получаем путь к классам (например, .../lesson-03-interactive-tz-agent/server/build/classes/...)
        val classPath = this::class.java.protectionDomain.codeSource?.location?.path
            ?: System.getProperty("user.dir")
        
        var dir = File(classPath)
        
        // Идем вверх по директориям, пока не найдем папку lesson-XX-*
        while (dir != null) {
            // Проверяем, является ли текущая директория корнем урока
            if (dir.name.matches(Regex("lesson-\\d+.*")) && dir.isDirectory) {
                return dir.absolutePath
            }
            
            // Идем на уровень выше
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }
        
        // Если не нашли по пути к классам, используем текущую рабочую директорию
        val currentDir = System.getProperty("user.dir")
        var currentDirFile = File(currentDir)
        
        // Проверяем, является ли текущая директория корнем урока
        if (currentDirFile.name.matches(Regex("lesson-\\d+.*")) && currentDirFile.isDirectory) {
            return currentDirFile.absolutePath
        }
        
        // Идем вверх от текущей директории
        while (currentDirFile != null) {
            if (currentDirFile.name.matches(Regex("lesson-\\d+.*")) && currentDirFile.isDirectory) {
                return currentDirFile.absolutePath
            }
            val parent = currentDirFile.parentFile
            if (parent == null || parent == currentDirFile) {
                break
            }
            currentDirFile = parent
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

