package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepositoryImpl
import com.prike.domain.agent.TemperatureAgent
import com.prike.presentation.controller.TemperatureController
import java.io.File

/**
 * Dependency Injection модуль
 * Создает и связывает все зависимости приложения
 */
object AppModule {
    // Храним клиент для закрытия ресурсов
    private var aiClient: OpenAIClient? = null
    
    /**
     * Создать контроллер экспериментов с температурой со всеми зависимостями
     */
    fun createTemperatureController(): TemperatureController {
        val aiConfig = Config.aiConfig
        val lessonConfig = Config.lessonConfig
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
        val agent = TemperatureAgent(
            aiRepository = repository,
            defaultQuestion = lessonConfig.defaultQuestion,
            defaultTemperatures = lessonConfig.defaultTemperatures,
            comparisonTemperature = lessonConfig.comparisonTemperature
        )
        return TemperatureController(agent)
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
     * Если не находит вверх, ищет в поддиректориях текущей директории
     */
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        // Идем вверх по директориям, пока не найдем папку lesson-XX-*
        while (true) {
            // Проверяем, является ли сама директория корнем урока (lesson-XX-*)
            if (dir.name.matches(Regex("lesson-\\d+.*"))) {
                return dir.absolutePath
            }
            
            // Проверяем, есть ли в текущей директории поддиректория lesson-XX-*
            try {
                val lessonDirs = dir.listFiles()?.filter { file ->
                    file.isDirectory && file.name.matches(Regex("lesson-\\d+.*"))
                }
                if (lessonDirs != null && lessonDirs.isNotEmpty()) {
                    // Если запускаем из корня проекта, ищем lesson-02-structured-response
                    val lesson02 = lessonDirs.firstOrNull { it.name.contains("lesson-02") }
                        ?: lessonDirs.firstOrNull()
                    if (lesson02 != null) {
                        return lesson02.absolutePath
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибки доступа к файловой системе
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
