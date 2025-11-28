package com.prike.config

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация сервера
 */
data class ServerConfig(
    val port: Int,
    val host: String
)

/**
 * Конфигурация Ollama
 */
data class OllamaConfig(
    val baseUrl: String,
    val model: String,
    val timeout: Long
)

/**
 * Конфигурация базы знаний
 */
data class KnowledgeBaseConfig(
    val databasePath: String
)

/**
 * Конфигурация индексации
 */
data class IndexingConfig(
    val chunkSize: Int,
    val overlapSize: Int,
    val documentsPath: String
)

/**
 * Конфигурация AI (OpenRouter)
 */
data class AIConfig(
    val provider: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000
)

/**
 * Конфигурация поиска для RAG
 */
data class RAGRetrievalConfig(
    val topK: Int = 5,
    val minSimilarity: Float = 0.4f
)

/**
 * Конфигурация порогового фильтра
 */
data class ThresholdFilterConfig(
    val minSimilarity: Float = 0.6f,
    val keepTop: Int? = null
)

/**
 * Конфигурация реранкера
 */
data class RerankerConfig(
    val model: String = "gpt-4o-mini",
    val maxChunks: Int = 6,
    val systemPrompt: String = "Ты — reranker. Оцени релевантность каждого чанка вопросу."
)

/**
 * Конфигурация фильтрации для RAG
 */
data class RAGFilterConfig(
    val enabled: Boolean = true,
    val type: String = "threshold",  // "none" | "threshold" | "reranker" | "hybrid"
    val threshold: ThresholdFilterConfig = ThresholdFilterConfig(),
    val reranker: RerankerConfig = RerankerConfig()
)

/**
 * Конфигурация RAG
 */
data class RAGConfig(
    val retrieval: RAGRetrievalConfig = RAGRetrievalConfig(),
    val filter: RAGFilterConfig = RAGFilterConfig()
)

/**
 * Главная конфигурация приложения
 */
data class AppConfig(
    val server: ServerConfig,
    val ollama: OllamaConfig,
    val knowledgeBase: KnowledgeBaseConfig,
    val indexing: IndexingConfig,
    val ai: AIConfig,
    val rag: RAGConfig = RAGConfig()
)

object Config {
    private val logger = LoggerFactory.getLogger(Config::class.java)
    
    private val dotenv = run {
        val projectRoot = findProjectRoot()
        try {
            dotenv {
                directory = projectRoot
                filename = ".env"
                ignoreIfMissing = true
            }
        } catch (e: Exception) {
            dotenv {
                ignoreIfMissing = true
            }
        }
    }
    
