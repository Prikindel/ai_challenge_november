package com.prike.domain.koog.tools

import ai.koog.agents.core.tools.SimpleTool
import com.prike.domain.service.RagMCPService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory

/**
 * Аргументы для инструмента search_support_docs
 */
@Serializable
data class SearchSupportDocsArgs(
    val query: String,
    val topK: Int = 5
)

/**
 * Koog инструмент для поиска в документации поддержки через RAG
 */
class SearchSupportDocsTool(private val ragMCPService: RagMCPService?) : SimpleTool<SearchSupportDocsArgs>() {
    private val logger = LoggerFactory.getLogger(SearchSupportDocsTool::class.java)
    
    override val name = "search_support_docs"
    override val description = "Поиск ответов в документации поддержки (FAQ, troubleshooting, user guide, auth guide). Используй этот инструмент для поиска информации, которая поможет ответить на вопрос пользователя."
    
    override val argsSerializer = serializer<SearchSupportDocsArgs>()
    
    override suspend fun doExecute(args: SearchSupportDocsArgs): String {
        return try {
            if (ragMCPService == null) {
                return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("RAG MCP service is not available"))))
            }
            
            val mcpArguments = buildJsonObject {
                put("query", JsonPrimitive(args.query))
                put("topK", JsonPrimitive(args.topK))
                put("filter", buildJsonObject {
                    put("path", JsonPrimitive("project/docs/support/"))
                })
            }
            
            val result = ragMCPService.callTool("rag_search_project_docs", mcpArguments)
            
            // Парсим результат от RAG MCP
            val jsonObj = Json.parseToJsonElement(result) as? JsonObject
                ?: return Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Invalid response from RAG MCP"))))
            
            val chunks = jsonObj["chunks"]?.jsonArray ?: JsonArray(emptyList())
            
            val response = buildJsonObject {
                put("results", chunks)
                put("count", JsonPrimitive(chunks.size))
            }
            
            Json.encodeToString(response)
        } catch (e: Exception) {
            logger.error("Error in search_support_docs tool", e)
            Json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("Failed to search support docs: ${e.message}"))))
        }
    }
}
