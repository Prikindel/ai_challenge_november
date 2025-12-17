# Создание своих MCP серверов

Руководство по созданию собственных MCP серверов для God Agent.

## Что такое MCP сервер?

MCP (Model Context Protocol) сервер — это модульный плагин, который предоставляет инструменты (tools) для AI агента. Каждый MCP сервер может быть включен или выключен через конфигурацию.

## Структура MCP сервера

```
mcp-servers/my-custom-server/
├── src/main/kotlin/com/prike/mycustommcpserver/
│   ├── Main.kt
│   ├── server/
│   │   └── MyCustomMCPServer.kt
│   └── tools/
│       ├── MyTool1.kt
│       └── MyTool2.kt
├── build.gradle.kts
└── README.md
```

## Пример: Простой MCP сервер

### 1. Создать сервер

```kotlin
// MyCustomMCPServer.kt
import com.prike.mcpcommon.server.BaseMCPServer
import com.prike.mcpcommon.dto.Tool

class MyCustomMCPServer : BaseMCPServer() {
    override fun getTools(): List<Tool> {
        return listOf(
            Tool(
                name = "my_tool",
                description = "Описание инструмента",
                inputSchema = mapOf(
                    "param1" to "string",
                    "param2" to "number"
                )
            )
        )
    }
    
    override suspend fun handleToolCall(
        toolName: String,
        arguments: Map<String, Any>
    ): ToolResult {
        return when (toolName) {
            "my_tool" -> {
                val param1 = arguments["param1"] as String
                val param2 = arguments["param2"] as Int
                
                // Ваша логика
                val result = doSomething(param1, param2)
                
                ToolResult(
                    success = true,
                    data = result
                )
            }
            else -> ToolResult(
                success = false,
                error = "Unknown tool: $toolName"
            )
        }
    }
    
    private fun doSomething(param1: String, param2: Int): String {
        // Ваша логика
        return "Result: $param1, $param2"
    }
}
```

### 2. Main.kt

```kotlin
import com.prike.mcpcommon.server.startMCPServer

fun main() {
    val server = MyCustomMCPServer()
    startMCPServer(server, port = 8001)
}
```

### 3. Добавить в конфигурацию

**config/mcp-servers.yaml:**
```yaml
mcp_servers:
  enabled: true
  
  my_custom_server:
    enabled: true
    name: "My Custom Server"
    description: "Описание сервера"
    config:
      port: 8001
      api_key: "${MY_CUSTOM_API_KEY}"
```

### 4. Интегрировать в God Agent

**domain/service/MCPRouterService.kt:**
```kotlin
class MCPRouterService {
    private val mcpClients = mapOf(
        "git" to GitMCPClient(),
        "telegram" to TelegramMCPClient(),
        "analytics" to AnalyticsMCPClient(),
        "my_custom_server" to MyCustomMCPClient()  // Добавить
    )
}
```

## Примеры MCP серверов

### 1. Weather MCP

Полный пример MCP сервера для получения погоды:

