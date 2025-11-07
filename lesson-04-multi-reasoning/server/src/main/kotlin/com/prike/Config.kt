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
        // .env файл находится в корне проекта (ai_challenge_november), а не в уроке
        // Ищем корень проекта, идя вверх от текущей директории
        val projectRoot = findProjectRoot()
        
        try {
            dotenv {
                directory = projectRoot
                filename = ".env"
                ignoreIfMissing = true
            }
        } catch (e: Exception) {
            dotenv {
                ignoreIfMissing = true
            }
        }
    }
    
    /**
     * Находит корень проекта (ai_challenge_november)
     * Ищет директорию, содержащую папки lesson-XX-*
     */
    private fun findProjectRoot(): String {
        var dir = File(System.getProperty("user.dir"))
        
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
    
    /**
     * Находит корень урока (папку lesson-XX-*)
     * Сначала пытается определить по расположению текущего класса,
     * затем по рабочей директории.
     */
    private fun findLessonRoot(currentDir: String): String {
        resolveLessonFromClassLocation()?.let { return it }
        resolveLessonFromWorkingDirectory(currentDir)?.let { return it }
        return currentDir
    }

    private val lessonRegex = Regex("lesson-\\d+.*")

    private fun resolveLessonFromClassLocation(): String? {
        val url = Config::class.java.protectionDomain.codeSource.location ?: return null
        var dir = File(url.toURI())
        if (!dir.isDirectory) {
            dir = dir.parentFile
        }
        while (dir != null) {
            if (dir.isDirectory && dir.name.matches(lessonRegex)) {
                return dir.absolutePath
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun resolveLessonFromWorkingDirectory(startDir: String): String? {
        var dir = File(startDir)
        while (dir != null) {
            if (dir.isDirectory && dir.name.matches(lessonRegex)) {
                return dir.absolutePath
            }
            val lessonDir = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.matches(lessonRegex) }
            if (lessonDir != null) {
                return lessonDir.absolutePath
            }
            val parent = dir.parentFile ?: break
            if (parent == dir) break
            dir = parent
        }
        return null
    }

    // Сервер
    val serverHost: String = dotenv["SERVER_HOST"] ?: System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val serverPort: Int = (dotenv["SERVER_PORT"] ?: System.getenv("SERVER_PORT") ?: "8080").toInt()

    // AI Конфигурация из YAML (опционально)
    val aiConfig: AIConfig? = loadAIConfig()

    /**
     * Загружает конфигурацию AI из YAML файла
     * API ключ приоритетно берется из .env (для безопасности)
     * Возвращает null, если API ключ не найден (для шаблона это нормально)
     */
    private fun loadAIConfig(): AIConfig? {
        val apiKey = dotenv["OPENAI_API_KEY"] ?: System.getenv("OPENAI_API_KEY") ?: return null
        
        val lessonRoot = findLessonRoot(System.getProperty("user.dir"))
        val configDir = File(lessonRoot, "config")
        val yamlFile = File(configDir, "ai.yaml")
        
        val yaml = Yaml()
        val configMap: Map<String, Any>? = runCatching {
            if (yamlFile.exists()) {
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

