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
    val documentsPath: String,
    val projectDocsPath: String? = null,
    val projectReadmePath: String? = null
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
 * Конфигурация истории чата
 */
data class ChatHistoryConfig(
    val maxMessages: Int = 10,
    val maxTokens: Int = 2000,
    val strategy: String = "sliding"  // "sliding" | "token_limit" | "none"
)

/**
 * Конфигурация чата
 */
data class ChatConfig(
    val history: ChatHistoryConfig = ChatHistoryConfig()
)

/**
 * Конфигурация Git MCP
 */
data class GitMCPConfig(
    val enabled: Boolean = true,
    val jarPath: String? = null,
    val cacheDurationMinutes: Int = 5
)

/**
 * Конфигурация RAG MCP
 */
data class RagMCPConfig(
    val enabled: Boolean = false,
    val jarPath: String? = null
)

/**
 * Конфигурация Git
 */
data class GitConfig(
    val mcp: GitMCPConfig = GitMCPConfig()
)

/**
 * Конфигурация RAG
 */
data class RAGConfig(
    val retrieval: RAGRetrievalConfig = RAGRetrievalConfig(),
    val filter: RAGFilterConfig = RAGFilterConfig(),
    val mcp: RagMCPConfig? = null
)

/**
 * Конфигурация шаблонов промптов
 */
data class PromptTemplatesConfig(
    val default: String = "default",
    val available: List<String> = listOf("default", "code_assistant", "qa_assistant")
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
    val localLLM: LocalLLMConfig? = null,
    val rag: RAGConfig = RAGConfig(),
    val chat: ChatConfig = ChatConfig(),
    val git: GitConfig = GitConfig(),
    val promptTemplates: PromptTemplatesConfig = PromptTemplatesConfig()
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
        val configDir = findConfigDirectory()
        val serverConfigFile = File(configDir, "server.yaml")
        logger.info("Loading config from: ${serverConfigFile.absolutePath}")
        if (!serverConfigFile.exists()) {
            throw IllegalStateException("Config file not found: ${serverConfigFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val configContent = serverConfigFile.readText()
        logger.debug("Config file content (first 500 chars): ${configContent.take(500)}")
        val serverConfigMap = yaml.load<Map<String, Any>>(configContent)
        
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
            documentsPath = resolveEnvVar(indexingMap["documentsPath"] as? String ?: "documents"),
            projectDocsPath = indexingMap["projectDocsPath"] as? String,
            projectReadmePath = indexingMap["projectReadmePath"] as? String
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
        
        // Конфигурация RAG MCP
        val ragMCPMap = serverConfigMap["rag"]?.let { it as? Map<String, Any> }?.get("mcp") as? Map<String, Any>
        val ragMCP = ragMCPMap?.let {
            RagMCPConfig(
                enabled = (it["enabled"] as? Boolean) ?: false,
                jarPath = it["jarPath"] as? String
            )
        }
        
        val rag = RAGConfig(
            retrieval = retrieval,
            filter = filter,
            mcp = ragMCP
        )
        
        // Конфигурация чата
        val chatMap = serverConfigMap["chat"] as? Map<String, Any> ?: emptyMap()
        val historyMap = chatMap["history"] as? Map<String, Any> ?: emptyMap()
        val history = ChatHistoryConfig(
            maxMessages = (historyMap["maxMessages"] as? Number)?.toInt() ?: 10,
            maxTokens = (historyMap["maxTokens"] as? Number)?.toInt() ?: 2000,
            strategy = historyMap["strategy"] as? String ?: "sliding"
        )
        val chat = ChatConfig(history = history)
        
        // Конфигурация Git
        val gitMap = serverConfigMap["git"] as? Map<String, Any> ?: emptyMap()
        val gitMCPMap = gitMap["mcp"] as? Map<String, Any> ?: emptyMap()
        val gitMCP = GitMCPConfig(
            enabled = (gitMCPMap["enabled"] as? Boolean) ?: true,
            jarPath = gitMCPMap["jarPath"] as? String,
            cacheDurationMinutes = (gitMCPMap["cacheDurationMinutes"] as? Number)?.toInt() ?: 5
        )
        val git = GitConfig(mcp = gitMCP)
        
        // Конфигурация локальной LLM
        val localLLMMap = serverConfigMap["localLLM"] as? Map<String, Any>
        val localLLM = localLLMMap?.let {
            // Логируем сырое значение baseUrl из конфига
            val rawBaseUrl = it["baseUrl"] as? String
            val logger = org.slf4j.LoggerFactory.getLogger(Config::class.java)
            logger.info("Loading localLLM config:")
            logger.info("  raw baseUrl from config: $rawBaseUrl")
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
            
            val resolvedBaseUrl = resolveEnvVar(rawBaseUrl ?: "http://localhost:11434")
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
        
        // Конфигурация шаблонов промптов
        val promptTemplatesMap = serverConfigMap["promptTemplates"] as? Map<String, Any>
        val promptTemplates = promptTemplatesMap?.let {
            PromptTemplatesConfig(
                default = (it["default"] as? String) ?: "default",
                available = (it["available"] as? List<*>)?.mapNotNull { item -> item as? String } 
                    ?: listOf("default", "code_assistant", "qa_assistant")
            )
        } ?: PromptTemplatesConfig()
        
        return AppConfig(
            server = server,
            ollama = ollama,
            knowledgeBase = knowledgeBase,
            indexing = indexing,
            ai = ai,
            localLLM = localLLM,
            rag = rag,
            chat = chat,
            git = git,
            promptTemplates = promptTemplates
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
     * Находит корень проекта (lesson-28-llm-optimization)
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
            logger.info("Found config in current directory: ${configDir.absolutePath}")
            return configDir.absolutePath
        }
        
        // Ищем lesson-28-llm-optimization вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-28-llm-optimization") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    logger.info("Found config in lesson-28-llm-optimization: ${configDir.absolutePath}")
                    return configDir.absolutePath
                }
            }
            
            val lessonDir = File(searchDir, "lesson-28-llm-optimization")
            if (lessonDir.exists()) {
                configDir = File(lessonDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    logger.info("Found config in lesson-28-llm-optimization subdirectory: ${configDir.absolutePath}")
                    return configDir.absolutePath
                }
            }
            
            // Также проверяем lesson-27-llm-vps для обратной совместимости
            if (searchDir.name == "lesson-27-llm-vps") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    logger.info("Found config in lesson-27-llm-vps: ${configDir.absolutePath}")
                    return configDir.absolutePath
                }
            }
            
            val lesson27Dir = File(searchDir, "lesson-27-llm-vps")
            if (lesson27Dir.exists()) {
                configDir = File(lesson27Dir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    logger.info("Found config in lesson-27-llm-vps subdirectory: ${configDir.absolutePath}")
                    return configDir.absolutePath
                }
            }
            
            searchDir = searchDir.parentFile
        }
        
        throw IllegalStateException(
            "Config directory not found. Searched in:\n" +
            "- ${File(currentDir, "config").absolutePath}\n" +
            "Please ensure config/server.yaml exists in lesson-28-llm-optimization directory."
        )
    }
}