```kotlin
import com.prike.mcpcommon.server.BaseMCPServer
import com.prike.mcpcommon.dto.Tool
import com.prike.mcpcommon.dto.ToolResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val city: String,
    val temperature: Double,
    val condition: String,
    val humidity: Int
)

class WeatherMCPServer(
    private val apiKey: String
) : BaseMCPServer() {
    
    private val httpClient = HttpClient()
    
    override fun getTools(): List<Tool> {
        return listOf(
            Tool(
                name = "get_weather",
                description = "Получить текущую погоду для указанного города. Возвращает температуру, условия и влажность.",
                inputSchema = mapOf(
                    "city" to mapOf(
                        "type" to "string",
                        "description" to "Название города (например: Москва, London)"
                    )
                )
            ),
            Tool(
                name = "get_weather_forecast",
                description = "Получить прогноз погоды на несколько дней",
                inputSchema = mapOf(
                    "city" to mapOf("type" to "string"),
                    "days" to mapOf("type" to "number", "default" to 3)
                )
            )
        )
    }
    
    override suspend fun handleToolCall(
        toolName: String,
        arguments: Map<String, Any>
    ): ToolResult {
        return when (toolName) {
            "get_weather" -> {
                try {
                    val city = arguments["city"] as? String
                        ?: return ToolResult(
                            success = false,
                            error = "City parameter is required"
                        )
                    
                    val weather = fetchWeather(city)
                    ToolResult(success = true, data = weather)
                } catch (e: Exception) {
                    ToolResult(
                        success = false,
                        error = "Failed to fetch weather: ${e.message}"
                    )
                }
            }
            "get_weather_forecast" -> {
                val city = arguments["city"] as? String
                val days = (arguments["days"] as? Number)?.toInt() ?: 3
                val forecast = fetchForecast(city, days)
                ToolResult(success = true, data = forecast)
            }
            else -> ToolResult(
                success = false,
                error = "Unknown tool: $toolName"
            )
        }
    }
    
    private suspend fun fetchWeather(city: String): WeatherResponse {
        val response = httpClient.get("https://api.weather.com/v1/current") {
            parameter("city", city)
            parameter("apiKey", apiKey)
        }.body<WeatherResponse>()
        
        return response
    }
    
    private suspend fun fetchForecast(city: String, days: Int): Map<String, Any> {
        // Implementation for forecast
        return mapOf("city" to city, "days" to days, "forecast" to emptyList<Any>())
    }
}
```

**Конфигурация:**
```yaml
# config/mcp-servers.yaml
weather:
  enabled: true
  name: "Weather MCP"
  description: "Get weather information"
  config:
    api_key: "${WEATHER_API_KEY}"
    port: 8002
```

### 2. Database MCP

Пример MCP сервера для работы с базами данных:

