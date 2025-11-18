# Промпт для реализации: 🔥 День 11. Первый инструмент MCP

Ты — разработчик, создающий урок "🔥 День 11. Первый инструмент MCP" в модуле `lesson-11-first-mcp-tool`. Проект использует стек **Kotlin + Ktor + Clean Architecture** с фронтендом на **vanilla JS**. Твоя задача — построить полностью самостоятельный модуль без упоминаний других уроков в коде и документации. Используй **официальный Kotlin SDK для MCP** (`io.modelcontextprotocol:kotlin-sdk`) для создания собственного MCP сервера.

## 🎯 Цель урока

Создать собственный MCP сервер, который предоставляет инструменты для работы с внешним API (например, Яндекс.Трекер), подключить его к приложению и интегрировать с LLM агентом. **LLM агент знает о доступных инструментах MCP, решает когда и какие инструменты вызывать, получает результаты и формирует ответ пользователю.**

### Архитектура взаимодействия:

```
Пользователь → LLM Агент → (решает вызвать инструмент) → MCP Client → MCP Server → External API
                                                                                        ↓
Пользователь ← LLM Агент ← (обрабатывает результат) ← MCP Client ← MCP Server ←────────┘
```

**Поток работы:**
1. Пользователь отправляет сообщение через UI
2. LLM агент получает список доступных инструментов от MCP сервера
3. LLM агент анализирует запрос и решает, какой инструмент вызвать (или несколько)
4. Клиент вызывает инструмент(ы) через MCP
5. Результат возвращается LLM агенту
6. LLM агент обрабатывает результат и формирует понятный ответ пользователю
7. Ответ отображается в UI

---

## 🧠 Этап 0: Мозговой штурм и выбор функциональности

**ВАЖНО:** Перед началом реализации необходимо определить, что будет делать MCP сервер и какие инструменты он предоставит.

### Задача для агента:

1. **Проанализируй доступные API:**
   - Яндекс.Трекер (Yandex Tracker API)
   - GitHub API
   - Telegram Bot API
   - Любой другой публичный API
   - Или локальный сервис (файловая система, база данных)

2. **Предложи 3-5 вариантов функциональности MCP сервера:**
   - Для каждого варианта укажи:
     - Название API/сервиса
     - Какие инструменты (tools) будет предоставлять
     - Примеры использования
     - Сложность реализации
     - Требования (API ключи, зависимости)

3. **Выбери один вариант для реализации:**
   - Рекомендуется начать с простого варианта (например, получение количества задач из Трекера)
   - Убедись, что есть доступ к API (ключи, документация)

### Примеры вариантов:

**Вариант 1: Яндекс.Трекер MCP Server**
- Инструменты:
  - `get_tasks_count` — получить количество задач
  - `get_task_by_id` — получить задачу по ID
  - `create_task` — создать новую задачу
  - `get_my_tasks` — получить мои задачи
- Сложность: Средняя
- Требования: API токен Яндекс.Трекера

**Вариант 2: GitHub MCP Server**
- Инструменты:
  - `get_repository_info` — информация о репозитории
  - `get_issues_count` — количество issues
  - `create_issue` — создать issue
- Сложность: Средняя
- Требования: GitHub Personal Access Token

**Вариант 3: Простой File System MCP Server**
- Инструменты:
  - `list_files` — список файлов в директории
  - `read_file` — прочитать файл
  - `file_exists` — проверить существование файла
- Сложность: Низкая
- Требования: Доступ к файловой системе

**Выбери один вариант и задокументируй выбор в README.md с обоснованием.**

---

## 🧠 Теория (добавь в README.md)

### Что такое MCP Server?

**MCP Server** — это сервер, который предоставляет инструменты (tools), ресурсы (resources) и промпты (prompts) для LLM через стандартизированный протокол. MCP Server может быть реализован на любом языке программирования и предоставлять доступ к любым внешним системам.

### Архитектура с LLM агентом:

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│ Пользователь│───►│ LLM Агент   │───►│ MCP Client  │───►│ MCP Server │
│             │    │ (знает о    │    │             │    │             │
│             │    │  инструментах)│    │             │    │             │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                          ▲                    │                  │
                          │                    │                  │
                          └────────────────────┴──────────────────┘
                                               │
                                               ▼
                                        ┌─────────────┐
                                        │ External    │
                                        │ API/Service │
                                        └─────────────┘
