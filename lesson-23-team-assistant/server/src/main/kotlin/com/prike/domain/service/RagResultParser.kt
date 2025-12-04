package com.prike.domain.service

import com.prike.domain.model.RetrievedChunk
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Парсер результатов RAG MCP инструментов
 */
class RagResultParser {
    private val logger = LoggerFactory.getLogger(RagResultParser::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    /**
     * Парсит результат RAG поиска из JSON строки
     * 
     * @param jsonResult JSON строка с результатами поиска
     * @return список извлеченных чанков
     */
    fun parseRagSearchResult(jsonResult: String): List<RetrievedChunk> {
        return try {
            val jsonElement = json.parseToJsonElement(jsonResult)
            if (jsonElement !is JsonObject) {
                logger.warn("RAG search result is not a JSON object, trying to parse as text")
                return parseTextFormat(jsonResult)
            }
            
            val chunksArray = jsonElement["chunks"]?.jsonArray ?: return emptyList()
            
            chunksArray.mapNotNull { chunkJson ->
                if (chunkJson !is JsonObject) return@mapNotNull null
                
                RetrievedChunk(
                    chunkId = chunkJson["chunkId"]?.jsonPrimitive?.content ?: "",
                    documentPath = chunkJson["documentPath"]?.jsonPrimitive?.contentOrNull,
                    documentTitle = chunkJson["documentTitle"]?.jsonPrimitive?.contentOrNull,
                    content = chunkJson["content"]?.jsonPrimitive?.content ?: "",
                    similarity = chunkJson["similarity"]?.jsonPrimitive?.floatOrNull ?: 0.0f,
                    chunkIndex = 0
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse RAG search result as JSON, trying text format: ${e.message}")
            parseTextFormat(jsonResult)
        }
    }
    
    /**
     * Парсит результат в текстовом формате (fallback)
     */
    private fun parseTextFormat(textResult: String): List<RetrievedChunk> {
        // Если формат текстовый, возвращаем пустой список
        // В будущем можно добавить парсинг текстового формата
        logger.debug("Parsing text format RAG result (not implemented, returning empty list)")
        return emptyList()
    }
}

