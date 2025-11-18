package com.prike.config

import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Загрузчик конфигурации из YAML файла
 */
object ConfigLoader {
    private val dotenv = run {
        // .env файл находится в корне проекта (ai_challenge_november), а не в уроке
        val projectRoot = findProjectRoot()
        try {
            dotenv {
                directory = projectRoot
                filename = ".env"
                ignoreIfMissing = true
            }
        } catch (e: Exception) {
            dotenv { ignoreIfMissing = true }
        }
    }
    
    /**
     * Загрузить конфигурацию AI из ai.yaml
     * @param lessonRoot корневая директория урока (lesson-11-first-mcp-tool)
     */
    fun loadAIConfig(lessonRoot: String): AIConfig {
        val configFile = File(lessonRoot, "config/ai.yaml")
        if (!configFile.exists()) {
            throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val configMap: Map<String, Any>? = runCatching {
            configFile.inputStream().use { stream ->
                val loaded = yaml.load<Any>(stream)
                @Suppress("UNCHECKED_CAST")
                when (loaded) {
                    is Map<*, *> -> loaded as? Map<String, Any>
                    else -> null
                }
            }
        }.getOrNull()
        
        @Suppress("UNCHECKED_CAST")
        val aiSection: Map<String, Any> = runCatching {
            val aiValue = configMap?.get("ai")
            when (aiValue) {
                is Map<*, *> -> aiValue as? Map<String, Any> ?: emptyMap()
                else -> emptyMap()
            }
        }.getOrElse { emptyMap() }
        
        // Получаем API ключ из переменных окружения
        // Поддерживаем оба варианта: OPENROUTER_API_KEY и OPENAI_API_KEY
        val apiKey = dotenv["OPENROUTER_API_KEY"]
            ?: System.getenv("OPENROUTER_API_KEY")
            ?: dotenv["OPENAI_API_KEY"]
            ?: System.getenv("OPENAI_API_KEY")
            ?: throw IllegalStateException(
                "OPENROUTER_API_KEY or OPENAI_API_KEY not found. " +
                "Set it in .env file in the project root or as a system environment variable."
            )
        
        return AIConfig(
            apiKey = apiKey,
            apiUrl = (aiSection["apiUrl"] as? String)
                ?: "https://openrouter.ai/api/v1/chat/completions",
            model = (aiSection["model"] as? String)
                ?: "meta-llama/llama-3.1-8b-instruct",
            temperature = ((aiSection["temperature"] as? Number)?.toDouble())
                ?: 0.7,
            maxTokens = ((aiSection["maxTokens"] as? Number)?.toInt())
                ?: 500,
            requestTimeout = ((aiSection["requestTimeout"] as? Number)?.toInt())
                ?: 60,
            systemPrompt = aiSection["systemPrompt"] as? String
        )
    }
    
    /**
     * Находит корень проекта (ai_challenge_november)
     * Ищет директорию, содержащую папки lesson-XX-*
     */
    private fun findProjectRoot(): String {
        var dir: File? = File(System.getProperty("user.dir"))
        
        while (dir != null) {
            // Проверяем, есть ли в текущей директории папки lesson-XX-*
            val hasLessonDirs = dir.listFiles()?.any { 
                it.isDirectory && it.name.matches(Regex("lesson-\\d+.*"))
            } ?: false
            
            if (hasLessonDirs) {
                return dir.absolutePath
            }
            
            // Идем на уровень выше
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }
        
        // Если не нашли, возвращаем текущую директорию
        return System.getProperty("user.dir")
    }
}

