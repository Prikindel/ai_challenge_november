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
    val database: DatabaseConfig
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
            val configMap = yaml.load<Map<String, Any>>(FileInputStream(configFile))
            
            return Config(
                server = ServerConfig.fromMap(configMap["server"] as Map<String, Any>),
                reviews = ReviewsConfig.fromMap(configMap["reviews"] as Map<String, Any>),
                koog = KoogConfig.fromMap(configMap["koog"] as Map<String, Any>),
                telegram = TelegramConfig.fromMap(configMap["telegram"] as Map<String, Any>),
                database = DatabaseConfig.fromMap(configMap["database"] as Map<String, Any>)
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
                dotenv[varName] ?: System.getenv(varName) ?: matchResult.value
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
            val apiMap = map["api"] as Map<String, Any>
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
            val oauthToken = (map["oauthToken"] as? String)?.let { token ->
                Config.resolveEnvVar(token)
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
    val apiKey: String
) {
    companion object {
        fun fromMap(map: Map<String, Any>): KoogConfig {
            val apiKey = (map["apiKey"] as String)
            val resolvedApiKey = Config.resolveEnvVar(apiKey)
            
            return KoogConfig(
                enabled = (map["enabled"] as? Boolean) ?: true,
                model = map["model"] as String,
                apiKey = resolvedApiKey
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
            val mcpMap = map["mcp"] as Map<String, Any>
            val botToken = Config.resolveEnvVar((map["botToken"] as String))
            val chatId = Config.resolveEnvVar((map["chatId"] as String))
            
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

