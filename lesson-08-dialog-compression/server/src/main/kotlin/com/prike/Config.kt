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
    private const val CURRENT_LESSON_DIR = "lesson-08-dialog-compression"

    private val lessonRoot: File by lazy {
        resolveLessonRoot(File(System.getProperty("user.dir")))
    }

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
    val dialogCompressionConfig: DialogCompressionConfig by lazy { loadDialogCompressionConfig() }

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
                (loaded as? Map<String, Any?>) ?: emptyMap()
            }
        }

        return DialogCompressionConfigLoader.load(yamlFile, provider)
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