```

**Ключевой момент:** LLM агент получает список инструментов от MCP сервера и сам решает, когда и какие инструменты вызывать на основе запроса пользователя.

### Основные компоненты MCP Server:

1. **Server** — основной класс сервера из Kotlin SDK
2. **Tools** — инструменты, которые может вызывать клиент
3. **Transport** — способ передачи данных (stdio, SSE, WebSocket)
4. **API Client** — клиент для работы с внешним API

### Жизненный цикл MCP Server:

1. Инициализация сервера
2. Регистрация инструментов
3. Подключение транспорта (stdio)
4. Ожидание запросов от клиента
5. Обработка вызовов инструментов
6. Возврат результатов клиенту

---

## 📚 Документация

### README.md

Создай `lesson-11-first-mcp-tool/README.md` со следующей структурой:

1. **Описание модуля** — что делает, зачем нужен
2. **Выбранная функциональность** — какой API используется, какие инструменты предоставляются
3. **Теория** — краткое описание MCP Server (см. выше)
4. **Быстрый старт** — как запустить MCP сервер и приложение
5. **Структура проекта** — описание файлов
6. **Конфигурация** — настройка API ключей и параметров
7. **API Endpoints** — описание эндпоинтов приложения
8. **Примеры использования** — сценарии работы с инструментами
9. **Выводы** — что получилось, что можно улучшить

**Важно:** Не упоминай другие уроки в README. Модуль должен быть самостоятельным.

### Обновление корневого README.md

В корневом `README.md` добавь раздел про урок 11:

```markdown
- [lesson-11-first-mcp-tool](./lesson-11-first-mcp-tool/) — создание собственного MCP сервера с инструментами для работы с внешним API
```

---

## ⚙️ Конфигурация

### Зависимости (server/build.gradle.kts)

Добавь зависимости:

```kotlin
dependencies {
    // ... существующие зависимости (Ktor, Kotlin Serialization, и т.д.) ...
    
    // MCP Kotlin SDK (для создания сервера)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.7")  // или последняя версия
    
    // HTTP клиент для работы с внешним API
    // Ktor Client уже есть, но убедись что версия совместима
    
    // Для работы с JSON (уже есть kotlinx-serialization-json)
}
```

**Важно:** Проверь последнюю версию SDK на [Maven Central](https://mvnrepository.com/artifact/io.modelcontextprotocol/kotlin-sdk).

### Конфигурация MCP Server (config/mcp-server.yaml)

Создай файл `config/mcp-server.yaml` для настройки MCP сервера:

```yaml
mcpServer:
  # Информация о сервере
  info:
    name: "yandex-tracker-mcp-server"  # или другое название
    version: "1.0.0"
    description: "MCP сервер для работы с Яндекс.Трекером"
  
  # Настройки внешнего API
  api:
    baseUrl: "https://api.tracker.yandex.net/v2"
    token: "${YANDEX_TRACKER_TOKEN}"  # из .env
    orgId: "${YANDEX_TRACKER_ORG_ID}"  # из .env
  
  # Настройки транспорта
  transport:
    type: "stdio"  # stdio, sse, websocket
  
  # Список доступных инструментов
  tools:
    - name: "get_tasks_count"
      description: "Получить количество задач в очереди"
      enabled: true
    - name: "get_task_by_id"
      description: "Получить задачу по ID"
      enabled: true
    - name: "get_my_tasks"
      description: "Получить мои задачи"
      enabled: true
```

### Переменные окружения (.env)

Добавь необходимые переменные в `.env` в корне проекта:

```env
# MCP Server Configuration
YANDEX_TRACKER_TOKEN=your_tracker_token_here
YANDEX_TRACKER_ORG_ID=your_org_id_here

