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
    private val dotenv: io.github.cdimascio.dotenv.Dotenv = run {
        val currentDir = System.getProperty("user.dir")
        // .env файл находится в корне проекта (ai_challenge_november), а не в уроке
        // Ищем корень проекта, идя вверх от текущей директории
        val projectRoot = findProjectRoot(currentDir)
        
        try {
            dotenv {
                directory = projectRoot
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
     * Находит корень проекта (ai_challenge_november)
     * Ищет директорию, содержащую папки lesson-XX-*
     */
    private fun findProjectRoot(currentDir: String): String {
        var dir = File(currentDir)
        
        // Идем вверх по директориям, пока не найдем директорию с папками lesson-XX-*
        while (true) {
            // Проверяем, есть ли в текущей директории поддиректории lesson-XX-*
            try {
                val lessonDirs = dir.listFiles()?.filter { file ->
                    file.isDirectory && file.name.matches(Regex("lesson-\\d+.*"))
                }
                if (lessonDirs != null && lessonDirs.isNotEmpty()) {
                    // Нашли корень проекта (директорию, содержащую папки lesson-XX-*)
                    return dir.absolutePath
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
        // (fallback на системные переменные окружения)
        return currentDir
    }
    
    /**
     * Находит корень урока (папку lesson-XX-*)
     * Ищет папку lesson-XX-* вверх по дереву директорий от текущей директории
     * Если не находит вверх, ищет в поддиректориях текущей директории
     */
    private fun findLessonRoot(currentDir: String): String {
        var dir = File(currentDir)
        
        // Сначала идем вверх по директориям, пока не найдем папку lesson-XX-*
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
                    // Если запускаем из корня проекта, ищем текущий урок
                    val currentLesson = lessonDirs.firstOrNull { it.name.contains("lesson-03") }
                        ?: lessonDirs.firstOrNull()
                    if (currentLesson != null) {
                        return currentLesson.absolutePath
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
        // (fallback на системные переменные окружения)
        return currentDir
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

