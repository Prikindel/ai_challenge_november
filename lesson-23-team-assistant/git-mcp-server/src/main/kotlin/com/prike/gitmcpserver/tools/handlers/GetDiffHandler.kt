package com.prike.gitmcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Параметры для получения diff между ветками
 */
data class GetDiffParams(
    val base: String,  // базовая ветка (например, "main" или "origin/main")
    val head: String   // целевая ветка (например, "feature-branch" или "HEAD")
)

/**
 * Обработчик для инструмента get_diff
 */
class GetDiffHandler : ToolHandler<GetDiffParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetDiffHandler::class.java)
    
    override fun execute(params: GetDiffParams): String {
        logger.info("Получение diff между ветками: ${params.base}..${params.head}")
        
        return getDiff(params.base, params.head)
    }
    
    override fun prepareResult(request: GetDiffParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    /**
     * Получает diff между двумя ветками git
     * Использует команду `git diff base..head`
     * 
     * @param base базовая ветка
     * @param head целевая ветка
     * @return diff или сообщение об ошибке
     */
    private fun getDiff(base: String, head: String): String {
        try {
            // Проверяем, что мы в git-репозитории
            val gitDir = File(".git")
            val parentGitDir = File("..", ".git")
            
            if (!gitDir.exists() && !parentGitDir.exists()) {
                logger.warn("Not in a git repository")
                return "Ошибка: Не находимся в git-репозитории"
            }
            
            // Выполняем команду git diff base..head
            val processBuilder = ProcessBuilder("git", "diff", "$base..$head")
            processBuilder.directory(File(System.getProperty("user.dir")))
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                logger.warn("Git diff command failed with exit code: $exitCode. Error: $errorOutput")
                return "Ошибка: Не удалось получить diff. Возможно, ветки не существуют или не найдены. Ошибка: $errorOutput"
            }
            
            if (output.isBlank()) {
                logger.info("No differences found between $base and $head")
                return "Нет различий между ветками $base и $head"
            }
            
            logger.info("Successfully retrieved diff between $base and $head (${output.length} chars)")
            return output
            
        } catch (e: java.io.IOException) {
            // Git не установлен или команда не найдена
            logger.warn("Git command not found or failed: ${e.message}")
            return "Ошибка: Git не установлен или команда не найдена: ${e.message}"
        } catch (e: Exception) {
            logger.error("Unexpected error getting git diff: ${e.message}", e)
            return "Ошибка при получении diff: ${e.message}"
        }
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: kotlinx.serialization.json.JsonObject): GetDiffParams {
            val base = arguments["base"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Parameter 'base' is required")
            
            val head = arguments["head"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Parameter 'head' is required")
            
            return GetDiffParams(base = base, head = head)
        }
    }
}