# Или для другого API:
# GITHUB_TOKEN=your_github_token_here
# TELEGRAM_BOT_TOKEN=your_bot_token_here
```

Создай `example.env` в директории урока с примерами.

---

## 🧠 Структура проекта

```
lesson-11-first-mcp-tool/
├── README.md                        # Описание модуля
├── example.env                      # Пример переменных окружения
├── config/
│   ├── mcp-server.yaml              # Конфигурация MCP сервера
│   └── mcp-server.yaml.example      # Пример конфигурации
├── mcp-server/                      # MCP сервер (отдельный модуль/процесс)
│   └── src/main/kotlin/com/prike/mcpserver/
│       ├── Main.kt                  # Точка входа MCP сервера
│       ├── Config.kt                # Загрузка конфигурации
│       ├── server/
│       │   └── MCPServer.kt         # Основной класс MCP сервера
│       ├── tools/
│       │   ├── ToolRegistry.kt      # Регистрация инструментов
│       │   └── TasksTool.kt         # Пример инструмента (для Трекера)
│       ├── api/
│       │   └── TrackerApiClient.kt  # Клиент для работы с API
│       └── dto/
│           └── TrackerModels.kt     # DTO для API
├── server/                          # Основное приложение (клиент MCP)
│   └── src/main/kotlin/com/prike/
│       ├── Main.kt                  # Точка входа приложения
│       ├── Config.kt                # Загрузка конфигурации
│       ├── data/
│       │   └── client/
│       │       └── MCPClient.kt     # Клиент для подключения к MCP серверу
│       ├── domain/
│       │   └── agent/
│       │       └── MCPToolAgent.kt   # Агент для вызова инструментов MCP
│       └── presentation/
│           ├── controller/
│           │   └── ToolController.kt # HTTP контроллер
│           └── dto/
│               └── ToolDtos.kt      # DTO для HTTP API
└── client/                          # Веб-клиент
    ├── index.html
    ├── style.css
    └── app.js
```

**Важно:** 
- MCP сервер должен быть **отдельным процессом** (отдельный JAR файл), который запускается независимо от основного приложения
- Основное приложение подключается к MCP серверу через stdio транспорт (запускает процесс и подключается к его stdin/stdout)
- Это демонстрирует реальный сценарий использования MCP, где сервер и клиент — разные процессы

---

## 🧠 Реализация MCP Server

### 1. MCPServer.kt

Создай основной класс MCP сервера:

```kotlin
package com.prike.mcpserver.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import com.prike.mcpserver.tools.ToolRegistry

class MCPServer(
    private val serverInfo: Implementation,
    private val toolRegistry: ToolRegistry
) {
    private val server = Server(
        serverInfo = serverInfo,
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null)
            )
        )
    ) {
        "MCP Server для работы с внешним API"
    }
    
    fun start() {
        // Регистрация всех инструментов из ToolRegistry
        toolRegistry.registerTools(server)
        
        // Запуск сервера с stdio транспортом
        val transport = StdioServerTransport()
        server.connect(transport)
        
        println("MCP Server started and waiting for connections...")
    }
}
```

### 2. ToolRegistry.kt

Создай реестр инструментов:

```kotlin
package com.prike.mcpserver.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import com.prike.mcpserver.api.TrackerApiClient

class ToolRegistry(
    private val apiClient: TrackerApiClient
) {
    fun registerTools(server: Server) {
        // Регистрация инструмента get_tasks_count
        server.addTool(
            name = "get_tasks_count",
            description = "Получить количество задач в указанной очереди"
        ) { request ->
            val queueKey = request.params.arguments?.get("queueKey") as? String
                ?: throw IllegalArgumentException("queueKey is required")
            
            val count = apiClient.getTasksCount(queueKey)
            
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Количество задач в очереди '$queueKey': $count"
                    )
                )
            )
        }
        
        // Регистрация инструмента get_task_by_id
        server.addTool(
            name = "get_task_by_id",
            description = "Получить задачу по ID"
        ) { request ->
            val taskId = request.params.arguments?.get("taskId") as? String
                ?: throw IllegalArgumentException("taskId is required")
            
            val task = apiClient.getTaskById(taskId)
            
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = task.toJson()  // Преобразуй задачу в JSON
                    )
                )
            )
        }
        
        // Добавь другие инструменты...
    }
}
```

### 3. TrackerApiClient.kt

Создай клиент для работы с API:

```kotlin
package com.prike.mcpserver.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import com.prike.mcpserver.dto.Task

class TrackerApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String,
    private val orgId: String
) {
    suspend fun getTasksCount(queueKey: String): Int {
        val response = httpClient.get("$baseUrl/issues/_count") {
            header("Authorization", "OAuth $token")
            header("X-Org-ID", orgId)
            parameter("queue", queueKey)
        }
        return response.body<CountResponse>().count
    }
    
    suspend fun getTaskById(taskId: String): Task {
        val response = httpClient.get("$baseUrl/issues/$taskId") {
            header("Authorization", "OAuth $token")
            header("X-Org-ID", orgId)
        }
        return response.body<Task>()
    }
    
    // Добавь другие методы API...
}
```

### 4. Main.kt (MCP Server)

Точка входа MCP сервера:

```kotlin
package com.prike.mcpserver

