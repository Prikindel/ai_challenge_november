package com.prike

import com.prike.config.AIConfig
import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация приложения
 * Загружает переменные окружения из .env файла и настройки AI из YAML
 */
object Config {
    private val dotenv = run {
        val currentDir = System.getProperty("user.dir")
        
        // Определяем корень урока lesson-01-simple-chat-agent
        // Путь относительно Config.kt: server/src/main/kotlin/com/prike/Config.kt
        // Нужно найти: lesson-01-simple-chat-agent/.env
        val lessonRoot = findLessonRoot(currentDir)
        
        try {
            dotenv {
                directory = lessonRoot
                filename = ".env"
                ignoreIfMissing = true
            }
        } catch (e: Exception) {
            // Если не удалось загрузить из файла, используем системные переменные
            dotenv {
                ignoreIfMissing = true
            }
        }
    }
    
    /**
     * Находит корень урока (папку lesson-01-simple-chat-agent)
     */
    private fun findLessonRoot(currentDir: String): String {
        var dir = File(currentDir)
        
        // Идем вверх по директориям, пока не найдем папку lesson-01-simple-chat-agent
        while (dir != null) {
            // Проверяем, есть ли в этой директории папка lesson-01-simple-chat-agent
            val lessonDir = File(dir, "lesson-01-simple-chat-agent")
            if (lessonDir.exists() && lessonDir.isDirectory) {
                return lessonDir.absolutePath
            }
            
            // Или проверяем, является ли сама директория корнем урока
            if (dir.name == "lesson-01-simple-chat-agent") {
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
        // (fallback на системные переменные окружения)
        return currentDir
    }

    // Сервер
    val serverHost: String = dotenv["SERVER_HOST"] ?: System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val serverPort: Int = (dotenv["SERVER_PORT"] ?: System.getenv("SERVER_PORT") ?: "8080").toInt()

    // AI Конфигурация из YAML
    val aiConfig: AIConfig = loadAIConfig()

    /**
     * Загружает конфигурацию AI из YAML файла
     * API ключ приоритетно берется из .env (для безопасности)
     */
    private fun loadAIConfig(): AIConfig {
        val lessonRoot = findLessonRoot(System.getProperty("user.dir"))
        val configDir = File(lessonRoot, "config")
        val yamlFile = File(configDir, "ai.yaml")
        
        val yaml = Yaml()
        val configMap: Map<String, Any>? = runCatching {
            if (yamlFile.exists()) {
                // Загружаем YAML как Object, затем приводим к Map
                yamlFile.inputStream().use { stream ->
                    val loaded = yaml.load<Any>(stream)
                    @Suppress("UNCHECKED_CAST")
                    when (loaded) {
                        is Map<*, *> -> loaded as? Map<String, Any>
                        else -> null
                    }
                }
            } else null
        }.getOrNull()
        
        // Извлекаем секцию "ai"
        @Suppress("UNCHECKED_CAST")
        val aiSection: Map<String, Any> = runCatching {
            val aiValue = configMap?.get("ai")
            when (aiValue) {
                is Map<*, *> -> aiValue as? Map<String, Any> ?: emptyMap()
                else -> emptyMap()
            }
        }.getOrElse { emptyMap() }
        
        // API ключ приоритетно из .env (для безопасности)
        val apiKey = dotenv["OPENAI_API_KEY"]
            ?: System.getenv("OPENAI_API_KEY")
            ?: throw IllegalStateException(
                "OPENAI_API_KEY не найден. " +
                "Установите его в .env файле"
            )
        
        return AIConfig(
            apiKey = apiKey,
            apiUrl = (aiSection["apiUrl"] as? String)
                ?: "https://api.openai.com/v1/chat/completions",
            model = (aiSection["model"] as? String)
                ?: "gpt-3.5-turbo",
            temperature = ((aiSection["temperature"] as? Number)?.toDouble())
                ?: 0.7,
            maxTokens = ((aiSection["maxTokens"] as? Number)?.toInt())
                ?: 500,
            requestTimeout = ((aiSection["requestTimeout"] as? Number)?.toInt())
                ?: 60,
            systemPrompt = aiSection["systemPrompt"] as? String
        )
    }
}
