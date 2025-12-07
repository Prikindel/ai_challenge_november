package com.prike.config

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
    val reviews: ReviewsConfig,
    val koog: KoogConfig,
    val telegram: TelegramConfig,
    val database: DatabaseConfig,
    val ollama: OllamaConfig
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
            
            if (!configFile.exists()) {
                throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
            }
            
            logger.info("Loading config from: ${configFile.absolutePath}")
            
            val yaml = Yaml()
            @Suppress("UNCHECKED_CAST")
            val configMap = yaml.load<Map<String, Any>>(FileInputStream(configFile)) as Map<String, Any>
            
            return Config(
                server = ServerConfig.fromMap(configMap["server"] as? Map<String, Any> ?: emptyMap()),
                reviews = ReviewsConfig.fromMap(configMap["reviews"] as? Map<String, Any> ?: emptyMap()),
                koog = KoogConfig.fromMap(configMap["koog"] as? Map<String, Any> ?: emptyMap()),
                telegram = TelegramConfig.fromMap(configMap["telegram"] as? Map<String, Any> ?: emptyMap()),
                database = DatabaseConfig.fromMap(configMap["database"] as? Map<String, Any> ?: emptyMap()),
                ollama = OllamaConfig.fromMap(configMap["ollama"] as? Map<String, Any> ?: emptyMap())
            )
        }
        
        private fun findLessonRoot(): File {
            var currentDir = File(System.getProperty("user.dir"))
            
            if (currentDir.name == "server") {
                currentDir = currentDir.parentFile
            }
            
            var searchDir: File? = currentDir
            while (searchDir != null) {
                if (searchDir.name == "lesson-24-real-task") {
                    return searchDir
                }
                
                val lessonDir = File(searchDir, "lesson-24-real-task")
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
                    logger.error("  1. File .env exists in lesson-24-real-task directory")
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