import com.prike.mcpserver.server.MCPServer
import com.prike.mcpserver.Config
import com.prike.mcpserver.tools.ToolRegistry
import com.prike.mcpserver.api.TrackerApiClient
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.ktor.client.*
import io.ktor.client.engine.cio.*

fun main() {
    // Загрузка конфигурации
    val config = Config.load()
    
    // Создание HTTP клиента
    val httpClient = HttpClient(CIO)
    
    // Создание API клиента
    val apiClient = TrackerApiClient(
        httpClient = httpClient,
        baseUrl = config.api.baseUrl,
        token = config.api.token,
        orgId = config.api.orgId
    )
    
    // Создание реестра инструментов
    val toolRegistry = ToolRegistry(apiClient)
    
    // Создание и запуск MCP сервера
    val server = MCPServer(
        serverInfo = Implementation(
            name = config.serverInfo.name,
            version = config.serverInfo.version
        ),
        toolRegistry = toolRegistry
    )
    
    server.start()
    
    // Ожидание завершения (сервер работает в stdio режиме)
    Thread.currentThread().join()
}
```

---

## 🧠 Реализация клиента (основное приложение)

### 1. MCPClient.kt

Клиент для подключения к MCP серверу (аналогично lesson-10):

```kotlin
package com.prike.data.client

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import java.io.InputStream
import java.io.OutputStream

class MCPClient {
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-11-mcp-client",
            version = "1.0.0"
        )
    )
    
    private var isConnected = false
    private var mcpServerProcess: Process? = null
    
    suspend fun connectToServer(jarPath: String) {
        // Запуск MCP сервера как отдельного процесса
        val process = ProcessBuilder("java", "-jar", jarPath)
            .start()
        
        mcpServerProcess = process
        
        // Подключение к stdin/stdout процесса
        val transport = StdioClientTransport(
            inputStream = process.inputStream,
            outputStream = process.outputStream
        )
        
        client.connect(transport)
        isConnected = true
    }
    
    fun disconnect() {
        mcpServerProcess?.destroy()
        mcpServerProcess = null
        isConnected = false
    }
    
    suspend fun listTools(): List<Tool> {
        if (!isConnected) {
            throw IllegalStateException("MCP client not connected")
        }
        val response = client.listTools()
        return response.tools.map { /* преобразование */ }
    }
    
    suspend fun callTool(
        name: String,
        arguments: Map<String, Any>
    ): String {
        if (!isConnected) {
            throw IllegalStateException("MCP client not connected")
        }
        val result = client.callTool(
            CallToolRequest(
                name = name,
                arguments = arguments
            )
        )
        return result.content.firstOrNull()?.text ?: ""
    }
}
```

### 2. MCPToolAgent.kt

Агент для работы с инструментами MCP:

```kotlin
package com.prike.domain.agent

import com.prike.data.client.MCPClient
import com.prike.domain.exception.ToolException

class MCPToolAgent(
    private val mcpClient: MCPClient
) {
    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>
    ): ToolResult {
        return try {
            val result = mcpClient.callTool(toolName, arguments)
            ToolResult.Success(result)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Unknown error", e)
        }
    }
    
    suspend fun getAvailableTools(): List<ToolInfo> {
        return mcpClient.listTools().map { tool ->
            ToolInfo(
                name = tool.name,
                description = tool.description
            )
        }
    }
    
    sealed class ToolResult {
        data class Success(val result: String) : ToolResult()
        data class Error(val message: String, val cause: Throwable? = null) : ToolResult()
    }
    
    data class ToolInfo(
        val name: String,
        val description: String?
    )
}
```

### 3. MCPConnectionController.kt

Контроллер для подключения к MCP серверу:

```kotlin
package com.prike.presentation.controller

import com.prike.data.client.MCPClient
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

class MCPConnectionController(
    private val mcpClient: MCPClient,
    private val lessonRoot: String
) {
    fun configureRoutes(routing: Routing) {
        routing.route("/api/mcp") {
            // POST /api/mcp/connect - подключиться к MCP серверу
            post("/connect") {
                handleConnect()
            }
        }
    }
    
    private suspend fun ApplicationCall.handleConnect() {
        try {
            val request = receive<ConnectMCPRequestDto>()
            val jarPath = File(lessonRoot, request.serverJarPath).absolutePath
            
            if (!File(jarPath).exists()) {
                respond(HttpStatusCode.NotFound, ErrorDto(
                    message = "MCP server JAR not found: $jarPath"
                ))
                return
            }
            
            mcpClient.connectToServer(jarPath)
            
            respond(HttpStatusCode.OK, ConnectMCPResponseDto(
                success = true,
                message = "Connected to MCP server"
            ))
        } catch (e: Exception) {
            respond(HttpStatusCode.InternalServerError, ErrorDto(
                message = "Failed to connect: ${e.message}"
            ))
        }
    }
}
```

### 4. ChatController.kt

HTTP контроллер для общения с LLM агентом:

```kotlin
package com.prike.presentation.controller

