package com.prike.ragmcpserver.domain.service

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Загрузчик документов из файловой системы
 */
class DocumentLoader(
    private val basePath: File? = null
) {
    private val logger = LoggerFactory.getLogger(DocumentLoader::class.java)
    
    /**
     * Разрешает путь относительно базового пути
     */
    private fun resolvePath(path: String): File {
        val file = File(path)
        // Если путь абсолютный, используем его как есть
        if (file.isAbsolute) {
            return file
        }
        // Если есть базовый путь, разрешаем относительно него
        return if (basePath != null) {
            File(basePath, path)
        } else {
            file
        }
    }
    
    /**
     * Загружает документ из файла
     * 
     * @param filePath путь к файлу (относительный или абсолютный)
     * @return содержимое файла
     * @throws DocumentLoadException если не удалось загрузить файл
     */
    fun loadDocument(filePath: String): LoadedDocument {
        val file = resolvePath(filePath)
        
        if (!file.exists()) {
            throw DocumentLoadException("File not found: $filePath")
        }
        
        if (!file.isFile) {
            throw DocumentLoadException("Path is not a file: $filePath")
        }
        
        if (!file.canRead()) {
            throw DocumentLoadException("Cannot read file: $filePath")
        }
        
        try {
            val content = file.readText(Charsets.UTF_8)
            val title = extractTitle(content, file.name)
            
            logger.debug("Loaded document: $filePath (${content.length} chars)")
            
            return LoadedDocument(
                filePath = filePath,
                title = title,
                content = content
            )
        } catch (e: Exception) {
            logger.error("Failed to load document: $filePath", e)
            throw DocumentLoadException("Failed to load document: ${e.message}", e)
        }
    }
    
    /**
     * Загружает все документы из директории
     * 
     * @param directoryPath путь к директории
     * @param extensions список расширений файлов для загрузки (по умолчанию .md)
     * @return список загруженных документов
     */
    fun loadDocumentsFromDirectory(
        directoryPath: String,
        extensions: List<String> = listOf(".md", ".markdown")
    ): List<LoadedDocument> {
        val directory = resolvePath(directoryPath)
        
        if (!directory.exists()) {
            throw DocumentLoadException("Directory not found: $directoryPath")
        }
        
        if (!directory.isDirectory) {
            throw DocumentLoadException("Path is not a directory: $directoryPath")
        }
        
        val documents = mutableListOf<LoadedDocument>()
        
        Files.walk(Paths.get(directory.absolutePath))
            .filter { Files.isRegularFile(it) }
            .filter { path ->
                val fileName = path.fileName.toString().lowercase()
                extensions.any { fileName.endsWith(it.lowercase()) }
            }
            .forEach { path ->
                try {
                    // Загружаем документ по абсолютному пути
                    val absolutePath = path.toAbsolutePath().toString()
                    val document = loadDocument(absolutePath)
                    
                    // Сохраняем относительный путь для сохранения в БД
                    val relativePath = if (basePath != null) {
                        try {
                            basePath.toPath().relativize(path).toString()
                        } catch (e: Exception) {
                            // Если не удалось получить относительный путь, используем абсолютный
                            absolutePath
                        }
                    } else {
                        absolutePath
                    }
                    
                    // Используем относительный путь в документе
                    documents.add(document.copy(filePath = relativePath))
                } catch (e: DocumentLoadException) {
                    logger.warn("Skipping file ${path}: ${e.message}")
                }
            }
        
        logger.info("Loaded ${documents.size} documents from ${directory.absolutePath}")
        return documents
    }
    
    /**
     * Извлекает заголовок из содержимого документа
     * Ищет первый заголовок Markdown (# Заголовок)
     */
    private fun extractTitle(content: String, fileName: String): String {
        // Ищем первый заголовок Markdown
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim()
            }
            if (trimmed.startsWith("## ")) {
                return trimmed.substring(3).trim()
            }
        }
        
        // Если заголовок не найден, используем имя файла без расширения
        return fileName.substringBeforeLast(".")
    }
}

/**
 * Загруженный документ
 */
data class LoadedDocument(
    val filePath: String,
    val title: String,
    val content: String
)

/**
 * Исключение при загрузке документа
 */
class DocumentLoadException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

