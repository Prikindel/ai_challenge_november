package com.prike.domain.agent

import com.prike.config.MCPServerConfig
import com.prike.data.repository.MCPRepository
import com.prike.data.dto.Tool
import com.prike.data.dto.Resource
import com.prike.domain.exception.MCPException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class MCPConnectionAgent(
    private val mcpRepository: MCPRepository
) {
    private val logger = LoggerFactory.getLogger(MCPConnectionAgent::class.java)
    private var currentProcess: Process? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    suspend fun connectToServer(
        serverConfig: MCPServerConfig
    ): ConnectionResult {
        return try {
            // Для stdio транспорта: запусти процесс и получи inputStream/outputStream
            if (serverConfig.type == "stdio") {
                val process = startStdioProcess(serverConfig)
                currentProcess = process
                
                // Проверяем, что процесс ещё жив
                if (!process.isAlive) {
                    val exitCode = process.exitValue()
                    throw MCPException("Process died before connection, exit code: $exitCode")
                }
                
                // Запускаем чтение stderr в фоне для логирования
                // Это нужно, чтобы логи сервера не попадали в stdout (который используется для MCP протокола)
                scope.launch {
                    readStderr(process.errorStream)
                }
                
                // Используем только stdout для MCP протокола
                val inputStream = process.inputStream
                val outputStream = process.outputStream
                
                // Подключаемся к серверу
                mcpRepository.connectToServer(inputStream, outputStream)
                
                // Проверяем, что процесс всё ещё жив после подключения
                if (!process.isAlive) {
                    val exitCode = process.exitValue()
                    throw MCPException("Process died after connection, exit code: $exitCode")
                }
                
                // Даём время на полную инициализацию MCP протокола
                delay(2000)
                
                // Получаем инструменты и ресурсы
                val tools = mcpRepository.getTools()
                
                // Ресурсы опциональны - некоторые серверы их не поддерживают
                val resources = try {
                    mcpRepository.getResources()
                } catch (e: Exception) {
                    logger.debug("Server does not support resources: ${e.message}")
                    emptyList()
                }
                
                ConnectionResult.Success(
                    tools = tools,
                    resources = resources,
                    serverName = serverConfig.name,
                    serverDescription = serverConfig.description
                )
            } else {
                throw MCPException("Unsupported transport type: ${serverConfig.type}")
            }
        } catch (e: Exception) {
            disconnect()
            ConnectionResult.Error(
                message = "Failed to connect: ${e.message}",
                cause = e
            )
        }
    }
    
    private fun startStdioProcess(config: MCPServerConfig): Process {
        val command = mutableListOf<String>()
        command.add(config.command ?: throw MCPException("Command not specified"))
        config.args?.let { command.addAll(it) }
        
        val processBuilder = ProcessBuilder(command)
        
        // Устанавливаем рабочую директорию на корень урока
        val lessonRoot = findLessonRoot()
        processBuilder.directory(File(lessonRoot))
        
        // НЕ перенаправляем stderr в stdout - это важно!
        // MCP протокол использует stdout для JSON-RPC сообщений
        // stderr будет читаться отдельно для логирования
        processBuilder.redirectErrorStream(false)
        
        // Пытаемся отключить логирование в stdout через переменные окружения
        val env = processBuilder.environment()
        env["LOG_LEVEL"] = "ERROR"
        env["QUIET"] = "true"
        env["SILENT"] = "true"
        
        val process = processBuilder.start()
        
        // Проверяем, что процесс запустился
        if (!process.isAlive) {
            val exitCode = process.exitValue()
            throw MCPException("Process exited immediately with code: $exitCode")
        }
        
        return process
    }
    
    suspend fun callTool(toolName: String, arguments: Map<String, Any>?): String {
        return mcpRepository.callTool(toolName, arguments)
    }
    
    suspend fun disconnect() {
        try {
            mcpRepository.disconnect()
        } catch (e: Exception) {
            // Игнорируем ошибки при отключении
        }
        
        currentProcess?.let { process ->
            try {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            } catch (e: Exception) {
                // Игнорируем ошибки при завершении процесса
            }
        }
        currentProcess = null
    }
    
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = java.io.File(currentDir)
        
        // Если мы в папке server/, то корень урока - это родительская директория
        if (dir.name == "server") {
            val parent = dir.parentFile
            if (parent != null && parent.name.matches(Regex("lesson-\\d+.*"))) {
                return parent.absolutePath
            }
        }
        
        // Приоритет: ищем конкретно lesson-10-mcp-connection
        val lesson10Path = File(currentDir, "lesson-10-mcp-connection")
        if (lesson10Path.exists() && lesson10Path.isDirectory) {
            return lesson10Path.absolutePath
        }
        
        // Если текущая директория - это lesson-10-mcp-connection
        if (dir.name == "lesson-10-mcp-connection" && dir.isDirectory) {
            return dir.absolutePath
        }
        
        // Идем вверх по директориям, ищем lesson-10-mcp-connection
        var searchDir = dir
        while (searchDir != null) {
            if (searchDir.name == "lesson-10-mcp-connection" && searchDir.isDirectory) {
                return searchDir.absolutePath
            }
            
            // Проверяем поддиректории текущей директории
            searchDir.listFiles()?.firstOrNull { 
                it.isDirectory && it.name == "lesson-10-mcp-connection"
            }?.let { 
                return it.absolutePath 
            }
            
            val parent = searchDir.parentFile
            if (parent == null || parent == searchDir) {
                break
            }
            searchDir = parent
        }
        
        // Fallback: ищем любой урок с client/ внутри
        searchDir = dir
        while (searchDir != null) {
            if (searchDir.name.matches(Regex("lesson-\\d+.*")) && searchDir.isDirectory) {
                val clientDir = File(searchDir, "client")
                if (clientDir.exists()) {
                    return searchDir.absolutePath
                }
            }
            
            val parent = searchDir.parentFile
            if (parent == null || parent == searchDir) {
                break
            }
            searchDir = parent
        }
        
        logger.warn("Could not find lesson root, using current dir: $currentDir")
        return currentDir
    }
    
    /**
     * Читает stderr процесса и логирует его
     * Это нужно, чтобы логи сервера не попадали в stdout (который используется для MCP протокола)
     */
    private suspend fun readStderr(errorStream: InputStream) {
        try {
            val reader = BufferedReader(InputStreamReader(errorStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Логи сервера игнорируем (они могут быть в stdout, что вызывает проблемы)
            }
        } catch (e: Exception) {
            // Игнорируем ошибки чтения stderr
        }
    }
    
    sealed class ConnectionResult {
        data class Success(
            val tools: List<Tool>,
            val resources: List<Resource>,
            val serverName: String,
            val serverDescription: String?
        ) : ConnectionResult()
        
        data class Error(
            val message: String,
            val cause: Throwable? = null
        ) : ConnectionResult()
    }
}