import com.prike.domain.agent.LLMWithMCPAgent
import com.prike.presentation.dto.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class ChatController(
    private val llmWithMCPAgent: LLMWithMCPAgent
) {
    fun configureRoutes(routing: Routing) {
        routing.route("/api/chat") {
            // POST /api/chat/message - отправить сообщение LLM агенту
            post("/message") {
                handleUserMessage()
            }
        }
    }
    
    private suspend fun ApplicationCall.handleUserMessage() {
        try {
            val request = receive<ChatMessageRequestDto>()
            val response = llmWithMCPAgent.processUserMessage(request.message)
            
            when (response) {
                is LLMWithMCPAgent.AgentResponse.Success -> {
                    respond(HttpStatusCode.OK, ChatMessageResponseDto(
                        message = response.message,
                        toolUsed = response.toolUsed,
                        toolResult = response.toolResult
                    ))
                }
                is LLMWithMCPAgent.AgentResponse.Error -> {
                    respond(HttpStatusCode.InternalServerError, ErrorDto(
                        message = response.message
                    ))
                }
            }
        } catch (e: Exception) {
            respond(HttpStatusCode.BadRequest, ErrorDto(
                message = e.message ?: "Unknown error"
            ))
        }
    }
}
```

---

## 🖥 Фронтенд

### index.html

Создай UI для работы с инструментами:

```html
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>🔥 День 11. Первый инструмент MCP</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="container">
        <header>
            <h1>🔥 Первый инструмент MCP</h1>
            <p class="subtitle">Создание собственного MCP сервера и вызов инструментов</p>
        </header>
        
        <div class="main-content">
            <!-- Статус подключения к MCP серверу -->
            <section class="connection-status">
                <h2>Статус подключения</h2>
                <div id="mcpStatus" class="status-indicator">Не подключено</div>
                <button id="connectBtn" class="btn btn-primary">Подключиться к MCP серверу</button>
            </section>
            
            <!-- Чат с LLM агентом -->
            <section class="chat-section" id="chatSection" style="display: none;">
                <h2>Чат с ассистентом</h2>
                <div id="chatMessages" class="chat-messages"></div>
                <div class="chat-input-container">
                    <input type="text" id="userMessageInput" placeholder="Введите сообщение..." />
                    <button id="sendMessageBtn" class="btn btn-primary">Отправить</button>
                </div>
            </section>
            
            <!-- Информация об использованных инструментах -->
            <section class="tools-info" id="toolsInfo" style="display: none;">
                <h3>Использованные инструменты</h3>
                <div id="toolsUsedList" class="tools-used-list"></div>
            </section>
        </div>
    </div>
    
    <script src="app.js"></script>
</body>
</html>
```

### app.js

Реализуй логику:

```javascript
let availableTools = [];

// Подключение к MCP серверу
async function connectToMCPServer() {
    try {
        showStatus('Подключение...', 'info');
        
        // Запуск MCP сервера и подключение к нему
        // Это зависит от реализации - может быть через API или напрямую
        const response = await fetch('/api/mcp/connect', {
            method: 'POST'
        });
        
        const data = await response.json();
        
        if (data.success) {
            showStatus('Подключено к MCP серверу', 'success');
            showChatSection();
        } else {
            showStatus('Ошибка подключения: ' + data.message, 'error');
        }
    } catch (error) {
        console.error('Connection error:', error);
        showStatus('Ошибка подключения', 'error');
    }
}

// Отправка сообщения LLM агенту
async function sendMessage() {
    const input = document.getElementById('userMessageInput');
    const message = input.value.trim();
    
    if (!message) return;
    
    // Добавляем сообщение пользователя в чат
    addMessageToChat('user', message);
    input.value = '';
    
    // Показываем индикатор загрузки
    const loadingId = addMessageToChat('assistant', 'Думаю...', true);
    
    try {
        const response = await fetch('/api/chat/message', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message })
        });
        
        const data = await response.json();
        
        // Удаляем индикатор загрузки
        removeMessage(loadingId);
        
        // Добавляем ответ ассистента
        addMessageToChat('assistant', data.message);
        
        // Если использовался инструмент, показываем информацию
        if (data.toolUsed) {
            showToolUsage(data.toolUsed, data.toolResult);
        }
    } catch (error) {
        console.error('Message send error:', error);
        removeMessage(loadingId);
        addMessageToChat('assistant', 'Ошибка: ' + error.message);
    }
}

