package com.prike.config

import com.prike.domain.model.*
import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

/**
 * Конфигурация сервера
 */
data class Config(
    val server: ServerConfig,
    val godAgent: GodAgentConfig,
    val reviews: ReviewsConfig,
    val koog: KoogConfig,
    val telegram: TelegramConfig,
    val database: DatabaseConfig,
    val ollama: OllamaConfig,
    val userProfile: UserProfile
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Config::class.java)
        private val dotenv = dotenv {
            ignoreIfMissing = true
        }
        
        /**
         * Загружает конфигурацию из YAML файла
         */
        fun load(): Config {
            val lessonRoot = findLessonRoot()
            val configFile = File(lessonRoot, "config/server.yaml")
            val profileFile = File(lessonRoot, "config/user-profile.yaml")
            
            if (!configFile.exists()) {
                throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
            }
            
            logger.info("Loading config from: ${configFile.absolutePath}")
            
            val yaml = Yaml()
            @Suppress("UNCHECKED_CAST")
            val configMap = yaml.load<Map<String, Any>>(FileInputStream(configFile)) as Map<String, Any>
            
            // Загружаем профиль пользователя
            val userProfile = if (profileFile.exists()) {
                logger.info("Loading user profile from: ${profileFile.absolutePath}")
                @Suppress("UNCHECKED_CAST")
                val profileMap = yaml.load<Map<String, Any>>(FileInputStream(profileFile)) as Map<String, Any>
                parseUserProfile(profileMap)
            } else {
                logger.warn("User profile file not found, using default profile")
                createDefaultProfile()
            }
            
            return Config(
                server = ServerConfig.fromMap(configMap["server"] as? Map<String, Any> ?: emptyMap()),
                godAgent = GodAgentConfig.fromMap(configMap["god_agent"] as? Map<String, Any> ?: emptyMap()),
                reviews = ReviewsConfig.fromMap(configMap["reviews"] as? Map<String, Any> ?: emptyMap()),
                koog = KoogConfig.fromMap(configMap["koog"] as? Map<String, Any> ?: emptyMap()),
                telegram = TelegramConfig.fromMap(configMap["telegram"] as? Map<String, Any> ?: emptyMap()),
                database = DatabaseConfig.fromMap(configMap["database"] as? Map<String, Any> ?: emptyMap()),
                ollama = OllamaConfig.fromMap(configMap["ollama"] as? Map<String, Any> ?: emptyMap()),
                userProfile = userProfile
            )
        }
        
        /**
         * Парсит профиль пользователя из YAML
         */
        private fun parseUserProfile(yaml: Map<String, Any>): UserProfile {
            @Suppress("UNCHECKED_CAST")
            val user = (yaml["user"] as? Map<String, Any>) ?: return createDefaultProfile()
            
            @Suppress("UNCHECKED_CAST")
            val preferencesMap = user["preferences"] as? Map<String, Any> ?: emptyMap()
            val preferences = UserPreferences(
                language = preferencesMap["language"] as? String ?: "ru",
                responseFormat = parseResponseFormat(preferencesMap["responseFormat"] as? String),
                timezone = preferencesMap["timezone"] as? String ?: "Europe/Moscow",
                dateFormat = preferencesMap["dateFormat"] as? String ?: "dd.MM.yyyy"
            )
            
            @Suppress("UNCHECKED_CAST")
            val workStyleMap = user["workStyle"] as? Map<String, Any> ?: emptyMap()
            
            // Обрабатываем дополнительные поля, которые не входят в стандартные параметры
            val standardFields = setOf("preferredWorkingHours", "focusAreas", "tools", "projects")
            val extraFields = workStyleMap
                .filterKeys { it !in standardFields }
                .mapValues { (_, value) -> value.toString() }
                .toMap()
            
            val workStyle = WorkStyle(
                preferredWorkingHours = workStyleMap["preferredWorkingHours"] as? String,
                focusAreas = (workStyleMap["focusAreas"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                tools = (workStyleMap["tools"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                projects = (workStyleMap["projects"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                extraFields = extraFields
            )
            
            @Suppress("UNCHECKED_CAST")
            val communicationMap = user["communicationStyle"] as? Map<String, Any> ?: emptyMap()
            val communicationStyle = CommunicationStyle(
                tone = parseTone(communicationMap["tone"] as? String),
                detailLevel = parseDetailLevel(communicationMap["detailLevel"] as? String),
                useExamples = communicationMap["useExamples"] as? Boolean ?: true,
                useEmojis = communicationMap["useEmojis"] as? Boolean ?: false
            )
            
            @Suppress("UNCHECKED_CAST")
            val contextMap = user["context"] as? Map<String, Any> ?: emptyMap()
            val context = UserContext(
                currentProject = contextMap["currentProject"] as? String,
                role = contextMap["role"] as? String,
                team = contextMap["team"] as? String,
                goals = (contextMap["goals"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
            
            return UserProfile(
                id = user["id"] as? String ?: "default",
                name = user["name"] as? String ?: "Пользователь",
                preferences = preferences,
                workStyle = workStyle,
                communicationStyle = communicationStyle,
                context = context
            )
        }
        
        private fun parseResponseFormat(format: String?): ResponseFormat {
            return when (format?.lowercase()) {
                "brief" -> ResponseFormat.BRIEF
                "detailed" -> ResponseFormat.DETAILED
                "structured" -> ResponseFormat.STRUCTURED
                else -> ResponseFormat.DETAILED
            }
        }
        
        private fun parseTone(tone: String?): Tone {
            return when (tone?.lowercase()) {
                "professional" -> Tone.PROFESSIONAL
                "casual" -> Tone.CASUAL
                "friendly" -> Tone.FRIENDLY
                else -> Tone.PROFESSIONAL
            }
        }
        
        private fun parseDetailLevel(level: String?): DetailLevel {
            return when (level?.lowercase()) {
                "low" -> DetailLevel.LOW
                "medium" -> DetailLevel.MEDIUM
                "high" -> DetailLevel.HIGH
                else -> DetailLevel.MEDIUM
            }
        }
        
        private fun createDefaultProfile(): UserProfile {
            return UserProfile(
                id = "default",
                name = "Пользователь",
                preferences = UserPreferences(),
                workStyle = WorkStyle(),
                communicationStyle = CommunicationStyle(),
                context = UserContext()
            )
        }
        
        private fun findLessonRoot(): File {
            var currentDir = File(System.getProperty("user.dir"))
            
            if (currentDir.name == "server") {
                currentDir = currentDir.parentFile
            }
            
            var searchDir: File? = currentDir
            while (searchDir != null) {
                if (searchDir.name == "lesson-32-god-agent") {
                    return searchDir
                }
                
                val lessonDir = File(searchDir, "lesson-32-god-agent")
                if (lessonDir.exists()) {
                    return lessonDir
                }
                
                searchDir = searchDir.parentFile
            }
            
            return currentDir
        }
        
        /**
         * Заменяет переменные окружения в строке вида ${VAR_NAME}
         */
        fun resolveEnvVar(value: String): String {
            return value.replace(Regex("\\$\\{([^}]+)\\}")) { matchResult ->
                val varName = matchResult.groupValues[1]
                val resolved = dotenv[varName] ?: System.getenv(varName)
                if (resolved == null) {
                    logger.warn("Environment variable '$varName' not found. Please check your .env file or environment variables.")
                }
                resolved ?: matchResult.value
            }
        }
    }
}

data class ServerConfig(
    val port: Int,
    val host: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): ServerConfig {
            return ServerConfig(
                port = (map["port"] as? Number)?.toInt() ?: 8080,
                host = (map["host"] as? String) ?: "0.0.0.0"
            )
        }
    }
}

data class ReviewsConfig(
    val api: ReviewsApiConfig
) {
    companion object {
        fun fromMap(map: Map<String, Any>): ReviewsConfig {
            @Suppress("UNCHECKED_CAST")
            val apiMap = (map["api"] as? Map<String, Any>) ?: emptyMap()
            return ReviewsConfig(
                api = ReviewsApiConfig.fromMap(apiMap)
            )
        }
    }
}

data class ReviewsApiConfig(
    val baseUrl: String,
    val store: String,
    val packageId: String,
    val oauthToken: String? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any>): ReviewsApiConfig {
            val oauthTokenRaw = map["oauthToken"] as? String
            val oauthToken = oauthTokenRaw?.let { token ->
                val resolved = Config.resolveEnvVar(token)
                // Если токен не был разрешен (остался как ${VAR_NAME}), возвращаем null
                if (resolved == token && token.startsWith("${") && token.contains("}")) {
                    val logger = org.slf4j.LoggerFactory.getLogger(ReviewsApiConfig::class.java)
                    logger.error("❌ OAuth token environment variable not resolved: $token")
                    logger.error("Please check:")
                    logger.error("  1. File .env exists in lesson-32-god-agent directory")
                    logger.error("  2. Variable REVIEWS_API_OAUTH_TOKEN is set in .env file")
                    logger.error("  3. Variable is not empty")
                    null
                } else {
                    resolved
                }
            }
            
            return ReviewsApiConfig(
                baseUrl = map["baseUrl"] as String,
                store = map["store"] as String,
                packageId = map["packageId"] as String,
                oauthToken = oauthToken
            )
        }
    }
}

data class KoogConfig(
    val enabled: Boolean,
    val model: String,
    val apiKey: String,
    val useOpenRouter: Boolean = false
) {
    companion object {
        fun fromMap(map: Map<String, Any>): KoogConfig {
            val apiKey = (map["apiKey"] as? String) ?: ""
            val resolvedApiKey = Config.resolveEnvVar(apiKey)
            
            return KoogConfig(
                enabled = (map["enabled"] as? Boolean) ?: true,
                model = (map["model"] as? String) ?: "gpt-4o-mini",
                apiKey = resolvedApiKey,
                useOpenRouter = (map["useOpenRouter"] as? Boolean) ?: false
            )
        }
    }
}

data class TelegramConfig(
    val mcp: TelegramMCPConfig,
    val botToken: String,
    val chatId: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): TelegramConfig {
            @Suppress("UNCHECKED_CAST")
            val mcpMap = (map["mcp"] as? Map<String, Any>) ?: emptyMap()
            val botToken = Config.resolveEnvVar((map["botToken"] as? String) ?: "")
            val chatId = Config.resolveEnvVar((map["chatId"] as? String) ?: "")
            
            return TelegramConfig(
                mcp = TelegramMCPConfig.fromMap(mcpMap),
                botToken = botToken,
                chatId = chatId
            )
        }
    }
}

data class TelegramMCPConfig(
    val enabled: Boolean,
    val jarPath: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): TelegramMCPConfig {
            return TelegramMCPConfig(
                enabled = (map["enabled"] as? Boolean) ?: false,
                jarPath = map["jarPath"] as String
            )
        }
    }
}

