package com.prike.ragmcpserver.tools.handlers

import com.prike.ragmcpserver.config.RagMCPConfig
import com.prike.ragmcpserver.domain.service.DocumentIndexer
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Параметры для индексации документации проекта
 */
data class RagIndexProjectDocsParams(
    val projectDocsPath: String? = null,  // Если null, используется из конфигурации
    val projectReadmePath: String? = null  // Если null, используется из конфигурации
)

/**
 * Результат индексации документации проекта
 */
data class RagIndexProjectDocsResult(
    val indexedCount: Int,
    val totalChunks: Int,
    val message: String
)

/**
 * Обработчик для инструмента rag_index_project_docs
 */
class RagIndexProjectDocsHandler(
    private val documentIndexer: DocumentIndexer,
    private val lessonRoot: File,
    private val config: RagMCPConfig
) : ToolHandler<RagIndexProjectDocsParams, RagIndexProjectDocsResult>() {
    
    override val logger = LoggerFactory.getLogger(RagIndexProjectDocsHandler::class.java)
    
    override suspend fun execute(params: RagIndexProjectDocsParams): RagIndexProjectDocsResult {
        val projectDocsPath = params.projectDocsPath ?: config.indexing.projectDocsPath
        val projectReadmePath = params.projectReadmePath ?: config.indexing.projectReadmePath
        
        logger.info("RAG index project docs: docsPath='$projectDocsPath', readmePath='$projectReadmePath'")
        
        var totalIndexed = 0
        var totalChunks = 0
        val messages = mutableListOf<String>()
        
        return try {
            // Индексируем папку project/docs
            if (projectDocsPath != null) {
                // Если путь абсолютный, используем его напрямую, иначе относительно lessonRoot
                val docsDir = if (File(projectDocsPath).isAbsolute) {
                    File(projectDocsPath)
                } else {
                    File(lessonRoot, projectDocsPath)
                }
                if (docsDir.exists() && docsDir.isDirectory) {
                    val results = documentIndexer.indexDirectory(docsDir.absolutePath)
                    val successCount = results.count { it.success }
                    val chunksCount = results.sumOf { it.chunksCount }
                    totalIndexed += successCount
                    totalChunks += chunksCount
                    messages.add("Папка ${docsDir.absolutePath}: $successCount документов, $chunksCount чанков")
                } else {
                    messages.add("Папка ${docsDir.absolutePath} не найдена")
                }
            }
            
            // Индексируем project/README.md
            if (projectReadmePath != null) {
                // Если путь абсолютный, используем его напрямую, иначе относительно lessonRoot
                val readmeFile = if (File(projectReadmePath).isAbsolute) {
                    File(projectReadmePath)
                } else {
                    File(lessonRoot, projectReadmePath)
                }
                if (readmeFile.exists() && readmeFile.isFile) {
                    val result = documentIndexer.indexDocument(readmeFile.absolutePath)
                    if (result.success) {
                        totalIndexed += 1
                        totalChunks += result.chunksCount
                        messages.add("Файл ${readmeFile.absolutePath}: ${result.chunksCount} чанков")
                    } else {
                        messages.add("Ошибка индексации ${readmeFile.absolutePath}: ${result.error}")
                    }
                } else {
                    messages.add("Файл ${readmeFile.absolutePath} не найден")
                }
            }
            
            RagIndexProjectDocsResult(
                indexedCount = totalIndexed,
                totalChunks = totalChunks,
                message = messages.joinToString("\n")
            )
        } catch (e: Exception) {
            logger.error("Failed to index project docs: ${e.message}", e)
            RagIndexProjectDocsResult(
                indexedCount = 0,
                totalChunks = 0,
                message = "Ошибка индексации: ${e.message}"
            )
        }
    }
    
    override fun prepareResult(request: RagIndexProjectDocsParams, result: RagIndexProjectDocsResult): TextContent {
        return TextContent(text = result.message)
    }
    
    companion object {
        fun parseParams(arguments: JsonObject): RagIndexProjectDocsParams {
            val projectDocsPath = arguments["projectDocsPath"]?.jsonPrimitive?.content
            val projectReadmePath = arguments["projectReadmePath"]?.jsonPrimitive?.content
            return RagIndexProjectDocsParams(
                projectDocsPath = projectDocsPath,
                projectReadmePath = projectReadmePath
            )
        }
    }
}

