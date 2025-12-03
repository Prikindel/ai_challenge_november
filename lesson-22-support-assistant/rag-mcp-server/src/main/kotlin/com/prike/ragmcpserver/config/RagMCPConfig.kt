package com.prike.ragmcpserver.config

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
    val projectReadmePath: String? = null,
    val supportDocsPath: String? = null
)

/**
 * Конфигурация AI (для реранкера)
 */
data class AIConfig(
    val provider: String = "openrouter",
    val apiKey: String,
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000
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
 * Конфигурация фильтрации RAG
 */
data class RAGFilterConfig(
    val enabled: Boolean = false,
    val type: String = "none",  // "none" | "threshold" | "reranker" | "hybrid"
    val reranker: RerankerConfig = RerankerConfig()
)

/**
 * Конфигурация RAG
 */
data class RAGConfig(
    val topK: Int = 5,
    val minSimilarity: Float = 0.4f,
    val filter: RAGFilterConfig = RAGFilterConfig()
)

/**
 * Главная конфигурация RAG MCP сервера
 */
data class RagMCPConfig(
    val ollama: OllamaConfig,
    val knowledgeBase: KnowledgeBaseConfig,
    val indexing: IndexingConfig,
    val rag: RAGConfig = RAGConfig(),
    val ai: AIConfig? = null  // Для реранкера (опционально)
) {
    companion object {
        /**
         * Загружает конфигурацию из переменных окружения
         */
        fun fromEnvironment(): RagMCPConfig {
            val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
            val ollamaModel = System.getenv("OLLAMA_MODEL") ?: "nomic-embed-text"
            val ollamaTimeout = System.getenv("OLLAMA_TIMEOUT")?.toLongOrNull() ?: 120000L
            
            val dbPath = System.getenv("KNOWLEDGE_BASE_DB_PATH") ?: "data/knowledge_base.db"
            
            val chunkSize = System.getenv("CHUNK_SIZE")?.toIntOrNull() ?: 300
            val overlapSize = System.getenv("OVERLAP_SIZE")?.toIntOrNull() ?: 50
            val documentsPath = System.getenv("DOCUMENTS_PATH") ?: "documents"
            val projectDocsPath = System.getenv("PROJECT_DOCS_PATH")
            val projectReadmePath = System.getenv("PROJECT_README_PATH")
            val supportDocsPath = System.getenv("SUPPORT_DOCS_PATH")
            
            val topK = System.getenv("RAG_TOP_K")?.toIntOrNull() ?: 5
            val minSimilarity = System.getenv("RAG_MIN_SIMILARITY")?.toFloatOrNull() ?: 0.4f
            
            // Конфигурация AI для реранкера (опционально)
            val aiConfig = System.getenv("OPENAI_API_KEY")?.let { apiKey ->
                AIConfig(
                    provider = System.getenv("AI_PROVIDER") ?: "openrouter",
                    apiKey = apiKey,
                    model = System.getenv("AI_MODEL") ?: "gpt-4o-mini",
                    temperature = System.getenv("AI_TEMPERATURE")?.toDoubleOrNull() ?: 0.7,
                    maxTokens = System.getenv("AI_MAX_TOKENS")?.toIntOrNull() ?: 2000
                )
            }
            
            return RagMCPConfig(
                ollama = OllamaConfig(
                    baseUrl = ollamaBaseUrl,
                    model = ollamaModel,
                    timeout = ollamaTimeout
                ),
                knowledgeBase = KnowledgeBaseConfig(
                    databasePath = dbPath
                ),
                indexing = IndexingConfig(
                    chunkSize = chunkSize,
                    overlapSize = overlapSize,
                    documentsPath = documentsPath,
                    projectDocsPath = projectDocsPath,
                    projectReadmePath = projectReadmePath,
                    supportDocsPath = supportDocsPath
                ),
                rag = RAGConfig(
                    topK = topK,
                    minSimilarity = minSimilarity
                ),
                ai = aiConfig
            )
        }
    }
}

