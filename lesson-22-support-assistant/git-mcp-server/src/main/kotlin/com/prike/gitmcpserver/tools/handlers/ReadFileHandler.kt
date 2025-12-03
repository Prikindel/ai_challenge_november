package com.prike.gitmcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Параметры для чтения файла
 */
data class ReadFileParams(
    val path: String
)

/**
 * Обработчик для инструмента read_file
 */
class ReadFileHandler(
    private val projectRoot: File
) : ToolHandler<ReadFileParams, String>() {
    
    override val logger = LoggerFactory.getLogger(ReadFileHandler::class.java)
    
    override fun execute(params: ReadFileParams): String {
        logger.info("Чтение файла: ${params.path}")
        
        return readFile(params.path)
    }
    
    override fun prepareResult(request: ReadFileParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    /**
     * Читает содержимое файла
     * 
     * @param filePath путь к файлу (относительно projectRoot)
     * @return содержимое файла или сообщение об ошибке
     */
    private fun readFile(filePath: String): String {
        try {
            val file = File(projectRoot, filePath)
            
            // Проверка безопасности - не позволяем выходить за пределы projectRoot
            if (!file.canonicalPath.startsWith(projectRoot.canonicalPath)) {
                logger.warn("Attempted to read file outside project root: $filePath")
                return "Ошибка: Доступ к файлу за пределами корня проекта запрещён"
            }
            
            if (!file.exists()) {
                logger.warn("File not found: $filePath")
                return "Ошибка: Файл не найден: $filePath"
            }
            
            if (!file.isFile) {
                logger.warn("Path is not a file: $filePath")
                return "Ошибка: Указанный путь не является файлом: $filePath"
            }
            
            // Ограничение размера файла (максимум 100KB)
            if (file.length() > 100 * 1024) {
                logger.warn("File too large: $filePath (${file.length()} bytes)")
                return "Ошибка: Файл слишком большой (${file.length()} байт). Максимальный размер: 100KB"
            }
            
            val content = file.readText(Charsets.UTF_8)
            logger.info("Successfully read file: $filePath (${content.length} chars)")
            return content
            
        } catch (e: SecurityException) {
            logger.error("Security error reading file: ${e.message}", e)
            return "Ошибка безопасности при чтении файла: ${e.message}"
        } catch (e: Exception) {
            logger.error("Error reading file: ${e.message}", e)
            return "Ошибка при чтении файла: ${e.message}"
        }
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: kotlinx.serialization.json.JsonObject): ReadFileParams {
            val path = arguments["path"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Parameter 'path' is required")
            
            return ReadFileParams(path = path)
        }
    }
}

