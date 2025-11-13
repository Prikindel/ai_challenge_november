package com.prike

import com.prike.config.AIConfig
import com.prike.config.DialogCompressionConfig
import com.prike.config.DialogCompressionConfigLoader
import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация приложения
 * Загружает переменные окружения из .env файла и настройки AI из YAML
 */
object Config {
    private val lessonRoot: File = findLessonRoot(File(System.getProperty("user.dir")))

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

    val aiConfig: AIConfig = loadAIConfig()
    val dialogCompressionConfig: DialogCompressionConfig = loadDialogCompressionConfig()

    private fun loadAIConfig(): AIConfig {
        val apiKey = dotenv["OPENAI_API_KEY"]
            ?: System.getenv("OPENAI_API_KEY")
            ?: throw IllegalStateException(
                buildString {
                    appendLine("Не найден OPENAI_API_KEY для урока $CURRENT_LESSON_DIR.")
                    appendLine("Создайте файл ${lessonRoot.absolutePath + File.separator + \".env\"} и укажите ключ.")
                }
            )

        val yamlFile = File(lessonRoot, "config/ai.yaml")
        val yaml = Yaml()
        val configMap: Map<String, Any?> = runCatching {
            if (!yamlFile.exists()) return@runCatching emptyMap<String, Any?>()
            yamlFile.inputStream().use { stream ->
                val loaded = yaml.load<Any>(stream)
                @Suppress("UNCHECKED_CAST")
                when (loaded) {
                    is Map<*, *> -> loaded as? Map<String, Any?> ?: emptyMap()
                    else -> emptyMap()
                }
            }
        }.getOrElse { emptyMap() }

        @Suppress("UNCHECKED_CAST")
        val aiSection = (configMap["ai"] as? Map<String, Any?>).orEmpty()

        return AIConfig(
            apiKey = apiKey,
            apiUrl = (aiSection["apiUrl"] as? String)?.takeIf { it.isNotBlank() }
                ?: "https://api.openai.com/v1/chat/completions",
            model = aiSection["model"] as? String,
            temperature = (aiSection["temperature"] as? Number)?.toDouble(),
            maxTokens = (aiSection["maxTokens"] as? Number)?.toInt(),
            requestTimeout = (aiSection["requestTimeout"] as? Number)?.toInt() ?: 60,
            systemPrompt = aiSection["systemPrompt"] as? String,
            useJsonFormat = aiSection["useJsonFormat"] as? Boolean ?: false
        )
    }

    private fun loadDialogCompressionConfig(): DialogCompressionConfig {
        val yamlFile = File(lessonRoot, "config/compression.yaml")
        val yaml = Yaml()
        val provider: (File) -> Map<String, Any?> = { file ->
            if (!file.exists()) {
                error("Файл конфигурации ${file.absolutePath} не найден")
            }
            file.inputStream().use { stream ->
                val loaded = yaml.load<Any>(stream)
                @Suppress("UNCHECKED_CAST")
                when (loaded) {
                    is Map<*, *> -> loaded as? Map<String, Any?> ?: emptyMap()
                    else -> emptyMap()
                }
            }
        }

        return DialogCompressionConfigLoader.load(yamlFile, provider)
    }

    private fun findLessonRoot(startDir: File): File {
        var dir: File? = startDir
        while (dir != null) {
            if (dir.name == CURRENT_LESSON_DIR) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Не удалось определить корень урока $CURRENT_LESSON_DIR от директории ${startDir.absolutePath}")
    }

    private fun Map<String, Any?>?.orEmpty(): Map<String, Any?> = this ?: emptyMap()

    private const val CURRENT_LESSON_DIR = "lesson-08-dialog-compression"
}

