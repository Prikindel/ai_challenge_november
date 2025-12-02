package com.prike.gitmcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Параметры для получения списка изменённых файлов
 */
data class GetChangedFilesParams(
    val base: String,  // базовая ветка
    val head: String   // целевая ветка
)

/**
 * Обработчик для инструмента get_changed_files
 */
class GetChangedFilesHandler : ToolHandler<GetChangedFilesParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetChangedFilesHandler::class.java)
    
    override fun execute(params: GetChangedFilesParams): String {
        logger.info("Получение списка изменённых файлов между ветками: ${params.base}..${params.head}")
        
        return getChangedFiles(params.base, params.head)
    }
    
    override fun prepareResult(request: GetChangedFilesParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    /**
     * Получает список изменённых файлов между двумя ветками git
     * Использует команду `git diff --name-status base..head`
     * 
     * @param base базовая ветка
     * @param head целевая ветка
     * @return список изменённых файлов (по одному на строку) или сообщение об ошибке
     */
    private fun getChangedFiles(base: String, head: String): String {
        try {
            // Проверяем, что мы в git-репозитории
            val gitDir = File(".git")
            val parentGitDir = File("..", ".git")
            
            if (!gitDir.exists() && !parentGitDir.exists()) {
                logger.warn("Not in a git repository")
                return "Ошибка: Не находимся в git-репозитории"
            }
            
            // Выполняем команду git diff --name-status base..head
            val processBuilder = ProcessBuilder("git", "diff", "--name-status", "$base..$head")
            processBuilder.directory(File(System.getProperty("user.dir")))
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                logger.warn("Git diff --name-status command failed with exit code: $exitCode. Error: $errorOutput")
                return "Ошибка: Не удалось получить список изменённых файлов. Возможно, ветки не существуют или не найдены. Ошибка: $errorOutput"
            }
            
            if (output.isBlank()) {
                logger.info("No changed files found between $base and $head")
                return "Нет изменённых файлов между ветками $base и $head"
            }
            
            // Форматируем вывод: каждая строка содержит статус и путь к файлу
            // Статусы: A (добавлен), M (изменён), D (удалён), R (переименован), C (скопирован)
            val files = output.lines().filter { it.isNotBlank() }
            logger.info("Found ${files.size} changed files between $base and $head")
            
            return files.joinToString("\n")
            
        } catch (e: java.io.IOException) {
            // Git не установлен или команда не найдена
            logger.warn("Git command not found or failed: ${e.message}")
            return "Ошибка: Git не установлен или команда не найдена: ${e.message}"
        } catch (e: Exception) {
            logger.error("Unexpected error getting changed files: ${e.message}", e)
            return "Ошибка при получении списка изменённых файлов: ${e.message}"
        }
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: kotlinx.serialization.json.JsonObject): GetChangedFilesParams {
            val base = arguments["base"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Parameter 'base' is required")
            
            val head = arguments["head"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Parameter 'head' is required")
            
            return GetChangedFilesParams(base = base, head = head)
        }
    }
}