```kotlin
import com.prike.mcpcommon.server.BaseMCPServer
import com.prike.mcpcommon.dto.Tool
import com.prike.mcpcommon.dto.ToolResult
import java.sql.*

class DatabaseMCPServer(
    private val defaultDbPath: String = "data/default.db"
) : BaseMCPServer() {
    
    override fun getTools(): List<Tool> {
        return listOf(
            Tool(
                name = "query_database",
                description = "Выполнить SQL запрос к базе данных. Поддерживает SELECT запросы. Возвращает результаты в формате JSON.",
                inputSchema = mapOf(
                    "query" to mapOf(
                        "type" to "string",
                        "description" to "SQL запрос (только SELECT)"
                    ),
                    "database" to mapOf(
                        "type" to "string",
                        "description" to "Путь к базе данных (опционально, по умолчанию используется default)",
                        "default" to defaultDbPath
                    )
                )
            ),
            Tool(
                name = "get_table_schema",
                description = "Получить схему таблицы",
                inputSchema = mapOf(
                    "table" to mapOf("type" to "string"),
                    "database" to mapOf("type" to "string", "default" to defaultDbPath)
                )
            ),
            Tool(
                name = "list_tables",
                description = "Получить список всех таблиц в базе данных",
                inputSchema = mapOf(
                    "database" to mapOf("type" to "string", "default" to defaultDbPath)
                )
            )
        )
    }
    
    override suspend fun handleToolCall(
        toolName: String,
        arguments: Map<String, Any>
    ): ToolResult {
        return when (toolName) {
            "query_database" -> {
                val query = arguments["query"] as? String
                    ?: return ToolResult(success = false, error = "Query is required")
                
                val dbPath = arguments["database"] as? String ?: defaultDbPath
                
                // Security: Only allow SELECT queries
                if (!query.trimStart().uppercase().startsWith("SELECT")) {
                    return ToolResult(
                        success = false,
                        error = "Only SELECT queries are allowed"
                    )
                }
                
                try {
                    val results = executeQuery(dbPath, query)
                    ToolResult(success = true, data = results)
                } catch (e: Exception) {
                    ToolResult(
                        success = false,
                        error = "Query failed: ${e.message}"
                    )
                }
            }
            "get_table_schema" -> {
                val table = arguments["table"] as? String
                    ?: return ToolResult(success = false, error = "Table name is required")
                val dbPath = arguments["database"] as? String ?: defaultDbPath
                val schema = getTableSchema(dbPath, table)
                ToolResult(success = true, data = schema)
            }
            "list_tables" -> {
                val dbPath = arguments["database"] as? String ?: defaultDbPath
                val tables = listTables(dbPath)
                ToolResult(success = true, data = tables)
            }
            else -> ToolResult(success = false, error = "Unknown tool: $toolName")
        }
    }
    
    private fun executeQuery(dbPath: String, query: String): List<Map<String, Any>> {
        val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery(query)
        
        val results = mutableListOf<Map<String, Any>>()
        val metaData = resultSet.metaData
        val columnCount = metaData.columnCount
        
        while (resultSet.next()) {
            val row = mutableMapOf<String, Any>()
            for (i in 1..columnCount) {
                val columnName = metaData.getColumnName(i)
                row[columnName] = resultSet.getObject(i) ?: ""
            }
            results.add(row)
        }
        
        resultSet.close()
        statement.close()
        connection.close()
        
        return results
    }
    
    private fun getTableSchema(dbPath: String, table: String): Map<String, Any> {
        val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        val metaData = connection.metaData
        val columns = metaData.getColumns(null, null, table, null)
        
        val schema = mutableListOf<Map<String, String>>()
        while (columns.next()) {
            schema.add(mapOf(
                "name" to columns.getString("COLUMN_NAME"),
                "type" to columns.getString("TYPE_NAME"),
                "nullable" to columns.getString("NULLABLE")
            ))
        }
        
        columns.close()
        connection.close()
        
        return mapOf("table" to table, "columns" to schema)
    }
    
    private fun listTables(dbPath: String): List<String> {
        val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        val metaData = connection.metaData
        val tables = metaData.getTables(null, null, null, arrayOf("TABLE"))
        
        val tableNames = mutableListOf<String>()
        while (tables.next()) {
            tableNames.add(tables.getString("TABLE_NAME"))
        }
        
        tables.close()
        connection.close()
        
        return tableNames
    }
}
```

**Пример использования:**
```kotlin
// В God Agent
val result = mcpRouterService.executeTool(
    serverName = "database",
    toolName = "query_database",
    arguments = mapOf(
        "query" to "SELECT * FROM users WHERE age > 25 LIMIT 10",
        "database" to "data/users.db"
    )
)
```

### 3. File Search MCP

Пример MCP сервера для поиска файлов:

