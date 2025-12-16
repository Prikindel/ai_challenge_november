package com.voiceagent

import com.voiceagent.config.AIConfig
import com.voiceagent.config.LocalLLMAuthConfig
import com.voiceagent.config.LocalLLMConfig
import com.voiceagent.config.SpeechRecognitionConfig
import com.voiceagent.config.VoiceConfig
import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File
import org.slf4j.LoggerFactory

/**
 * Конфигурация приложения
 * Загружает переменные окружения из .env файла и настройки AI из YAML
 */
object Config {
    private val logger = LoggerFactory.getLogger(Config::class.java)
    val lessonRoot: String by lazy { findLessonRoot(System.getProperty("user.dir")) }

    private val dotenv = run {
        val currentDir = System.getProperty("user.dir")
        
        // Определяем корень урока lesson-31-voice-agent
        // Путь относительно Config.kt: server/src/main/kotlin/com/voiceagent/Config.kt
        // Нужно найти: lesson-31-voice-agent/.env
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
     * Находит корень урока (папку lesson-31-voice-agent)
     */
    private fun findLessonRoot(currentDir: String): String {
        var dir = File(currentDir)
        
        // Идем вверх по директориям, пока не найдем папку lesson-31-voice-agent
        while (dir != null) {
            // Проверяем, есть ли в этой директории папка lesson-31-voice-agent
            val lessonDir = File(dir, "lesson-31-voice-agent")
            if (lessonDir.exists() && lessonDir.isDirectory) {
                return lessonDir.absolutePath
            }
            
            // Или проверяем, является ли сама директория корнем урока
            if (dir.name == "lesson-31-voice-agent") {
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
    val voiceConfig: VoiceConfig by lazy { loadVoiceConfig() }
    val aiConfig: AIConfig by lazy { loadAIConfig() }

    /**
     * Загружает конфигурацию AI из YAML файла
     * API ключ приоритетно берется из .env (для безопасности)
     */
    private fun loadAIConfig(): AIConfig {
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
        
        @Suppress("UNCHECKED_CAST")
        val authSection: Map<String, Any> = runCatching {
            val auth = aiSection["auth"]
            when (auth) {
                is Map<*, *> -> auth as? Map<String, Any> ?: emptyMap()
                else -> emptyMap()
            }
        }.getOrElse { emptyMap() }

        // API ключ приоритетно из .env (для безопасности) и поддержка OPENROUTER
        // Приоритет OpenRouter ключа, затем OpenAI
        val apiKey = resolveEnv(
            dotenv["OPENROUTER_API_KEY"]
                ?: System.getenv("OPENROUTER_API_KEY")
                ?: dotenv["OPENAI_API_KEY"]
                ?: System.getenv("OPENAI_API_KEY")
                ?: (aiSection["apiKey"] as? String)
                ?: (authSection["token"] as? String)
        )
        
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException("API ключ для LLM не найден (OPENROUTER_API_KEY/OPENAI_API_KEY)")
        }

        val cfg = AIConfig(
            apiKey = apiKey,
            apiUrl = (aiSection["apiUrl"] as? String)
                ?: voiceConfig.localLLM.baseUrl.trimEnd('/') + "/v1/chat/completions",
            model = (aiSection["model"] as? String)
                ?: voiceConfig.localLLM.model,
            temperature = ((aiSection["temperature"] as? Number)?.toDouble())
                ?: voiceConfig.localLLM.temperature,
            maxTokens = ((aiSection["maxTokens"] as? Number)?.toInt())
                ?: voiceConfig.localLLM.maxTokens,
            requestTimeout = ((aiSection["requestTimeout"] as? Number)?.toInt())
                ?: 60,
            systemPrompt = aiSection["systemPrompt"] as? String,
            authType = (authSection["type"] as? String)
                ?: voiceConfig.localLLM.auth?.type,
            authUser = (authSection["user"] as? String)
                ?: voiceConfig.localLLM.auth?.user,
            authPassword = (authSection["password"] as? String)
                ?: voiceConfig.localLLM.auth?.password
        )

        logger.info(
            "AI config loaded: url={}, model={}, authType={}, hasApiKey={}",
            cfg.apiUrl, cfg.model, cfg.authType ?: "bearer", cfg.apiKey?.isNotBlank()
        )
        return cfg
    }

    private fun loadVoiceConfig(): VoiceConfig {
        val configDir = File(lessonRoot, "config")
        val yamlFile = File(configDir, "server.yaml")
        val yaml = Yaml()

        val configMap: Map<String, Any>? = runCatching {
            if (yamlFile.exists()) {
                yamlFile.inputStream().use { stream ->
                    @Suppress("UNCHECKED_CAST")
                    when (val loaded = yaml.load<Any>(stream)) {
                        is Map<*, *> -> loaded as? Map<String, Any>
                        else -> null
                    }
                }
            } else null
        }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        val speechSection = (configMap?.get("speechRecognition") as? Map<String, Any>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val llmSection = (configMap?.get("localLLM") as? Map<String, Any>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val authSection = (llmSection["auth"] as? Map<String, Any>) ?: emptyMap()

        val speechConfig = SpeechRecognitionConfig(
            enabled = (speechSection["enabled"] as? Boolean) ?: true,
            provider = (speechSection["provider"] as? String) ?: "vosk",
            modelPath = (speechSection["modelPath"] as? String) ?: "models/vosk-model-small-ru-0.22",
            sampleRate = ((speechSection["sampleRate"] as? Number)?.toInt()) ?: 16000
        )

        val llmConfig = LocalLLMConfig(
            enabled = (llmSection["enabled"] as? Boolean) ?: true,
            provider = (llmSection["provider"] as? String) ?: "ollama",
            baseUrl = (llmSection["baseUrl"] as? String) ?: "http://localhost:11434",
            model = (llmSection["model"] as? String) ?: "llama3.2",
            temperature = ((llmSection["temperature"] as? Number)?.toDouble()) ?: 0.7,
            maxTokens = ((llmSection["maxTokens"] as? Number)?.toInt()) ?: 2048,
            auth = LocalLLMAuthConfig(
                type = (authSection["type"] as? String),
                user = (authSection["user"] as? String),
                password = (authSection["password"] as? String),
                token = resolveEnv(authSection["token"] as? String)
            )
        )

        return VoiceConfig(
            speechRecognition = speechConfig,
            localLLM = llmConfig
        )
    }

    private fun resolveEnv(value: String?): String? {
        if (value.isNullOrBlank()) return value
        val regex = Regex("\\$\\{([^}]+)}")
        val match = regex.matchEntire(value)
        val key = match?.groupValues?.getOrNull(1)
        return if (key != null) {
            // Если плейсхолдер не смогли заменить, считаем, что значение отсутствует
            dotenv[key] ?: System.getenv(key)
        } else value
    }
}
