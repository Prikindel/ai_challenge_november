package com.prike.analyticsmcpserver.server

import com.prike.analyticsmcpserver.tools.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.ktor.utils.io.streams.asInput
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Analytics MCP Server для анализа данных из CSV, JSON, БД
 */
class AnalyticsMCPServer(
    serverInfo: Implementation,
    private val projectRoot: File
) {
    private val logger = LoggerFactory.getLogger(AnalyticsMCPServer::class.java)
    private val server = Server(
        serverInfo = serverInfo,
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null)
            )
        )
    )
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    fun start() {
        logger.info("Starting Analytics MCP Server")
        
        // Регистрация инструментов
        registerTools()
        
        // Запуск сервера с stdio транспортом
        val transport = StdioServerTransport(
            inputStream = System.`in`.asInput(),
            outputStream = System.out.asSink().buffered()
        )
        
        runBlocking {
            val session = server.createSession(transport)
            logger.info("Analytics MCP Server started and waiting for connections...")
            
            val done = Job()
            session.onClose {
                logger.info("Analytics MCP Server session closed")
                done.complete()
            }
            
            done.join()
        }
    }
    
    /**
     * Регистрирует все инструменты на сервере
     */
    private fun registerTools() {
        // analyze_csv
        server.addTool(
            name = "analyze_csv",
            description = "Анализ данных из CSV файла. Возвращает статистику, структуру данных и ответы на вопросы.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("file_path") {
                        put("type", "string")
                        put("description", "Путь к CSV файлу (относительно корня проекта или абсолютный)")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Вопрос для анализа данных")
                    }
                },
                required = listOf("file_path", "query")
            )
        ) { request ->
            val params = request.arguments
            val filePath = params["file_path"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("file_path is required")
            val query = params["query"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("query is required")
            
            val result = analyzeCSV(filePath, query)
            CallToolResult(content = listOf(TextContent(result)))
        }
        
        // analyze_json
        server.addTool(
            name = "analyze_json",
            description = "Анализ данных из JSON файла. Возвращает структуру данных и ответы на вопросы.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("file_path") {
                        put("type", "string")
                        put("description", "Путь к JSON файлу")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Вопрос для анализа данных")
                    }
                },
                required = listOf("file_path", "query")
            )
        ) { request ->
            val params = request.arguments
            val filePath = params["file_path"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("file_path is required")
            val query = params["query"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("query is required")
            
            val result = analyzeJSON(filePath, query)
            CallToolResult(content = listOf(TextContent(result)))
        }
        
        // analyze_database
        server.addTool(
            name = "analyze_database",
            description = "Анализ данных из SQLite базы данных. Выполняет SQL запросы и анализирует результаты.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("db_path") {
                        put("type", "string")
                        put("description", "Путь к SQLite базе данных")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "SQL запрос или вопрос для анализа")
                    }
                },
                required = listOf("db_path", "query")
            )
        ) { request ->
            val params = request.arguments
            val dbPath = params["db_path"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("db_path is required")
            val query = params["query"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("query is required")
            
            val result = analyzeDatabase(dbPath, query)
            CallToolResult(content = listOf(TextContent(result)))
        }
        
        // get_data_summary
        server.addTool(
            name = "get_data_summary",
            description = "Получить сводку по источнику данных (CSV, JSON или БД).",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("data_source") {
                        put("type", "string")
                        put("description", "Путь к источнику данных")
                    }
                },
                required = listOf("data_source")
            )
        ) { request ->
            val params = request.arguments
            val dataSource = params["data_source"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("data_source is required")
            
            val result = getDataSummary(dataSource)
            CallToolResult(content = listOf(TextContent(result)))
        }
    }
    
    
    /**
     * Анализ CSV файла
     */
    private fun analyzeCSV(filePath: String, query: String): String {
        val file = resolveFilePath(filePath)
        if (!file.exists()) {
            return "Error: CSV file not found: ${file.absolutePath}"
        }
        
        return try {
            val csvAnalyzer = CSVAnalyzer(file)
            csvAnalyzer.analyze(query)
        } catch (e: Exception) {
            "Error analyzing CSV: ${e.message}"
        }
    }
    
    /**
     * Анализ JSON файла
     */
    private fun analyzeJSON(filePath: String, query: String): String {
        val file = resolveFilePath(filePath)
        if (!file.exists()) {
            return "Error: JSON file not found: ${file.absolutePath}"
        }
        
        return try {
            val jsonAnalyzer = JSONAnalyzer(file, json)
            jsonAnalyzer.analyze(query)
        } catch (e: Exception) {
            "Error analyzing JSON: ${e.message}"
        }
    }
    
    /**
     * Анализ базы данных
     */
    private fun analyzeDatabase(dbPath: String, query: String): String {
        val file = resolveFilePath(dbPath)
        if (!file.exists()) {
            return "Error: Database file not found: ${file.absolutePath}"
        }
        
        return try {
            val dbAnalyzer = DatabaseAnalyzer(file)
            dbAnalyzer.analyze(query)
        } catch (e: Exception) {
            "Error analyzing database: ${e.message}"
        }
    }
    
    /**
     * Получить сводку по источнику данных
     */
    private fun getDataSummary(dataSource: String): String {
        val file = resolveFilePath(dataSource)
        if (!file.exists()) {
            return "Error: Data source not found: ${file.absolutePath}"
        }
        
        return try {
            when (file.extension.lowercase()) {
                "csv" -> {
                    val csvAnalyzer = CSVAnalyzer(file)
                    csvAnalyzer.getSummary()
                }
                "json" -> {
                    val jsonAnalyzer = JSONAnalyzer(file, json)
                    jsonAnalyzer.getSummary()
                }
                "db", "sqlite" -> {
                    val dbAnalyzer = DatabaseAnalyzer(file)
                    dbAnalyzer.getSummary()
                }
                else -> "Unsupported file type: ${file.extension}"
            }
        } catch (e: Exception) {
            "Error getting summary: ${e.message}"
        }
    }
    
    /**
     * Разрешить путь к файлу (относительный или абсолютный)
     */
    private fun resolveFilePath(path: String): File {
        val file = File(path)
        return if (file.isAbsolute) {
            file
        } else {
            // Пробуем относительно корня проекта
            File(projectRoot, path)
        }
    }
}

