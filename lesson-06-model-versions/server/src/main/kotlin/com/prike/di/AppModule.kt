package com.prike.di

import com.prike.Config
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepositoryImpl
import com.prike.domain.agent.ModelComparisonAgent
import com.prike.presentation.controller.ModelComparisonController
import java.io.File

/**
 * Простейший DI контейнер текущего урока.
 * Отвечает за создание зависимостей и их время жизни.
 */
object AppModule {
    private var aiClient: OpenAIClient? = null

    fun createModelComparisonController(): ModelComparisonController {
        val aiConfig = Config.aiConfig
        val lessonConfig = Config.lessonConfig

        val client = OpenAIClient(
            apiKey = aiConfig.apiKey,
            defaultApiUrl = aiConfig.apiUrl,
            defaultModel = aiConfig.model,
            defaultTemperature = aiConfig.temperature,
            defaultMaxTokens = aiConfig.maxTokens,
            requestTimeoutSeconds = aiConfig.requestTimeout,
            defaultSystemPrompt = aiConfig.systemPrompt,
            useJsonFormat = aiConfig.useJsonFormat
        ).also { aiClient = it }

        val repository = AIRepositoryImpl(client)
        val agent = ModelComparisonAgent(
            aiRepository = repository,
            lessonConfig = lessonConfig
        )

        return ModelComparisonController(
            agent = agent,
            lessonConfig = lessonConfig
        )
    }

    fun getClientDirectory(): File {
        val lessonRoot = findLessonRoot()
        return File(lessonRoot, "client")
    }

    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)

        while (true) {
            if (dir.name == CURRENT_LESSON_DIR) {
                return dir.absolutePath
            }
            if (dir.name.matches(Regex("lesson-\\d+.*"))) {
                return dir.absolutePath
            }

            try {
                val lessonDirs = dir.listFiles()
                    ?.filter { file -> file.isDirectory && file.name.matches(Regex("lesson-\\d+.*")) }
                if (!lessonDirs.isNullOrEmpty()) {
                    val target = lessonDirs.firstOrNull { it.name == CURRENT_LESSON_DIR }
                        ?: lessonDirs.firstOrNull()
                    if (target != null) {
                        return target.absolutePath
                    }
                }
            } catch (_: Exception) {
                // пропускаем ошибки доступа
            }

            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }

        return currentDir
    }

    fun close() {
        aiClient?.close()
        aiClient = null
    }

    private const val CURRENT_LESSON_DIR = "lesson-06-model-versions"
}
