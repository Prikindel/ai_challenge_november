package com.prike.gitmcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Параметры для списка файлов в директории
 */
data class ListDirectoryParams(
    val path: String = "."
)

/**
 * Обработчик для инструмента list_directory
 */
class ListDirectoryHandler(
    private val projectRoot: File
) : ToolHandler<ListDirectoryParams, String>() {
    
    override val logger = LoggerFactory.getLogger(ListDirectoryHandler::class.java)
    
    override fun execute(params: ListDirectoryParams): String {
        logger.info("Список файлов в директории: ${params.path}")
        
        return listDirectory(params.path)
    }
    
    override fun prepareResult(request: ListDirectoryParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    /**
     * Возвращает список файлов и директорий
     * 
     * @param dirPath путь к директории (относительно projectRoot)
     * @return список файлов и директорий в формате JSON-подобной строки
     */
    private fun listDirectory(dirPath: String): String {
        try {
            val dir = if (dirPath == "." || dirPath.isEmpty()) {
                projectRoot
            } else {
                File(projectRoot, dirPath)
            }
            
            // Проверка безопасности
            if (!dir.canonicalPath.startsWith(projectRoot.canonicalPath)) {
                logger.warn("Attempted to list directory outside project root: $dirPath")
                return "Ошибка: Доступ к директории за пределами корня проекта запрещён"
            }
            
            if (!dir.exists()) {
                logger.warn("Directory not found: $dirPath")
                return "Ошибка: Директория не найдена: $dirPath"
            }
            
            if (!dir.isDirectory) {
                logger.warn("Path is not a directory: $dirPath")
                return "Ошибка: Указанный путь не является директорией: $dirPath"
            }
            
            val filesArray = dir.listFiles() ?: emptyArray()
            val files = filesArray.sortedBy { it.name }
            
            val result = buildString {
                appendLine("Директория: ${dir.relativeTo(projectRoot).path}")
                appendLine("Всего элементов: ${files.size}")
                appendLine()
                
                // Разделяем на директории и файлы
                val directories = files.filter { file: File -> file.isDirectory }
                val fileList = files.filter { file: File -> file.isFile }
                
                if (directories.isNotEmpty()) {
                    appendLine("Директории:")
                    directories.forEach { dir: File ->
                        appendLine("  📁 ${dir.name}/")
                    }
                    appendLine()
                }
                
                if (fileList.isNotEmpty()) {
                    appendLine("Файлы:")
                    fileList.forEach { file: File ->
                        val size = if (file.length() < 1024) {
                            "${file.length()} B"
                        } else if (file.length() < 1024 * 1024) {
                            "${file.length() / 1024} KB"
                        } else {
                            "${file.length() / (1024 * 1024)} MB"
                        }
                        appendLine("  📄 ${file.name} ($size)")
                    }
                }
            }
            
            logger.info("Successfully listed directory: $dirPath (${files.size} items)")
            return result
            
        } catch (e: SecurityException) {
            logger.error("Security error listing directory: ${e.message}", e)
            return "Ошибка безопасности при чтении директории: ${e.message}"
        } catch (e: Exception) {
            logger.error("Error listing directory: ${e.message}", e)
            return "Ошибка при чтении директории: ${e.message}"
        }
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: kotlinx.serialization.json.JsonObject): ListDirectoryParams {
            val path = arguments["path"]?.jsonPrimitive?.content ?: "."
            
            return ListDirectoryParams(path = path)
        }
    }
}

