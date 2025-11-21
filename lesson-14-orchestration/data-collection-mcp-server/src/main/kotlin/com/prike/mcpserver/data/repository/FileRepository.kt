package com.prike.mcpserver.data.repository

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Репозиторий для чтения файлов
 */
class FileRepository(
    private val basePath: String
) {
    private val logger = LoggerFactory.getLogger(FileRepository::class.java)
    
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
        
        // Если запущены из data-collection-mcp-server директории
        if (currentDir.name == "data-collection-mcp-server") {
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
    
    /**
     * Прочитать содержимое файла
     * @param filePath путь к файлу (относительный или абсолютный)
     * @return содержимое файла как текст
     */
    fun readFileContent(filePath: String): String {
        val file = resolveFilePath(filePath)
        
        if (!file.exists()) {
            throw IllegalArgumentException("Файл не найден: ${file.absolutePath}")
        }
        
        if (!file.isFile) {
            throw IllegalArgumentException("Путь не является файлом: ${file.absolutePath}")
        }
        
        logger.info("Чтение файла: ${file.absolutePath}")
        return file.readText(Charsets.UTF_8)
    }
}