// Добавление сообщения в чат
function addMessageToChat(role, text, isLoading = false) {
    const messagesContainer = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}-message`;
    if (isLoading) {
        messageDiv.id = 'loading-message';
    }
    messageDiv.textContent = text;
    messagesContainer.appendChild(messageDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
    return messageDiv.id || null;
}

// Показ информации об использованном инструменте
function showToolUsage(toolName, toolResult) {
    const section = document.getElementById('toolsInfo');
    const list = document.getElementById('toolsUsedList');
    
    const toolDiv = document.createElement('div');
    toolDiv.className = 'tool-used-item';
    toolDiv.innerHTML = `
        <strong>${toolName}</strong>
        <pre>${toolResult}</pre>
    `;
    
    list.appendChild(toolDiv);
    section.style.display = 'block';
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('connectBtn').addEventListener('click', connectToMCPServer);
    document.getElementById('sendMessageBtn').addEventListener('click', sendMessage);
    document.getElementById('userMessageInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendMessage();
        }
    });
});
```

---

## ✅ Финальная проверка

1. **Сборка MCP сервера:**
   ```bash
   cd lesson-11-first-mcp-tool/mcp-server
   ./gradlew build
   ```

2. **Запуск MCP сервера:**
   ```bash
   java -jar build/libs/mcp-server-1.0.0.jar
   ```

3. **Сборка и запуск основного приложения:**
   ```bash
   cd lesson-11-first-mcp-tool/server
   ./gradlew run
   ```

4. **Проверка UI:**
   - Открой `http://localhost:8080`
   - Подключись к MCP серверу
   - Убедись, что список инструментов отображается
   - Вызови инструмент и проверь результат

---

## 🎯 Итоговый результат

Урок должен демонстрировать:

1. ✅ **Мозговой штурм** — выбор функциональности MCP сервера с обоснованием
2. ✅ **Создание MCP сервера** — реализация сервера с использованием Kotlin SDK
3. ✅ **Интеграция с внешним API** — подключение к выбранному API (Трекер, GitHub, и т.д.)
4. ✅ **Регистрация инструментов** — добавление tools в MCP сервер
5. ✅ **Подключение клиента** — подключение приложения к MCP серверу
6. ✅ **Вызов инструментов** — демонстрация вызова инструментов из приложения
7. ✅ **UI для работы с инструментами** — интерфейс для вызова и отображения результатов
8. ✅ **Теория про MCP Server** — описание в README
9. ✅ **Полная документация** — инструкции по настройке и использованию

**Важно:** 
- Модуль должен быть полностью самостоятельным
- MCP сервер должен быть отдельным процессом (JAR файл)
- **LLM агент должен знать о доступных инструментах и сам решать, когда их вызывать**
- Поток работы: пользователь → LLM (анализирует, решает вызвать инструмент) → MCP → результат → LLM (обрабатывает) → пользователь
- Документируй выбор API и функциональности в README
- Покажи полный цикл: создание сервера → регистрация инструментов → LLM получает список → LLM вызывает инструмент → результат обрабатывается LLM → ответ пользователю

---

## 📝 Дополнительные рекомендации

1. **Выбор API:**
   - Начни с простого API (например, получение количества задач)
   - Убедись, что есть доступ к API (ключи, токены)
   - Документируй требования к API в README

2. **Структура MCP сервера:**
   - Раздели код на модули (server, tools, api)
   - Используй Clean Architecture для MCP сервера
   - Обрабатывай ошибки API корректно

3. **Обработка ошибок:**
   - Обрабатывай ошибки подключения к API
   - Показывай понятные сообщения об ошибках в UI
   - Логируй все операции

4. **Тестирование:**
   - Протестируй каждый инструмент отдельно
   - Проверь обработку ошибок (неверные аргументы, недоступный API)
   - Убедись, что UI корректно отображает результаты

5. **Документация:**
   - Опиши процесс выбора функциональности
   - Добавь инструкции по получению API ключей
   - Приведи примеры вызова каждого инструмента

---

**Готово к реализации!** 🚀

**Помни:** Начни с этапа мозгового штурма — определи, что будет делать MCP сервер, прежде чем приступать к реализации!