data class DatabaseConfig(
    val path: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): DatabaseConfig {
            return DatabaseConfig(
                path = map["path"] as String
            )
        }
    }
}

data class OllamaConfig(
    val baseUrl: String,
    val model: String,
    val timeout: Long
) {
    companion object {
        fun fromMap(map: Map<String, Any>): OllamaConfig {
            return OllamaConfig(
                baseUrl = (map["baseUrl"] as? String) ?: "http://localhost:11434",
                model = (map["model"] as? String) ?: "nomic-embed-text",
                timeout = (map["timeout"] as? Number)?.toLong() ?: 120000L
            )
        }
    }
}

data class GodAgentConfig(
    val enabled: Boolean,
    val mcpServers: GodAgentMCPServersConfig,
    val knowledgeBase: GodAgentKnowledgeBaseConfig,
    val personalization: GodAgentPersonalizationConfig,
    val voice: GodAgentVoiceConfig,
    val localLlm: GodAgentLocalLLMConfig
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>): GodAgentConfig {
            return GodAgentConfig(
                enabled = (map["enabled"] as? Boolean) ?: true,
                mcpServers = GodAgentMCPServersConfig.fromMap(
                    (map["mcp_servers"] as? Map<String, Any>) ?: emptyMap()
                ),
                knowledgeBase = GodAgentKnowledgeBaseConfig.fromMap(
                    (map["knowledge_base"] as? Map<String, Any>) ?: emptyMap()
                ),
                personalization = GodAgentPersonalizationConfig.fromMap(
                    (map["personalization"] as? Map<String, Any>) ?: emptyMap()
                ),
                voice = GodAgentVoiceConfig.fromMap(
                    (map["voice"] as? Map<String, Any>) ?: emptyMap()
                ),
                localLlm = GodAgentLocalLLMConfig.fromMap(
                    (map["local_llm"] as? Map<String, Any>) ?: emptyMap()
                )
            )
        }
    }
}

