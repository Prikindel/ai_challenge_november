package com.prike.mcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Обработчик для инструмента save_to_file
 * Сохраняет содержимое в файл
 */
class SaveToFileHandler(
    private val basePath: String
) : ToolHandler<SaveToFileHandler.Params, SaveToFileHandler.SaveResult>() {

    override val logger = LoggerFactory.getLogger(SaveToFileHandler::class.java)

    override fun execute(params: Params): SaveResult {
        logger.info("Сохранение файла: ${params.filePath} (размер: ${params.content.length} символов)")
        
        val file = resolveFilePath(params.filePath)
        
        // Создаём директорию, если её нет
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
            logger.info("Создана директория: ${parentDir.absolutePath}")
        }
        
        // Сохраняем содержимое
        file.writeText(params.content, Charsets.UTF_8)
        
        val fileSize = file.length()
        logger.info("Файл сохранён: ${file.absolutePath} (размер: $fileSize байт)")
        
        return SaveResult(
            success = true,
            filePath = file.absolutePath,
            size = fileSize.toInt()
        )
    }

    override fun prepareResult(request: Params, result: SaveResult): TextContent {
        val resultJson = buildJsonObject {
            put("success", result.success)
            put("filePath", result.filePath)
            put("size", result.size)
        }
        
        return TextContent(text = resultJson.toString())
    }
    
    /**
     * Разрешить путь к файлу относительно basePath
     */
    private fun resolveFilePath(filePath: String): File {
        val file = File(filePath)
        
        // Если путь абсолютный, используем его
        if (file.isAbsolute) {
            return file
        }
        
        // Если путь относительный, используем basePath
        val baseDir = resolveBasePath()
        return File(baseDir, filePath)
    }
    
    /**
     * Разрешить базовый путь
     */
    private fun resolveBasePath(): File {
        val base = File(basePath)
        
        // Если путь абсолютный, используем его
        if (base.isAbsolute) {
            return base
        }
        
        // Если путь относительный, пытаемся найти относительно конфигурационного файла
        var currentDir = File(System.getProperty("user.dir"))
        
        // Если запущены из reporting-mcp-server директории
        if (currentDir.name == "reporting-mcp-server") {
            return File(currentDir, basePath)
        }
        
        // Если запущены из корня урока
        if (currentDir.name == "lesson-14-orchestration") {
            return File(currentDir, basePath)
        }
        
        // Ищем lesson-14-orchestration вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-14-orchestration") {
                return File(searchDir, basePath)
            }
            
            val lessonDir = File(searchDir, "lesson-14-orchestration")
            if (lessonDir.exists()) {
                return File(lessonDir, basePath)
            }
            
            searchDir = searchDir.parentFile
        }
        
        // Fallback: используем относительно текущей директории
        return base
    }

    data class Params(
        val filePath: String,
        val content: String
    )
    
    data class SaveResult(
        val success: Boolean,
        val filePath: String,
        val size: Int
    )
}

