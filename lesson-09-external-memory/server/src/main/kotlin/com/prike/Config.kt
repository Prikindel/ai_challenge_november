package com.prike

import com.prike.config.AIConfig
import com.prike.config.MemoryConfig
import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация приложения
 * Загружает переменные окружения из .env файла и настройки из YAML
 */
object Config {
    private const val CURRENT_LESSON_DIR = "lesson-09-external-memory"

    private val lessonRoot: File by lazy {
        resolveLessonRoot(File(System.getProperty("user.dir")))
    }

    val lessonRootPath: File
        get() = lessonRoot

    private val dotenv = run {
        val envFile = File(lessonRoot, ".env")
        try {
            dotenv {
                directory = lessonRoot.absolutePath
                filename = envFile.name
                ignoreIfMissing = true
            }
        } catch (_: Exception) {
            dotenv {
                ignoreIfMissing = true
            }
        }
    }
    
    val serverHost: String = dotenv["SERVER_HOST"] ?: System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val serverPort: Int = (dotenv["SERVER_PORT"] ?: System.getenv("SERVER_PORT") ?: "8080").toInt()

    val aiConfig: AIConfig by lazy { loadAIConfig() }
    val memoryConfig: MemoryConfig by lazy { loadMemoryConfig() }

    private fun loadAIConfig(): AIConfig {
        val apiKey = dotenv["OPENAI_API_KEY"]
            ?: System.getenv("OPENAI_API_KEY")
            ?: throw IllegalStateException(
                buildString {
                    appendLine("Не найден OPENAI_API_KEY для урока $CURRENT_LESSON_DIR.")
                    appendLine("Создайте файл ${File(lessonRoot, ".env").absolutePath} и укажите ключ.")
                }
            )

        val yamlFile = File(lessonRoot, "config/ai.yaml")
        val yaml = Yaml()
        val configMap: Map<String, Any?> = runCatching {
            if (!yamlFile.exists()) emptyMap<String, Any?>() else {
                yamlFile.inputStream().use { stream ->
                    val loaded = yaml.load<Any>(stream)
                    @Suppress("UNCHECKED_CAST")
                    (loaded as? Map<String, Any?>) ?: emptyMap()
                }
            }
        }.getOrElse { emptyMap() }

        val aiSection = configMap["ai"] as? Map<String, Any?> ?: emptyMap()

        val apiUrl = (aiSection["apiUrl"] as? String)?.takeIf { it.isNotBlank() }
            ?: "https://api.openai.com/v1/chat/completions"
        val model = (aiSection["model"] as? String)?.takeIf { it.isNotBlank() } ?: "gpt-4o-mini"
        val temperature = (aiSection["temperature"] as? Number)?.toDouble() ?: 0.3
        val maxTokens = (aiSection["maxTokens"] as? Number)?.toInt() ?: 1024
        val requestTimeout = (aiSection["requestTimeout"] as? Number)?.toInt() ?: 60
        val systemPrompt = (aiSection["systemPrompt"] as? String)?.takeIf { it.isNotBlank() }
        val useJsonFormat = aiSection["useJsonFormat"] as? Boolean ?: false
        
        return AIConfig(
            apiKey = apiKey,
            apiUrl = apiUrl,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            requestTimeout = requestTimeout,
            systemPrompt = systemPrompt,
            useJsonFormat = useJsonFormat
        )
    }

    private fun loadMemoryConfig(): MemoryConfig {
        val yamlFile = File(lessonRoot, "config/memory.yaml")
        val yaml = Yaml()
        
        val configMap: Map<String, Any?> = runCatching {
            if (!yamlFile.exists()) {
                // Если файл не найден, используем значения по умолчанию
                return MemoryConfig(
                    storageType = MemoryConfig.StorageType.SQLITE,
                    sqlite = MemoryConfig.SqliteConfig("data/memory.db"),
                    json = MemoryConfig.JsonConfig("data/memory.json"),
                    limits = MemoryConfig.MemoryLimits()
                )
            }
            yamlFile.inputStream().use { stream ->
                val loaded = yaml.load<Any>(stream)
                @Suppress("UNCHECKED_CAST")
                (loaded as? Map<String, Any?>) ?: emptyMap()
            }
        }.getOrElse { emptyMap() }

        val memorySection = configMap["memory"] as? Map<String, Any?> ?: emptyMap()
        
        // Тип хранилища
        val storageTypeStr = (memorySection["storageType"] as? String)?.uppercase() ?: "SQLITE"
        val storageType = when (storageTypeStr) {
            "SQLITE" -> MemoryConfig.StorageType.SQLITE
            "JSON" -> MemoryConfig.StorageType.JSON
            else -> MemoryConfig.StorageType.SQLITE
        }
        
        // Конфигурация SQLite
        val sqliteSection = memorySection["sqlite"] as? Map<String, Any?>
        val sqliteConfig = sqliteSection?.let {
            MemoryConfig.SqliteConfig(
                databasePath = (it["databasePath"] as? String) ?: "data/memory.db"
            )
        }
        
        // Конфигурация JSON
        val jsonSection = memorySection["json"] as? Map<String, Any?>
        val jsonConfig = jsonSection?.let {
            MemoryConfig.JsonConfig(
                filePath = (it["filePath"] as? String) ?: "data/memory.json",
                prettyPrint = it["prettyPrint"] as? Boolean ?: true
            )
        }
        
        // Ограничения
        val limitsSection = memorySection["limits"] as? Map<String, Any?>
        val limits = limitsSection?.let {
            MemoryConfig.MemoryLimits(
                maxEntries = (it["maxEntries"] as? Number)?.toInt(),
                maxHistoryDays = (it["maxHistoryDays"] as? Number)?.toInt(),
                autoCleanup = it["autoCleanup"] as? Boolean ?: false
            )
        }

        // Суммаризация
        val summarizationSection = memorySection["summarization"] as? Map<String, Any?>
        val summarization = summarizationSection?.let {
            MemoryConfig.SummarizationConfig(
                enabled = it["enabled"] as? Boolean ?: true,
                userMessagesPerSummary = (it["userMessagesPerSummary"] as? Number)?.toInt() ?: 10,
                userMessagesPerSegment = (it["userMessagesPerSegment"] as? Number)?.toInt() ?: 100,
                model = (it["model"] as? String)?.takeIf { s -> s.isNotBlank() },
                temperature = (it["temperature"] as? Number)?.toDouble() ?: 0.2,
                maxTokens = (it["maxTokens"] as? Number)?.toInt() ?: 900
            )
        }

        return MemoryConfig(
            storageType = storageType,
            sqlite = sqliteConfig,
            json = jsonConfig,
            limits = limits,
            summarization = summarization
        )
    }

    private fun resolveLessonRoot(startDir: File): File {
        var dir: File? = startDir
        while (dir != null) {
            if (dir.name == CURRENT_LESSON_DIR) {
                return dir
            }
            dir.listFiles()
                ?.firstOrNull { it.isDirectory && it.name == CURRENT_LESSON_DIR }
                ?.let { return it }

            dir = dir.parentFile
        }

        error("Не удалось определить корень урока $CURRENT_LESSON_DIR от директории ${startDir.absolutePath}")
    }
}

