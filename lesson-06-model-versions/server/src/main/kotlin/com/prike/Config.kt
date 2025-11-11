package com.prike

import com.prike.config.AIConfig
import com.prike.config.ModelComparisonLessonConfig
import com.prike.config.ModelDefinitionConfig
import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация приложения.
 * Отвечает за загрузку переменных окружения и YAML-файлов текущего урока.
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
    val lessonConfig: ModelComparisonLessonConfig = loadLessonConfig()

    val availableModels: List<ModelDefinitionConfig>
        get() = lessonConfig.models

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

        val apiKey = dotenv["HUGGINGFACE_API_KEY"]
            ?: System.getenv("HUGGINGFACE_API_KEY")
            ?: run {
                val currentDir = System.getProperty("user.dir")
                val foundLessonRoot = findLessonRoot(currentDir)
                val envFile = File(foundLessonRoot, ".env")
                throw IllegalStateException(
                    "HUGGINGFACE_API_KEY не найден. " +
                        "Добавьте его в .env текущего урока.\n" +
                        "Ожидаемый путь к .env: ${envFile.absolutePath}\n" +
                        "Файл существует: ${envFile.exists()}\n" +
                        "Рабочая директория: $currentDir\n" +
                        "Найденный корень урока: $foundLessonRoot"
                )
            }

        return AIConfig(
            apiKey = apiKey,
            apiUrl = (aiSection["apiUrl"] as? String)
                ?: "https://api-inference.huggingface.co/v1/chat/completions",
            model = aiSection["model"] as? String,
            temperature = (aiSection["temperature"] as? Number)?.toDouble(),
            maxTokens = (aiSection["maxTokens"] as? Number)?.toInt(),
            requestTimeout = (aiSection["requestTimeout"] as? Number)?.toInt() ?: 60,
            systemPrompt = aiSection["systemPrompt"] as? String,
            useJsonFormat = aiSection["useJsonFormat"] as? Boolean ?: false
        )
    }

    private fun loadLessonConfig(): ModelComparisonLessonConfig {
        val currentDir = System.getProperty("user.dir")
        val lessonRoot = findLessonRoot(currentDir)
        val configDir = File(lessonRoot, "config")
        val yamlFile = File(configDir, "models.yaml")

        val yaml = Yaml()
        val configMap: Map<String, Any?> = runCatching {
            if (!yamlFile.exists()) {
                throw IllegalStateException("Файл конфигурации моделей ${yamlFile.absolutePath} не найден")
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
            throw IllegalStateException("Не удалось загрузить models.yaml: ${throwable.message}", throwable)
        }

        val lessonSection = (configMap["lesson"] as? Map<*, *>)?.let { map ->
            map.filterKeys { it is String } as Map<String, Any?>
        } ?: emptyMap()

        val defaultQuestion = (lessonSection["defaultQuestion"] as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_QUESTION

        val configuredDefaultIds = (lessonSection["defaultModelIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val modelDefinitions = ((configMap["models"] as? List<*>))
            ?.mapNotNull { node ->
                node as? Map<*, *> ?: return@mapNotNull null
                val id = (node["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val displayName = (node["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: id
                val endpoint = (node["endpoint"] as? String)?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_MODEL_ENDPOINT
                val huggingFaceUrl = (node["huggingFaceUrl"] as? String)?.takeIf { it.isNotBlank() }
                    ?: "https://huggingface.co/$id"
                val price = (node["pricePer1kTokensUsd"] as? Number)?.toDouble()
                val defaultParams = (node["defaultParams"] as? Map<*, *>)?.let { params ->
                    params.entries.mapNotNull { entry ->
                        val key = entry.key as? String ?: return@mapNotNull null
                        key to entry.value
                    }.toMap()
                } ?: emptyMap()

                ModelDefinitionConfig(
                    id = id,
                    displayName = displayName,
                    endpoint = endpoint,
                    huggingFaceUrl = huggingFaceUrl,
                    pricePer1kTokensUsd = price,
                    defaultParams = defaultParams
                )
            }.orEmpty()

        if (modelDefinitions.isEmpty()) {
            throw IllegalStateException("Список моделей в models.yaml пуст")
        }

        val effectiveDefaultIds = sanitizeDefaultModelIds(configuredDefaultIds, modelDefinitions)

        return ModelComparisonLessonConfig(
            defaultQuestion = defaultQuestion,
            defaultModelIds = effectiveDefaultIds,
            models = modelDefinitions
        )
    }

    private fun sanitizeDefaultModelIds(
        configuredDefaultIds: List<String>,
        models: List<ModelDefinitionConfig>
    ): List<String> {
        val availableIds = models.map { it.id }.toSet()
        val cleaned = configuredDefaultIds.filter { availableIds.contains(it) }
        if (cleaned.isNotEmpty()) {
            return cleaned
        }

        if (models.isEmpty()) {
            throw IllegalStateException("Нельзя подобрать модели по умолчанию: каталог пуст")
        }

        val indices = buildSet {
            add(0)
            add(models.lastIndex)
            add(models.size / 2)
        }.sorted()

        return indices.map { index -> models[index].id }.distinct()
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

    private const val CURRENT_LESSON_DIR = "lesson-06-model-versions"
    private const val DEFAULT_MODEL_ENDPOINT = "https://api-inference.huggingface.co/v1/chat/completions"
    private val DEFAULT_QUESTION = """
        Представь, что ты выбираешь модель для аналитического ассистента. Какие сильные и слабые стороны у каждой из доступных моделей и когда стоит использовать каждую?
    """.trimIndent()
}