data class GodAgentMCPServersConfig(
    val configPath: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): GodAgentMCPServersConfig {
            return GodAgentMCPServersConfig(
                configPath = (map["config_path"] as? String) ?: "config/mcp-servers.yaml"
            )
        }
    }
}

data class GodAgentKnowledgeBaseConfig(
    val basePath: String,
    val autoIndex: Boolean,
    val watchChanges: Boolean
) {
    companion object {
        fun fromMap(map: Map<String, Any>): GodAgentKnowledgeBaseConfig {
            return GodAgentKnowledgeBaseConfig(
                basePath = (map["base_path"] as? String) ?: "knowledge-base",
                autoIndex = (map["auto_index"] as? Boolean) ?: true,
                watchChanges = (map["watch_changes"] as? Boolean) ?: true
            )
        }
    }
}

data class GodAgentPersonalizationConfig(
    val enabled: Boolean,
    val profilePath: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): GodAgentPersonalizationConfig {
            return GodAgentPersonalizationConfig(
                enabled = (map["enabled"] as? Boolean) ?: true,
                profilePath = (map["profile_path"] as? String) ?: "data/user-profile.json"
            )
        }
    }
}

data class GodAgentVoiceConfig(
    val enabled: Boolean,
    val voskModelPath: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): GodAgentVoiceConfig {
            return GodAgentVoiceConfig(
                enabled = (map["enabled"] as? Boolean) ?: true,
                voskModelPath = (map["vosk_model_path"] as? String) ?: "models/vosk-model-small-ru-0.22"
            )
        }
    }
}

data class GodAgentLocalLLMConfig(
    val enabled: Boolean,
    val provider: String,
    val baseUrl: String,
    val model: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): GodAgentLocalLLMConfig {
            return GodAgentLocalLLMConfig(
                enabled = (map["enabled"] as? Boolean) ?: true,
                provider = (map["provider"] as? String) ?: "ollama",
                baseUrl = (map["base_url"] as? String) ?: "http://localhost:11434",
                model = (map["model"] as? String) ?: "llama3.2"
            )
        }
    }
}

