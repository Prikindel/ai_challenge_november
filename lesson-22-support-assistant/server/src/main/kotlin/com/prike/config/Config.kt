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
 * Конфигурация индексации кода
 */
data class CodeFilesConfig(
    val enabled: Boolean = false,
    val paths: List<String> = emptyList(),  // Пути к директориям с кодом
    val extensions: List<String> = listOf(".kt", ".java", ".js", ".ts", ".py"),  // Расширения файлов
    val excludePatterns: List<String> = listOf("**/build/**", "**/node_modules/**", "**/.git/**")  // Паттерны исключения
)

/**
 * Конфигурация индексации
 */
data class IndexingConfig(
    val chunkSize: Int,
    val overlapSize: Int,
    val documentsPath: String,
    val projectDocsPath: String? = null,
    val projectReadmePath: String? = null,
    val supportDocsPath: String? = null,
    val codeFiles: CodeFilesConfig = CodeFilesConfig()
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
 * Конфигурация CRM MCP
 */
data class CRMMCPConfig(
    val enabled: Boolean = true,
    val jarPath: String? = null
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
 * Конфигурация CRM
 */
data class CrmConfig(
    val mcp: CRMMCPConfig = CRMMCPConfig()
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
 * Главная конфигурация приложения
 */
data class AppConfig(
    val server: ServerConfig,
    val ollama: OllamaConfig,
    val knowledgeBase: KnowledgeBaseConfig,
    val indexing: IndexingConfig,
    val ai: AIConfig,
    val rag: RAGConfig = RAGConfig(),
    val chat: ChatConfig = ChatConfig(),
    val git: GitConfig = GitConfig(),
    val crm: CrmConfig = CrmConfig()
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
        
        // Конфигурация индексации кода
        val codeFilesMap = indexingMap["codeFiles"] as? Map<String, Any> ?: emptyMap()
        val codeFiles = CodeFilesConfig(
            enabled = (codeFilesMap["enabled"] as? Boolean) ?: false,
            paths = (codeFilesMap["paths"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            extensions = (codeFilesMap["extensions"] as? List<*>)?.mapNotNull { it as? String } 
                ?: listOf(".kt", ".java", ".js", ".ts", ".py"),
            excludePatterns = (codeFilesMap["excludePatterns"] as? List<*>)?.mapNotNull { it as? String }
                ?: listOf("**/build/**", "**/node_modules/**", "**/.git/**")
        )
        
        val indexing = IndexingConfig(
            chunkSize = (indexingMap["chunkSize"] as? Number)?.toInt() ?: 800,
            overlapSize = (indexingMap["overlapSize"] as? Number)?.toInt() ?: 100,
            documentsPath = resolveEnvVar(indexingMap["documentsPath"] as? String ?: "documents"),
            projectDocsPath = indexingMap["projectDocsPath"] as? String,
            projectReadmePath = indexingMap["projectReadmePath"] as? String,
            supportDocsPath = indexingMap["supportDocsPath"] as? String,
            codeFiles = codeFiles
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
        
        // Конфигурация CRM
        val crmMap = serverConfigMap["crm"] as? Map<String, Any> ?: emptyMap()
        val crmMCPMap = crmMap["mcp"] as? Map<String, Any> ?: emptyMap()
        val crmMCP = CRMMCPConfig(
            enabled = (crmMCPMap["enabled"] as? Boolean) ?: true,
            jarPath = crmMCPMap["jarPath"] as? String
        )
        val crm = CrmConfig(mcp = crmMCP)
        
        return AppConfig(
            server = server,
            ollama = ollama,
            knowledgeBase = knowledgeBase,
            indexing = indexing,
            ai = ai,
            rag = rag,
            chat = chat,
            git = git,
            crm = crm
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
     * Находит корень проекта (lesson-22-support-assistant)
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
        
        // Ищем lesson-22-support-assistant вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-22-support-assistant") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            val lessonDir = File(searchDir, "lesson-22-support-assistant")
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
            "Please ensure config/server.yaml exists in lesson-22-support-assistant directory."
        )
    }
}

