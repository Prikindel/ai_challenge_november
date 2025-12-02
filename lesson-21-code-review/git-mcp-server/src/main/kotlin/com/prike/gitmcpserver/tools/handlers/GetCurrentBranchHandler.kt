package com.prike.gitmcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Параметры для получения текущей ветки git (пусто, так как параметров нет)
 */
object GetCurrentBranchParams {
    // Нет параметров
}

/**
 * Обработчик для инструмента get_current_branch
 */
class GetCurrentBranchHandler : ToolHandler<GetCurrentBranchParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetCurrentBranchHandler::class.java)
    
    override fun execute(params: GetCurrentBranchParams): String {
        logger.info("Получение текущей ветки git репозитория")
        
        return getCurrentGitBranch()
    }
    
    override fun prepareResult(request: GetCurrentBranchParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    /**
     * Получает текущую ветку git репозитория
     * Использует команду `git branch --show-current`
     * 
     * @return имя текущей ветки или "unknown" при ошибке
     */
    private fun getCurrentGitBranch(): String {
        try {
            // Проверяем, что мы в git-репозитории
            val gitDir = File(".git")
            val parentGitDir = File("..", ".git")
            
            if (!gitDir.exists() && !parentGitDir.exists()) {
                logger.warn("Not in a git repository")
                return "unknown"
            }
            
            // Выполняем команду git branch --show-current
            val processBuilder = ProcessBuilder("git", "branch", "--show-current")
            processBuilder.directory(File(System.getProperty("user.dir")))
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                logger.warn("Git command failed with exit code: $exitCode")
                return "unknown"
            }
            
            if (output.isBlank()) {
                logger.warn("Git branch command returned empty output")
                return "unknown"
            }
            
            logger.info("Current git branch: $output")
            return output
        } catch (e: java.io.IOException) {
            // Git не установлен или команда не найдена
            logger.warn("Git command not found or failed: ${e.message}")
            return "unknown"
        } catch (e: Exception) {
            logger.error("Unexpected error getting git branch: ${e.message}", e)
            return "unknown"
        }
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON (для этого инструмента параметров нет)
         */
        fun parseParams(arguments: kotlinx.serialization.json.JsonObject): GetCurrentBranchParams {
            // Нет параметров для этого инструмента
            return GetCurrentBranchParams
        }
    }
}

