package com.prike

import com.prike.config.AIConfig
import com.prike.config.TokenUsageLessonConfig
import com.prike.config.TokenUsageScenarioConfig
import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация приложения для урока по анализу токенов.
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
        } catch (_: Exception) {
            dotenv {
                ignoreIfMissing = true
            }
        }
    }

    val serverHost: String = dotenv["SERVER_HOST"] ?: System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val serverPort: Int = (dotenv["SERVER_PORT"] ?: System.getenv("SERVER_PORT") ?: "8080").toInt()

    val aiConfig: AIConfig = loadAIConfig()
    val lessonConfig: TokenUsageLessonConfig = loadLessonConfig()

    private fun loadAIConfig(): AIConfig {
        val lessonRoot = findLessonRoot(System.getProperty("user.dir"))
        val configDir = File(lessonRoot, "config")
        val yamlFile = File(configDir, "ai.yaml")

        val yaml = Yaml()
        val configMap: Map<String, Any?>? = runCatching {
            if (yamlFile.exists()) {
                yamlFile.inputStream().use { stream ->
                    val loaded = yaml.load<Any>(stream)
                    @Suppress("UNCHECKED_CAST")
                    when (loaded) {
                        is Map<*, *> -> loaded as? Map<String, Any?>
                        else -> null
                    }
                }
            } else null
        }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        val aiSection: Map<String, Any?> = runCatching {
            val aiValue = configMap?.get("ai")
            when (aiValue) {
                is Map<*, *> -> aiValue as? Map<String, Any?> ?: emptyMap()
                else -> emptyMap()
            }
        }.getOrElse { emptyMap() }

        val apiKey = dotenv["OPENAI_API_KEY"]
            ?: System.getenv("OPENAI_API_KEY")
            ?: run {
                val currentDir = System.getProperty("user.dir")
                val foundLessonRoot = findLessonRoot(currentDir)
                val envFile = File(foundLessonRoot, ".env")
                throw IllegalStateException(
                    buildString {
                        appendLine("OPENAI_API_KEY не найден.")
                        appendLine("Добавьте переменную окружения в .env текущего урока.")
                        appendLine("Ожидаемый путь: ${envFile.absolutePath}")
                        appendLine("Файл существует: ${envFile.exists()}")
                        appendLine("Рабочая директория: $currentDir")
                        appendLine("Найденный корень урока: $foundLessonRoot")
                    }
                )
            }

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

    private fun loadLessonConfig(): TokenUsageLessonConfig {
        val currentDir = System.getProperty("user.dir")
        val lessonRoot = findLessonRoot(currentDir)
        val configDir = File(lessonRoot, "config")
        val yamlFile = File(configDir, "token-usage.yaml")

        val yaml = Yaml()
        val configMap: Map<String, Any?> = runCatching {
            if (!yamlFile.exists()) {
                throw IllegalStateException("Файл конфигурации урока ${yamlFile.absolutePath} не найден")
            }
            yamlFile.inputStream().use { stream ->
                val loaded = yaml.load<Any>(stream)
                @Suppress("UNCHECKED_CAST")
                when (loaded) {
                    is Map<*, *> -> loaded as? Map<String, Any?>
                    else -> null
                } ?: emptyMap()
            }
        }.getOrElse { throwable ->
            throw IllegalStateException("Не удалось загрузить token-usage.yaml: ${throwable.message}", throwable)
        }

        val lessonSection = (configMap["lesson"] as? Map<*, *>)?.asStringMap() ?: emptyMap()
        val scenariosSection = (configMap["scenarios"] as? List<*>) ?: emptyList<Any?>()

        val promptTokenLimit = lessonSection["promptTokenLimit"]?.toString()?.toIntOrNull()
            ?: throw IllegalStateException("В секции lesson необходимо указать promptTokenLimit (целое число)")

        val defaultMaxResponseTokens = lessonSection["defaultMaxResponseTokens"]?.toString()?.toIntOrNull()
            ?: DEFAULT_MAX_RESPONSE_TOKENS

        val historyLimit = lessonSection["historyLimit"]?.toString()?.toIntOrNull()
            ?: DEFAULT_HISTORY_LIMIT

        val tokenEncoding = lessonSection["tokenEncoding"]?.takeIf { !it.isNullOrBlank() }
            ?: DEFAULT_ENCODING

        if (scenariosSection.isEmpty()) {
            throw IllegalStateException("Секция scenarios в token-usage.yaml не должна быть пустой")
        }

        val scenarios = scenariosSection.mapNotNull { node ->
            val map = (node as? Map<*, *>)?.asStringMap() ?: return@mapNotNull null
            val id = map["id"]?.takeIf { !it.isNullOrBlank() }
                ?: throw IllegalStateException("Каждый сценарий должен содержать непустое поле id")
            val name = map["name"]?.takeIf { !it.isNullOrBlank() }
                ?: throw IllegalStateException("Сценарий $id должен содержать поле name")
            val prompt = map["defaultPrompt"]?.takeIf { !it.isNullOrBlank() }
                ?: throw IllegalStateException("Сценарий $id должен содержать поле defaultPrompt")
            val description = map["description"]?.takeIf { !it.isNullOrBlank() }
            val temperature = map["temperature"]?.toDoubleOrNull()
            val maxResponseTokens = map["maxResponseTokens"]?.toIntOrNull()

            TokenUsageScenarioConfig(
                id = id,
                name = name,
                defaultPrompt = prompt,
                description = description,
                temperature = temperature,
                maxResponseTokens = maxResponseTokens
            )
        }

        return TokenUsageLessonConfig(
            promptTokenLimit = promptTokenLimit,
            defaultMaxResponseTokens = defaultMaxResponseTokens,
            historyLimit = historyLimit,
            tokenEncoding = tokenEncoding,
            scenarios = scenarios
        )
    }

    private fun Map<*, *>.asStringMap(): Map<String, String?> =
        entries.associate { (key, value) ->
            key.toString() to value?.toString()
        }

    private fun findLessonRoot(currentDir: String): String {
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

    private const val CURRENT_LESSON_DIR = "lesson-07-token-usage"
    private const val DEFAULT_HISTORY_LIMIT = 10
    private const val DEFAULT_MAX_RESPONSE_TOKENS = 512
    private const val DEFAULT_ENCODING = "cl100k_base"
}
