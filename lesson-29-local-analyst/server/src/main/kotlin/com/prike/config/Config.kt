package com.prike.config

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

/**
 * Конфигурация авторизации для локальной LLM
 */
data class LocalLLMAuthConfig(
    val type: String = "none",  // "none", "basic", "bearer"
    val user: String = "",
    val password: String = "",
    val token: String = ""
)

/**
 * Конфигурация локальной LLM (Ollama, LM Studio и др.)
 */
data class LocalLLMConfig(
    val enabled: Boolean = false,
    val provider: String = "ollama",  // ollama, lmstudio, llamacpp
    val baseUrl: String = "http://localhost:11434",
    val model: String = "llama3.2",
    val apiPath: String = "/api/generate",  // для Ollama
    val timeout: Long = 120000L,
    val auth: LocalLLMAuthConfig? = null,
    val parameters: com.prike.domain.model.LLMParameters = com.prike.domain.model.LLMParameters()
)

/**
 * Конфигурация сервера
 */
data class Config(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val ollama: OllamaConfig,
    val localLLM: LocalLLMConfig?
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
            
            // Конфигурация локальной LLM
            val localLLMMap = configMap["localLLM"] as? Map<String, Any>
            val localLLM = localLLMMap?.let {
                val logger = org.slf4j.LoggerFactory.getLogger(Config::class.java)
                logger.info("Loading localLLM config:")
                logger.info("  enabled: ${it["enabled"]}")
                logger.info("  provider: ${it["provider"]}")
                logger.info("  model: ${it["model"]}")
                
                // Конфигурация авторизации
                val authMap = it["auth"] as? Map<String, Any>
                val auth = authMap?.let { auth ->
                    LocalLLMAuthConfig(
                        type = resolveEnvVar(auth["type"] as? String ?: "none"),
                        user = resolveEnvVar(auth["user"] as? String ?: ""),
                        password = resolveEnvVar(auth["password"] as? String ?: ""),
                        token = resolveEnvVar(auth["token"] as? String ?: "")
                    )
                }
                
                val resolvedBaseUrl = resolveEnvVar(it["baseUrl"] as? String ?: "http://localhost:11434")
                logger.info("  resolved baseUrl: $resolvedBaseUrl")
                
                // Парсинг параметров LLM
                val parametersMap = it["parameters"] as? Map<String, Any>
                val parameters = parametersMap?.let { params ->
                    com.prike.domain.model.LLMParameters(
                        temperature = (params["temperature"] as? Number)?.toDouble() ?: 0.7,
                        maxTokens = (params["maxTokens"] as? Number)?.toInt() ?: 2048,
                        topP = (params["topP"] as? Number)?.toDouble() ?: 0.9,
                        topK = (params["topK"] as? Number)?.toInt() ?: 40,
                        repeatPenalty = (params["repeatPenalty"] as? Number)?.toDouble() ?: 1.1,
                        contextWindow = (params["contextWindow"] as? Number)?.toInt() ?: 4096,
                        seed = (params["seed"] as? Number)?.toInt()
                    )
                } ?: com.prike.domain.model.LLMParameters()
                
                LocalLLMConfig(
                    enabled = (it["enabled"] as? Boolean) ?: false,
                    provider = resolveEnvVar(it["provider"] as? String ?: "ollama"),
                    baseUrl = resolvedBaseUrl,
                    model = resolveEnvVar(it["model"] as? String ?: "llama3.2"),
                    apiPath = it["apiPath"] as? String ?: "/api/generate",
                    timeout = (it["timeout"] as? Number)?.toLong() ?: 120000L,
                    auth = auth,
                    parameters = parameters
                )
            }
            
            return Config(
                server = ServerConfig.fromMap(configMap["server"] as? Map<String, Any> ?: emptyMap()),
                database = DatabaseConfig.fromMap(configMap["database"] as? Map<String, Any> ?: emptyMap()),
                ollama = OllamaConfig.fromMap(configMap["ollama"] as? Map<String, Any> ?: emptyMap()),
                localLLM = localLLM
            )
        }
        
        private fun findLessonRoot(): File {
            var currentDir = File(System.getProperty("user.dir"))
            
            if (currentDir.name == "server") {
                currentDir = currentDir.parentFile
            }
            
            var searchDir: File? = currentDir
            while (searchDir != null) {
                if (searchDir.name == "lesson-29-local-analyst") {
                    return searchDir
                }
                
                val lessonDir = File(searchDir, "lesson-29-local-analyst")
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

