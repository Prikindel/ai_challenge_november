# Промпт для реализации: 🔥 День 32. God Agent

Ты — разработчик, создающий финальный урок «🔥 День 32. God Agent» в модуле `lesson-32-god-agent`. Это **объединение всех наработок** в одного большого персонального AI-помощника с модульной архитектурой (MCP как плагины, RAG для базы знаний).

## 🎯 Цель урока

Создать **персонального AI-помощника**, который объединяет:
- **RAG** — база знаний с индексацией документов
- **MCP серверы** — модульные плагины (Git, Telegram, Analytics, File System и т.д.)
- **Голосовой ввод** — распознавание речи через Vosk
- **Персонализация** — профиль пользователя, предпочтения, стиль
- **Аналитика** — анализ данных из разных источников
- **Локальная LLM** — работа через VPS

### Ключевая идея

```
Единый интерфейс → Роутинг запросов → MCP/RAG/Analytics → Локальная LLM → Персонализированный ответ
```

**Система должна:**
- Работать как персональная база знаний (как Obsidian)
- Поддерживать модульные MCP серверы (как плагины)
- Понимать контекст пользователя и его данные
- Анализировать данные из разных источников
- Персонализировать ответы под пользователя

---

## 📋 Поэтапная реализация (отдельные коммиты)

**КРИТИЧЕСКИ ВАЖНО:**
- Каждый перечисленный шаг = отдельный git-коммит.
- После каждого шага **СТОП**, покажи изменения пользователю и дождись «ок, продолжай».
- Пользователь сам делает merge/commit далее.

---

### Коммит 1: Выбор базы и подготовка структуры

**Задача:** выбрать базовый урок и подготовить структуру для God Agent.

**Рекомендуемая база:** `lesson-30-personalization` или `lesson-20-dev-assistant`
- Уже есть RAG, MCP, персонализация
- Полноценный чат с историей
- Интеграция с локальной LLM

**Действия:**
1. Скопировать структуру из `lesson-30-personalization` в `lesson-32-god-agent/`
2. Обновить названия:
   - Папка проекта, Gradle settings, package references
   - Все текстовые упоминания → `lesson-32-god-agent`
   - «День 30» → «День 32»
3. Создать структуру для модульных компонентов:
   ```
   lesson-32-god-agent/
   ├── mcp-servers/          # MCP серверы (плагины)
   │   ├── git-mcp/
   │   ├── telegram-mcp/
   │   ├── analytics-mcp/
   │   ├── filesystem-mcp/
   │   └── calendar-mcp/
   ├── knowledge-base/       # База знаний (RAG)
   │   ├── projects/
   │   ├── learning/
   │   ├── personal/
   │   └── references/
   ├── data/                 # Данные для анализа
   │   ├── analytics/
   │   └── projects/
   └── config/
       └── mcp-servers.yaml  # Конфигурация MCP серверов
   ```
4. Убедиться, что проект собирается и запускается

**После коммита:** остановиться, показать структуру проекта.

---

### Коммит 2: Конфигурация MCP серверов (плагинов)

**Задача:** создать систему конфигурации для модульных MCP серверов.

**Компоненты:**

1. **Конфигурация MCP** (`config/mcp-servers.yaml`):
   ```yaml
   mcp_servers:
     enabled: true
     
     git:
       enabled: true
       name: "Git MCP"
       description: "Работа с git репозиториями и файлами проекта"
       repositories:
         - path: "${HOME}/projects/my-project"
           name: "My Project"
         - path: "${HOME}/projects/other-project"
           name: "Other Project"
     
     telegram:
       enabled: true
       name: "Telegram MCP"
       description: "Напоминания и отправка сообщений в Telegram"
       bot_token: "${TELEGRAM_BOT_TOKEN}"
       chat_id: "${TELEGRAM_CHAT_ID}"
     
     analytics:
       enabled: true
       name: "Analytics MCP"
       description: "Анализ данных из CSV, JSON, БД"
       data_sources:
         - type: "csv"
           path: "data/analytics/metrics.csv"
           name: "Metrics"
         - type: "db"
           path: "data/analytics/user_data.db"
           name: "User Data"
         - type: "json"
           path: "data/analytics/logs.json"
           name: "Logs"
     
     filesystem:
       enabled: true
       name: "File System MCP"
       description: "Поиск и чтение файлов в файловой системе"
       allowed_paths:
         - "${HOME}/Documents"
         - "${HOME}/projects"
         - "knowledge-base"
     
     calendar:
       enabled: false  # Опционально
       name: "Calendar MCP"
       description: "Управление событиями и напоминаниями"
       storage_path: "data/calendar/events.json"
   ```