    fun load(): AppConfig {
        // Загружаем конфигурацию сервера
        val serverConfigFile = File(findConfigDirectory(), "server.yaml")
        if (!serverConfigFile.exists()) {
            throw IllegalStateException("Config file not found: ${serverConfigFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val serverConfigMap = yaml.load<Map<String, Any>>(serverConfigFile.readText())
        
        // Конфигурация сервера
        val serverMap = serverConfigMap["server"] as? Map<String, Any> ?: emptyMap()
        val server = ServerConfig(
            port = (serverMap["port"] as? Number)?.toInt() ?: 8080,
            host = serverMap["host"] as? String ?: "0.0.0.0"
        )
        
        // Конфигурация Ollama
        val ollamaMap = serverConfigMap["ollama"] as? Map<String, Any> ?: emptyMap()
        val ollama = OllamaConfig(
            baseUrl = resolveEnvVar(ollamaMap["baseUrl"] as? String ?: "http://localhost:11434"),
            model = resolveEnvVar(ollamaMap["model"] as? String ?: "nomic-embed-text"),
            timeout = (ollamaMap["timeout"] as? Number)?.toLong() ?: 30000
        )
        
        // Конфигурация базы знаний
        val knowledgeBaseMap = serverConfigMap["knowledgeBase"] as? Map<String, Any> ?: emptyMap()
        val knowledgeBase = KnowledgeBaseConfig(
            databasePath = resolveEnvVar(knowledgeBaseMap["databasePath"] as? String ?: "data/knowledge_base.db")
        )
        
        // Конфигурация индексации
        val indexingMap = serverConfigMap["indexing"] as? Map<String, Any> ?: emptyMap()
        val indexing = IndexingConfig(
            chunkSize = (indexingMap["chunkSize"] as? Number)?.toInt() ?: 800,
            overlapSize = (indexingMap["overlapSize"] as? Number)?.toInt() ?: 100,
            documentsPath = resolveEnvVar(indexingMap["documentsPath"] as? String ?: "documents")
        )
        
        // Конфигурация AI (OpenRouter)
        val aiMap = serverConfigMap["ai"] as? Map<String, Any> ?: emptyMap()
        val ai = AIConfig(
            provider = aiMap["provider"] as? String ?: "openrouter",
            apiKey = resolveEnvVar(aiMap["apiKey"] as? String ?: ""),
            model = resolveEnvVar(aiMap["model"] as? String ?: "gpt-4o-mini"),
            temperature = (aiMap["temperature"] as? Number)?.toDouble() ?: 0.7,
            maxTokens = (aiMap["maxTokens"] as? Number)?.toInt() ?: 2000
        )
        
        // Конфигурация RAG
        val ragMap = serverConfigMap["rag"] as? Map<String, Any> ?: emptyMap()
        
        // Конфигурация поиска
        val retrievalMap = ragMap["retrieval"] as? Map<String, Any> ?: emptyMap()
        val retrieval = RAGRetrievalConfig(
            topK = (retrievalMap["topK"] as? Number)?.toInt() ?: 5,
            minSimilarity = (retrievalMap["minSimilarity"] as? Number)?.toFloat() ?: 0.4f
        )
        
        // Конфигурация фильтра
        val filterMap = ragMap["filter"] as? Map<String, Any> ?: emptyMap()
        val thresholdMap = filterMap["threshold"] as? Map<String, Any> ?: emptyMap()
        val threshold = ThresholdFilterConfig(
            minSimilarity = (thresholdMap["minSimilarity"] as? Number)?.toFloat() ?: 0.6f,
            keepTop = (thresholdMap["keepTop"] as? Number)?.toInt()
        )
        
        // Конфигурация реранкера
        val rerankerMap = filterMap["reranker"] as? Map<String, Any> ?: emptyMap()
        val reranker = RerankerConfig(
            model = resolveEnvVar(rerankerMap["model"] as? String ?: "gpt-4o-mini"),
            maxChunks = (rerankerMap["maxChunks"] as? Number)?.toInt() ?: 6,
            systemPrompt = rerankerMap["systemPrompt"] as? String ?: "Ты — reranker. Оцени релевантность каждого чанка вопросу."
        )
        
        val filter = RAGFilterConfig(
            enabled = (filterMap["enabled"] as? Boolean) ?: true,
            type = filterMap["type"] as? String ?: "threshold",
            threshold = threshold,
            reranker = reranker
        )
        
        val rag = RAGConfig(
            retrieval = retrieval,
            filter = filter
        )
        
        return AppConfig(
            server = server,
            ollama = ollama,
            knowledgeBase = knowledgeBase,
            indexing = indexing,
            ai = ai,
            rag = rag
        )
    }
    
    private fun resolveEnvVar(value: String): String {
        if (value.startsWith("\${") && value.endsWith("}")) {
            val envVarName = value.substring(2, value.length - 1)
            return dotenv[envVarName] 
                ?: System.getenv(envVarName)
                ?: throw IllegalStateException("Environment variable $envVarName not found in .env file or system environment")
        }
        return value
    }
    
    /**
     * Находит корень проекта (lesson-19-rag-chat)
     */
    private fun findProjectRoot(): String {
        var currentDir = File(System.getProperty("user.dir"))
        
        if (currentDir.name == "server") {
            currentDir = currentDir.parentFile
        }
        
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            val envFile = File(searchDir, ".env")
            if (envFile.exists()) {
                return searchDir.absolutePath
            }
            
            val parent = searchDir.parentFile
            if (parent == null || parent == searchDir) {
                break
            }
            searchDir = parent
        }
        
        return currentDir.absolutePath
    }
    
    private fun findConfigDirectory(): String {
        var currentDir = File(System.getProperty("user.dir"))
        
        if (currentDir.name == "server") {
            currentDir = currentDir.parentFile
        }
        
        // Проверяем config в текущей директории
        var configDir = File(currentDir, "config")
        if (configDir.exists() && File(configDir, "server.yaml").exists()) {
            return configDir.absolutePath
        }
        
        // Ищем lesson-19-rag-chat вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-19-rag-chat") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            val lessonDir = File(searchDir, "lesson-19-rag-chat")
            if (lessonDir.exists()) {
                configDir = File(lessonDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            searchDir = searchDir.parentFile
        }
        
        throw IllegalStateException(
            "Config directory not found. Searched in:\n" +
            "- ${File(currentDir, "config").absolutePath}\n" +
            "Please ensure config/server.yaml exists in lesson-19-rag-chat directory."
        )
    }
}

