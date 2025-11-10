package com.prike

import com.prike.config.AIConfig
import com.prike.config.TemperatureLessonConfig
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
     * Находит корень урока (lesson-XX-*)
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
        // (fallback на системные переменные окружения)
        return currentDir
    }

    // Сервер
    val serverHost: String = dotenv["SERVER_HOST"] ?: System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val serverPort: Int = (dotenv["SERVER_PORT"] ?: System.getenv("SERVER_PORT") ?: "8080").toInt()

    // AI Конфигурация из YAML
    val aiConfig: AIConfig = loadAIConfig()
    
    // Конфигурация урока (дефолтный вопрос, температуры)
    val lessonConfig: TemperatureLessonConfig = loadLessonConfig()

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
            ?: run {
                val currentDir = System.getProperty("user.dir")
                val foundLessonRoot = findLessonRoot(currentDir)
                val envFile = File(foundLessonRoot, ".env")
                throw IllegalStateException(
                    "OPENAI_API_KEY не найден. " +
                    "Установите его в .env файле.\n" +
                    "Ожидаемый путь к .env: ${envFile.absolutePath}\n" +
                    "Файл существует: ${envFile.exists()}\n" +
                    "Текущая рабочая директория: $currentDir\n" +
                    "Найденный корень урока: $foundLessonRoot"
                )
            }
        
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
            systemPrompt = aiSection["systemPrompt"] as? String,
            useJsonFormat = (aiSection["useJsonFormat"] as? Boolean)
                ?: false
        )
    }
    
    /**
     * Загружает конфигурацию урока из YAML файла
     */
    private fun loadLessonConfig(): TemperatureLessonConfig {
        val currentDir = System.getProperty("user.dir")
        val lessonRoot = findLessonRoot(currentDir)
        val configDir = File(lessonRoot, "config")
        val yamlFile = File(configDir, "topic.yaml")

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

        @Suppress("UNCHECKED_CAST")
        val lessonSection: Map<String, Any> = runCatching {
            val lessonValue = configMap?.get("lesson")
            when (lessonValue) {
                is Map<*, *> -> lessonValue as? Map<String, Any> ?: emptyMap()
                else -> emptyMap()
            }
        }.getOrElse { emptyMap() }

        val defaultQuestion = (lessonSection["defaultQuestion"] as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: FALLBACK_DEFAULT_QUESTION

        val defaultTemperatures = (lessonSection["defaultTemperatures"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toDouble() }
            ?.takeIf { it.isNotEmpty() }
            ?: FALLBACK_DEFAULT_TEMPERATURES

        val comparisonTemperature = (lessonSection["comparisonTemperature"] as? Number)?.toDouble()
            ?: FALLBACK_COMPARISON_TEMPERATURE

        return TemperatureLessonConfig(
            defaultQuestion = defaultQuestion,
            defaultTemperatures = defaultTemperatures,
            comparisonTemperature = comparisonTemperature
        )
    }

    private const val FALLBACK_COMPARISON_TEMPERATURE = 0.4
    private val FALLBACK_DEFAULT_TEMPERATURES = listOf(0.0, 0.7, 1.2)
    private val FALLBACK_DEFAULT_QUESTION = """
        У нас есть три друга — Анна, Борис и Виктор. Они получили три разных подарка: книгу, игру и головоломку.

        Известно, что:
        1. Анна не получила игру.
        2. Борис не получил головоломку.

        Кто какой подарок получил? Объясни ход рассуждений.
    """.trimIndent()
}