2. **Модель конфигурации** (`domain/model/MCPServerConfig.kt`):
   ```kotlin
   data class MCPServerConfig(
       val enabled: Boolean,
       val name: String,
       val description: String,
       val config: Map<String, Any>
   )
   
   data class MCPServersConfig(
       val enabled: Boolean,
       val servers: Map<String, MCPServerConfig>
   )
   ```

3. **Сервис загрузки конфигурации** (`domain/service/MCPConfigService.kt`):
   ```kotlin
   class MCPConfigService {
       fun loadConfig(): MCPServersConfig {
           // Загрузка из config/mcp-servers.yaml
       }
       
       fun getEnabledServers(): List<MCPServerConfig> {
           // Возвращает только включенные серверы
       }
       
       fun isServerEnabled(name: String): Boolean {
           // Проверка, включен ли сервер
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 3: Динамический MCP Router

**Задача:** создать роутер, который динамически подключает MCP серверы из конфигурации.

**Компоненты:**

1. **MCP Router Service** (`domain/service/MCPRouterService.kt`):
   ```kotlin
   class MCPRouterService(
       private val mcpConfigService: MCPConfigService,
       private val mcpClients: Map<String, MCPClient>
   ) {
       /**
        * Получить список всех доступных инструментов из всех MCP серверов
        */
       fun getAllAvailableTools(): List<MCPTool> {
           val enabledServers = mcpConfigService.getEnabledServers()
           return enabledServers.flatMap { serverConfig ->
               val client = mcpClients[serverConfig.name]
               client?.listTools() ?: emptyList()
           }
       }
       
       /**
        * Выполнить инструмент по имени
        */
       suspend fun executeTool(
           serverName: String,
           toolName: String,
           arguments: Map<String, Any>
       ): MCPToolResult {
           val client = mcpClients[serverName]
               ?: throw IllegalArgumentException("MCP server not found: $serverName")
           
           return client.callTool(toolName, arguments)
       }
       
       /**
        * Получить описание всех доступных инструментов для LLM
        */
       fun getToolsDescription(): String {
           val tools = getAllAvailableTools()
           return tools.groupBy { it.serverName }
               .map { (serverName, serverTools) ->
                   """
                   ## $serverName
                   ${serverTools.joinToString("\n") { "- ${it.name}: ${it.description}" }}
                   """.trimIndent()
               }
               .joinToString("\n\n")
       }
   }
   ```

2. **Модель инструмента** (`domain/model/MCPTool.kt`):
   ```kotlin
   data class MCPTool(
       val serverName: String,
       val name: String,
       val description: String,
       val parameters: Map<String, Any>
   )
   
   data class MCPToolResult(
       val success: Boolean,
       val data: Any?,
       val error: String?
   )
   ```

3. **Обновление RequestRouterService** (`domain/service/RequestRouterService.kt`):
   ```kotlin
   class RequestRouterService(
       private val mcpRouterService: MCPRouterService,
       private val llmService: LLMService
   ) {
       /**
        * Определить, какие инструменты использовать для запроса
        */
       suspend fun routeRequest(
           userQuery: String,
           chatHistory: List<ChatMessage>
       ): RoutingDecision {
           // Получить список доступных инструментов
           val availableTools = mcpRouterService.getAllAvailableTools()
           val toolsDescription = mcpRouterService.getToolsDescription()
           
           // LLM решает, какие инструменты использовать
           val prompt = buildRoutingPrompt(userQuery, toolsDescription, chatHistory)
           val response = llmService.generateResponse(prompt)
           
           return parseRoutingDecision(response, availableTools)
       }
       
       private fun buildRoutingPrompt(
           query: String,
           toolsDescription: String,
           history: List<ChatMessage>
       ): String {
           return """
           Ты — роутер запросов для персонального AI-помощника.
           
           Доступные инструменты:
           $toolsDescription
           
           История диалога:
           ${history.takeLast(5).joinToString("\n") { "${it.role}: ${it.content}" }}
           
           Запрос пользователя: $query
           
           Определи, какие инструменты нужно использовать для ответа на запрос.
           Верни JSON с решением:
           {
             "action": "MCP_TOOLS" | "RAG_SEARCH" | "ANALYTICS" | "DIRECT_ANSWER",
             "tools": [
               {"server": "server_name", "tool": "tool_name", "args": {...}}
             ],
             "reasoning": "объяснение выбора"
           }
           """.trimIndent()
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 4: Расширенная база знаний (RAG)

**Задача:** расширить RAG систему для работы с личной базой знаний.

**Компоненты:**

1. **Структура базы знаний** (`knowledge-base/`):
   ```
   knowledge-base/
   ├── projects/
   │   ├── project-1/
   │   │   ├── docs/
   │   │   ├── notes.md
   │   │   └── ideas.md
   │   └── project-2/
   ├── learning/
   │   ├── ai-notes.md
   │   ├── kotlin-tips.md
   │   └── architecture-patterns.md
   ├── personal/
   │   ├── goals-2024.md
   │   ├── meeting-notes/
   │   └── ideas.md
   └── references/
       ├── articles/
       └── books/
   ```

2. **Расширенный DocumentIndexer** (`domain/service/KnowledgeBaseService.kt`):
   ```kotlin
   class KnowledgeBaseService(
       private val documentIndexer: DocumentIndexer,
       private val embeddingService: EmbeddingService
   ) {
       /**
        * Индексировать всю базу знаний
        */
       suspend fun indexKnowledgeBase(basePath: String = "knowledge-base") {
           val categories = listOf("projects", "learning", "personal", "references")
           
           categories.forEach { category ->
               val categoryPath = Paths.get(basePath, category)
               if (Files.exists(categoryPath)) {
                   indexCategory(category, categoryPath.toString())
               }
           }
       }
       
       /**
        * Поиск по категории
        */
       suspend fun searchInCategory(
           query: String,
           category: String? = null,
           limit: Int = 5
       ): List<RetrievedChunk> {
           // RAG поиск с фильтрацией по категории
       }
       
       /**
        * Автоматическое обновление индекса при изменении файлов
        */
       fun watchForChanges(basePath: String) {
           // File watcher для автоматической переиндексации
       }
   }
   ```

3. **Категории документов** (`domain/model/DocumentCategory.kt`):
   ```kotlin
   enum class DocumentCategory {
       PROJECTS,
       LEARNING,
       PERSONAL,
       REFERENCES
   }
   ```

4. **API для управления базой знаний** (`presentation/controller/KnowledgeBaseController.kt`):
   ```kotlin
   class KnowledgeBaseController(
       private val knowledgeBaseService: KnowledgeBaseService
   ) {
       fun Application.knowledgeBaseRoutes() {
           route("/api/knowledge-base") {
               post("/index") {
                   knowledgeBaseService.indexKnowledgeBase()
                   call.respond(mapOf("status" to "indexed"))
               }
               
               get("/search") {
                   val query = call.request.queryParameters["query"] ?: ""
                   val category = call.request.queryParameters["category"]
                   val results = knowledgeBaseService.searchInCategory(query, category)
                   call.respond(results)
               }
               
               get("/categories") {
                   val categories = listOf("projects", "learning", "personal", "references")
                   call.respond(categories)
               }
           }
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 5: Интеграция голосового ввода

**Задача:** добавить голосовой ввод из урока 31.

**Компоненты:**

1. **Скопировать компоненты из lesson-31**:
   - `SpeechRecognitionService` (Vosk)
   - `AudioConversionService` (ffmpeg)
   - `VoiceController` (API endpoints)

2. **Интеграция в главный чат** (`presentation/controller/ChatController.kt`):
   ```kotlin
   class ChatController(
       private val chatService: ChatService,
       private val speechRecognitionService: SpeechRecognitionService
   ) {
       fun Application.chatRoutes() {
           route("/api/chat") {
               post("/message") {
                   val request = call.receive<ChatRequest>()
                   val response = chatService.processMessage(request)
                   call.respond(response)
               }
               
               // Голосовой ввод
               post("/voice") {
                   val multipart = call.receiveMultipart()
                   var audioData: ByteArray? = null
                   
                   multipart.forEachPart { part ->
                       when (part) {
                           is PartData.FileItem -> {
                               audioData = part.streamProvider().readBytes()
                           }
                           else -> {}
                       }
                       part.dispose()
                   }
                   
                   if (audioData == null) {
                       call.respond(HttpStatusCode.BadRequest,
                           mapOf("error" to "No audio data"))
                       return@post
                   }
                   
                   // Распознавание речи
                   val recognizedText = speechRecognitionService.recognize(audioData)
                   
                   // Обработка как обычное сообщение
                   val request = ChatRequest(
                       message = recognizedText,
                       sessionId = call.request.queryParameters["sessionId"]
                   )
                   val response = chatService.processMessage(request)
                   
                   call.respond(mapOf(
                       "recognizedText" to recognizedText,
                       "response" to response
                   ))
               }
           }
       }
   }
   ```

3. **Обновление UI** (`client/index.html`):
   - Добавить кнопку микрофона в чат
   - Интегрировать Web Audio API
   - Отправка аудио на `/api/chat/voice`

**После коммита:** остановиться.

---

### Коммит 6: Analytics MCP сервер

**Задача:** создать MCP сервер для анализа данных из разных источников.

**Компоненты:**

1. **Analytics MCP Server** (`mcp-servers/analytics-mcp/`):
   ```kotlin
   class AnalyticsMCPServer : BaseMCPServer() {
       override fun getTools(): List<Tool> {
           return listOf(
               Tool(
                   name = "analyze_csv",
                   description = "Анализ данных из CSV файла",
                   inputSchema = mapOf(
                       "file_path" to "string",
                       "query" to "string"
                   )
               ),
               Tool(
                   name = "analyze_json",
                   description = "Анализ данных из JSON файла",
                   inputSchema = mapOf(
                       "file_path" to "string",
                       "query" to "string"
                   )
               ),
               Tool(
                   name = "analyze_database",
                   description = "Анализ данных из SQLite базы",
                   inputSchema = mapOf(
                       "db_path" to "string",
                       "query" to "string"
                   )
               ),
               Tool(
                   name = "get_data_summary",
                   description = "Получить сводку по данным",
                   inputSchema = mapOf(
                       "data_source" to "string"
                   )
               )
           )
       }
       
       override suspend fun handleToolCall(toolName: String, arguments: Map<String, Any>): ToolResult {
           return when (toolName) {
               "analyze_csv" -> analyzeCSV(
                   arguments["file_path"] as String,
                   arguments["query"] as String
               )
               "analyze_json" -> analyzeJSON(
                   arguments["file_path"] as String,
                   arguments["query"] as String
               )
               "analyze_database" -> analyzeDatabase(
                   arguments["db_path"] as String,
                   arguments["query"] as String
               )
               "get_data_summary" -> getDataSummary(
                   arguments["data_source"] as String
               )
               else -> ToolResult(success = false, error = "Unknown tool")
           }
       }
       
       private suspend fun analyzeCSV(filePath: String, query: String): ToolResult {
           // Чтение CSV, анализ через LLM
           val data = readCSV(filePath)
           val analysis = llmService.analyzeData(data, query)
           return ToolResult(success = true, data = analysis)
       }
   }
   ```

2. **Интеграция в MCP Router**:
   - Добавить Analytics MCP в список доступных серверов
   - Обновить конфигурацию

**После коммита:** остановиться.

---

### Коммит 7: Унифицированный God Agent Service

**Задача:** создать главный сервис, который объединяет все компоненты.

**Компоненты:**

1. **God Agent Service** (`domain/service/GodAgentService.kt`):
   ```kotlin
   class GodAgentService(
       private val mcpRouterService: MCPRouterService,
       private val knowledgeBaseService: KnowledgeBaseService,
       private val requestRouterService: RequestRouterService,
       private val chatService: ChatService,
       private val userProfileService: UserProfileService,
       private val llmService: LLMService
   ) {
       /**
        * Главный метод обработки запроса пользователя
        */
       suspend fun processUserRequest(
           message: String,
           sessionId: String,
           userId: String
       ): ChatResponse {
           // 1. Получить профиль пользователя
           val userProfile = userProfileService.getUserProfile(userId)
           
           // 2. Получить историю диалога
           val chatHistory = chatService.getChatHistory(sessionId)
           
           // 3. Роутинг запроса
           val routingDecision = requestRouterService.routeRequest(message, chatHistory)
           
           // 4. Выполнить действия
           val context = when (routingDecision.action) {
               RoutingAction.MCP_TOOLS -> executeMCPTools(routingDecision.tools)
               RoutingAction.RAG_SEARCH -> performRAGSearch(message, routingDecision.category)
               RoutingAction.ANALYTICS -> performAnalytics(message, routingDecision.dataSource)
               RoutingAction.DIRECT_ANSWER -> null
           }
           
           // 5. Сформировать промпт с учетом контекста и персонализации
           val prompt = buildPersonalizedPrompt(
               message = message,
               context = context,
               userProfile = userProfile,
               chatHistory = chatHistory
           )
           
           // 6. Генерация ответа через LLM
           val response = llmService.generateResponse(prompt)
           
           // 7. Сохранение в историю
           chatService.saveMessage(sessionId, message, response, userId)
           
           return ChatResponse(
               message = response,
               sources = extractSources(context),
               toolsUsed = routingDecision.tools.map { it.toolName }
           )
       }
       
       private suspend fun executeMCPTools(tools: List<ToolCall>): String {
           val results = tools.map { tool ->
               mcpRouterService.executeTool(
                   serverName = tool.server,
                   toolName = tool.tool,
                   arguments = tool.args
               )
           }
           return results.joinToString("\n") { it.data.toString() }
       }
       
       private suspend fun performRAGSearch(
           query: String,
           category: String?
       ): String {
           val chunks = knowledgeBaseService.searchInCategory(query, category)
           return chunks.joinToString("\n\n") { chunk ->
               "[${chunk.source}] ${chunk.text}"
           }
       }
       
       private suspend fun performAnalytics(
           query: String,
           dataSource: String?
       ): String {
           val result = mcpRouterService.executeTool(
               serverName = "analytics",
               toolName = "analyze_data",
               arguments = mapOf(
                   "data_source" to (dataSource ?: "default"),
                   "query" to query
               )
           )
           return result.data.toString()
       }
       
       private fun buildPersonalizedPrompt(
           message: String,
           context: String?,
           userProfile: UserProfile,
           chatHistory: List<ChatMessage>
       ): String {
           return """
           Ты — персональный AI-помощник пользователя ${userProfile.name}.
           
           Профиль пользователя:
           - Стиль общения: ${userProfile.communicationStyle}
           - Предпочтения: ${userProfile.preferences.joinToString(", ")}
           - Контекст работы: ${userProfile.workContext}
           
           ${if (context != null) "Контекст:\n$context\n" else ""}
           
           История диалога:
           ${chatHistory.takeLast(5).joinToString("\n") { "${it.role}: ${it.content}" }}
           
           Запрос пользователя: $message
           
           Ответь на запрос, учитывая профиль пользователя и контекст.
           """.trimIndent()
       }
   }
   ```

2. **Обновление ChatController**:
   ```kotlin
   class ChatController(
       private val godAgentService: GodAgentService
   ) {
       fun Application.chatRoutes() {
           route("/api/chat") {
               post("/message") {
                   val request = call.receive<ChatRequest>()
                   val response = godAgentService.processUserRequest(
                       message = request.message,
                       sessionId = request.sessionId ?: generateSessionId(),
                       userId = request.userId ?: "default"
                   )
                   call.respond(response)
               }
           }
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 8: UI для управления MCP серверами

**Задача:** создать UI для управления MCP серверами и базой знаний.

**Компоненты:**

1. **Страница настроек** (`client/settings.html`):
   ```html
   <div class="settings-page">
       <h1>Настройки God Agent</h1>
       
       <!-- MCP Серверы -->
       <section class="mcp-servers">
           <h2>MCP Серверы (Плагины)</h2>
           <div id="mcpServersList"></div>
           <button onclick="addMCPServer()">Добавить сервер</button>
       </section>
       
       <!-- База знаний -->
       <section class="knowledge-base">
           <h2>База знаний</h2>
           <button onclick="indexKnowledgeBase()">Переиндексировать</button>
           <div id="knowledgeBaseStats"></div>
       </section>
       
       <!-- Профиль пользователя -->
       <section class="user-profile">
           <h2>Профиль пользователя</h2>
           <form id="profileForm">
               <!-- Поля профиля -->
           </form>
       </section>
   </div>
   ```

2. **JavaScript для управления** (`client/settings.js`):
   ```javascript
   async function loadMCPServers() {
       const response = await fetch('/api/mcp/servers');
       const servers = await response.json();
       
       const list = document.getElementById('mcpServersList');
       list.innerHTML = servers.map(server => `
           <div class="mcp-server-card">
               <h3>${server.name}</h3>
               <p>${server.description}</p>
               <label>
                   <input type="checkbox" 
                          ${server.enabled ? 'checked' : ''}
                          onchange="toggleMCPServer('${server.name}', this.checked)">
                   Включен
               </label>
           </div>
       `).join('');
   }
   
   async function indexKnowledgeBase() {
       const response = await fetch('/api/knowledge-base/index', {
           method: 'POST'
       });
       const result = await response.json();
       alert('База знаний проиндексирована!');
   }
   ```

**После коммита:** остановиться.

---

### Коммит 9: Примеры контента и документация

**Задача:** создать примеры контента для базы знаний и документацию.

**Компоненты:**

1. **Примеры документов** (`knowledge-base/examples/`):
   - `projects/example-project/README.md`
   - `learning/ai-notes.md`
   - `personal/goals-2024.md`
   - `references/articles/example.md`

2. **Документация** (`docs/`):
   - `MCP_SERVERS.md` — как создавать свои MCP серверы
   - `KNOWLEDGE_BASE.md` — как организовать базу знаний
   - `ANALYTICS.md` — как использовать аналитику
   - `PERSONALIZATION.md` — настройка персонализации

3. **README.md** — обновить с полным описанием

**После коммита:** финал.

---

## Технические детали

### Архитектура

```
┌─────────────────────────────────────────┐
│         God Agent (Единый UI)           │
│  ┌──────────┐  ┌──────────┐  ┌────────┐│
│  │   Чат    │  │ База знаний│ │Аналитика││
│  │ (текст+  │  │   (RAG)   │  │ (данные)││
│  │ голос)   │  │           │  │         ││
│  └──────────┘  └──────────┘  └────────┘│
└─────────────────────────────────────────┘
           │              │
           ▼              ▼
    ┌──────────────┐  ┌──────────────┐
    │  MCP Router  │  │  RAG Engine  │
    │  (плагины)   │  │  (поиск)     │
    └──────────────┘  └──────────────┘
           │
           ▼
    ┌─────────────────────────┐
    │   MCP Servers (плагины) │
    ├─────────────────────────┤
    │ • Git MCP               │
    │ • Telegram MCP          │
    │ • Analytics MCP         │
    │ • File System MCP       │
    │ • Calendar MCP          │
    │ • Custom MCP...         │
    └─────────────────────────┘
           │
           ▼
    ┌──────────────┐
    │ Local LLM    │
    │   (VPS)      │
    └──────────────┘
```

### Конфигурация

**config/server.yaml:**
```yaml
god_agent:
  enabled: true
  
  mcp_servers:
    config_path: "config/mcp-servers.yaml"
  
  knowledge_base:
    base_path: "knowledge-base"
    auto_index: true
    watch_changes: true
  
  personalization:
    enabled: true
    profile_path: "data/user-profile.json"
  
  voice:
    enabled: true
    vosk_model_path: "models/vosk-model-small-ru-0.22"
  
  local_llm:
    enabled: true
    provider: "ollama"
    base_url: "https://your-vps.com"
    model: "llama3.2"
```

### Примеры использования

1. **"Найди информацию о проекте X"**
   - RAG ищет в `knowledge-base/projects/`
   - Git MCP читает файлы проекта
   - Возвращает ответ с источниками

2. **"Проанализируй метрики за последний месяц"**
   - Analytics MCP анализирует `data/analytics/metrics.csv`
   - LLM генерирует отчет
   - Визуализация результатов

3. **"Напомни мне о встрече завтра"**
   - Calendar MCP создает напоминание
   - Telegram MCP отправляет сообщение

4. **"Что я писал про архитектуру?"**
   - RAG ищет в `knowledge-base/learning/`
   - Возвращает релевантные фрагменты

## Риски и рекомендации

- **MCP серверы:** убедиться, что все серверы правильно настроены
- **База знаний:** регулярно индексировать новые документы
- **Персонализация:** обновлять профиль пользователя
- **Производительность:** кэшировать результаты RAG поиска
- **Безопасность:** ограничить доступ к файловой системе через File System MCP