```kotlin
import com.prike.mcpcommon.server.BaseMCPServer
import com.prike.mcpcommon.dto.Tool
import com.prike.mcpcommon.dto.ToolResult
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class FileSearchMCPServer(
    private val allowedPaths: List<String>
) : BaseMCPServer() {
    
    override fun getTools(): List<Tool> {
        return listOf(
            Tool(
                name = "search_files",
                description = "Найти файлы по имени или содержимому в разрешенных директориях",
                inputSchema = mapOf(
                    "pattern" to mapOf(
                        "type" to "string",
                        "description" to "Паттерн поиска (имя файла или текст)"
                    ),
                    "path" to mapOf(
                        "type" to "string",
                        "description" to "Путь для поиска (должен быть в allowed_paths)"
                    ),
                    "searchContent" to mapOf(
                        "type" to "boolean",
                        "description" to "Искать в содержимом файлов",
                        "default" to false
                    )
                )
            ),
            Tool(
                name = "read_file",
                description = "Прочитать содержимое файла",
                inputSchema = mapOf(
                    "filePath" to mapOf("type" to "string")
                )
            )
        )
    }
    
    override suspend fun handleToolCall(
        toolName: String,
        arguments: Map<String, Any>
    ): ToolResult {
        return when (toolName) {
            "search_files" -> {
                val pattern = arguments["pattern"] as? String
                    ?: return ToolResult(success = false, error = "Pattern is required")
                val searchPath = arguments["path"] as? String
                val searchContent = arguments["searchContent"] as? Boolean ?: false
                
                if (searchPath != null && !isPathAllowed(searchPath)) {
                    return ToolResult(
                        success = false,
                        error = "Path not allowed: $searchPath"
                    )
                }
                
                val results = searchFiles(pattern, searchPath, searchContent)
                ToolResult(success = true, data = results)
            }
            "read_file" -> {
                val filePath = arguments["filePath"] as? String
                    ?: return ToolResult(success = false, error = "File path is required")
                
                if (!isPathAllowed(filePath)) {
                    return ToolResult(
                        success = false,
                        error = "File path not allowed"
                    )
                }
                
                val content = readFile(filePath)
                ToolResult(success = true, data = content)
            }
            else -> ToolResult(success = false, error = "Unknown tool")
        }
    }
    
    private fun isPathAllowed(path: String): Boolean {
        val normalizedPath = Paths.get(path).normalize().toString()
        return allowedPaths.any { allowedPath ->
            normalizedPath.startsWith(Paths.get(allowedPath).normalize().toString())
        }
    }
    
    private fun searchFiles(
        pattern: String,
        searchPath: String?,
        searchContent: Boolean
    ): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        val pathsToSearch = if (searchPath != null) {
            listOf(searchPath)
        } else {
            allowedPaths
        }
        
        pathsToSearch.forEach { basePath ->
            Files.walk(Paths.get(basePath)).use { stream ->
                stream.filter { path ->
                    val file = path.toFile()
                    if (file.isDirectory) return@filter false
                    
                    if (searchContent) {
                        try {
                            file.readText().contains(pattern, ignoreCase = true)
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        file.name.contains(pattern, ignoreCase = true)
                    }
                }.forEach { path ->
                    results.add(mapOf(
                        "path" to path.toString(),
                        "size" to path.toFile().length(),
                        "modified" to path.toFile().lastModified()
                    ))
                }
            }
        }
        
        return results
    }
    
    private fun readFile(filePath: String): Map<String, Any> {
        val file = File(filePath)
        return mapOf(
            "path" to filePath,
            "content" to file.readText(),
            "size" to file.length(),
            "lines" to file.readLines().size
        )
    }
}
```

## Лучшие практики

1. **Именование инструментов**: используйте snake_case (`get_weather`, `query_database`)
2. **Описания**: пишите понятные описания для LLM
3. **Обработка ошибок**: всегда возвращайте ToolResult с success = false при ошибках
4. **Валидация**: проверяйте входные параметры
5. **Логирование**: логируйте вызовы инструментов для отладки

## Тестирование

```kotlin
fun main() {
    val server = MyCustomMCPServer()
    
    // Тест получения инструментов
    val tools = server.getTools()
    println("Tools: ${tools.map { it.name }}")
    
    // Тест вызова инструмента
    val result = server.handleToolCall(
        "my_tool",
        mapOf("param1" to "test", "param2" to 42)
    )
    println("Result: $result")
}
```

## Интеграция в God Agent

После создания MCP сервера:

1. Добавьте сервер в `config/mcp-servers.yaml`
2. Создайте MCP клиент в `data/client/MyCustomMCPClient.kt`
3. Добавьте клиент в `MCPRouterService`
4. Перезапустите God Agent

## Дополнительные ресурсы

- [MCP Specification](https://modelcontextprotocol.io/)
- Примеры в `mcp-servers/` директории
- Базовый класс: `com.prike.mcpcommon.server.BaseMCPServer`

