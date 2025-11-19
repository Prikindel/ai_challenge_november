# Промпт для реализации: 🔥 День 12. Планировщик + MCP

Ты — разработчик, создающий урок «🔥 День 12. Планировщик + MCP» в модуле `lesson-12-reminder-mcp`. Проект использует стек **Kotlin + Ktor + Clean Architecture + SQLite** с фронтендом на **vanilla JS**. Твоя задача — построить полностью самостоятельный модуль без упоминаний других уроков в коде и документации. Используй **официальный Kotlin SDK для MCP** (`io.modelcontextprotocol:kotlin-sdk`) для создания собственного MCP сервера.

## 🎯 Цель урока

Создать систему автоматической суммаризации данных из разных источников:
- **Источник 1: Веб-чат** — история переписки из lesson-09 (memory.db)
- **Источник 2: Telegram группа** — сообщения из Telegram группы (сохраняются в БД)
- Планировщик автоматически анализирует данные за период и генерирует summary через LLM
- Summary отправляется лично пользователю в Telegram от бота (как в уроке 11)

### Архитектура:

```
Планировщик (каждые N минут)
    ↓
Вызывает LLM: "Проанализируй данные из источника X за период Y-Z"
    ↓
LLM решает: нужны данные из источника
    ↓
LLM вызывает MCP инструмент: get_chat_history() или get_telegram_messages()
    ↓
MCP инструмент читает из БД:
  - Веб-чат → memory.db (lesson-09)
  - Telegram → summary.db (telegram_messages таблица)
    ↓
MCP возвращает данные LLM
    ↓
LLM анализирует и генерирует summary
    ↓
Сохраняем summary в БД (summaries таблица)
    ↓
Отправляем summary лично пользователю в Telegram от бота
```

---

## 📋 Поэтапная реализация (отдельные коммиты)

**ВАЖНО:** Реализуй пошагово, каждый шаг — отдельный git коммит. После каждого шага покажи изменения пользователю, он закоммитит и скажет "приступай к следующему шагу".

### Коммит 1: MCP Server - веб-чат инструмент
- Создать MCP сервер с инструментом `get_chat_history(startTime, endTime)`
- Инструмент читает из БД lesson-09 (memory.db) — используй подход из `SqliteMemoryRepository`
- Возвращает JSON с историей сообщений за период
- Тесты инструмента

### Коммит 2: MCP Server - Telegram инструмент + Telegram Bot
- Telegram Bot Client (получение сообщений через polling/webhook)
- Сохранение сообщений в БД (таблица `telegram_messages` в summary.db)
- MCP инструмент `get_telegram_messages(groupId, startTime, endTime)` — читает из БД
- Telegram Bot для отправки summary пользователю (как в уроке 11)
- Тесты

### Коммит 3: Основное приложение - MCP клиент
- MCPClient (подключение к MCP серверам)
- Конфигурация источников (YAML)
- API для получения списка доступных инструментов
- Подключение к MCP серверам при старте

### Коммит 4: LLM интеграция
- LLMWithSummaryAgent (интеграция с LLM)
- Системный промпт с информацией об инструментах
- Вызов инструментов через LLM (function calling или парсинг)
- Обработка результатов и формирование ответа

### Коммит 5: Планировщик
- SchedulerService (корутины, автоматический запуск при старте)
- Автоматическая генерация summary по расписанию
- Сохранение summary в БД
- Отправка summary в Telegram пользователю

### Коммит 6: UI - настройки и отображение
- Настройки источников (выбор активного источника, частота)
- Отображение summary в веб-чате
- История summary
- Статус планировщика

---

## ⚙️ Конфигурация

### Структура проекта

```
lesson-12-reminder-mcp/
├── README.md
├── THEORY.md
├── example.env
├── telegram_messages_example.txt  # Примеры сообщений для тестирования
├── config/
│   ├── summary.yaml
│   └── summary.yaml.example
├── mcp-server/
│   └── src/main/kotlin/com/prike/mcpserver/...
├── server/
│   └── src/main/kotlin/com/prike/...
└── client/
    ├── index.html
    ├── style.css
    └── app.js
```

### Конфиг (`config/summary.yaml`)

```yaml
summary:
  # Активный источник данных (один в момент, но можно и два)
  activeSource: "web_chat"  # или "telegram", "both"
  
  # Настройки веб-чата
  webChat:
    enabled: true
    memoryDbPath: "../lesson-09-external-memory/data/memory.db"  # путь к БД lesson-09
  
  # Настройки Telegram
  telegram:
    enabled: false
    botToken: "${TELEGRAM_BOT_TOKEN}"  # из .env
    groupId: "${TELEGRAM_GROUP_ID}"    # из .env
    databasePath: "data/summary.db"
  
  # Планировщик
  scheduler:
    enabled: true
    intervalMinutes: 15  # как часто генерировать summary
    periodHours: 24      # за какой период анализировать (последние N часов)
  
  # LLM
  llm:
    systemPrompt: |
      Ты — ассистент для анализа и суммаризации данных.
      У тебя есть доступ к инструментам через MCP:
      - get_chat_history(startTime, endTime): получить историю веб-чата
      - get_telegram_messages(groupId, startTime, endTime): получить сообщения из Telegram
      
      Когда тебя просят проанализировать данные, используй соответствующий инструмент,
      получи данные, проанализируй их и создай понятную сводку.
  
  # Доставка summary
  delivery:
    telegram:
      enabled: true
      userId: "${TELEGRAM_USER_ID}"  # ID пользователя для отправки (из .env)
    webChat:
      enabled: true
```

### Переменные окружения (`example.env`)

```env
OPENAI_API_KEY=your_openai_api_key
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_GROUP_ID=your_telegram_group_id
TELEGRAM_USER_ID=your_telegram_user_id  # твой личный ID для получения summary
```

### Файл с примерами сообщений (`telegram_messages_example.txt`)

Создай файл с примерами сообщений для тестирования Telegram функциональности:

```
Пользователь 1 (Иван):
Привет! Как дела с проектом?

Пользователь 2 (Мария):
Всё отлично! Завершили интеграцию с MCP.

Пользователь 1 (Иван):
Отлично! Когда планируем релиз?

Пользователь 2 (Мария):
Думаю, через неделю. Нужно ещё протестировать.

Пользователь 1 (Иван):
Понял. Давай созвонимся завтра в 10:00 для обсуждения.

Пользователь 2 (Мария):
Хорошо, записала в календарь.
```

**Формат:** Простой текст, можно использовать для ручного тестирования (отправить эти сообщения в Telegram группу).

---

## 🧠 Реализация MCP Server (`mcp-server/`)

### Коммит 1: Веб-чат инструмент

**Структура:**
```
mcp-server/
├── src/main/kotlin/com/prike/mcpserver/
│   ├── Main.kt
│   ├── server/
│   │   └── MCPSummaryServer.kt
│   ├── tools/
│   │   ├── ToolRegistry.kt
│   │   └── ChatHistoryTool.kt
│   ├── data/
│   │   └── repository/
│   │       └── ChatHistoryRepository.kt  # Чтение из memory.db
│   └── config/
│       └── MCPConfig.kt
```

**ChatHistoryTool.kt:**
```kotlin
// MCP инструмент: get_chat_history
server.addTool(
    name = "get_chat_history",
    description = "Получить историю переписки из веб-чата за указанный период"
) { request ->
    val startTime = request.params.arguments?.get("startTime") as? Long
        ?: throw IllegalArgumentException("startTime is required")
    val endTime = request.params.arguments?.get("endTime") as? Long
        ?: throw IllegalArgumentException("endTime is required")
    
    // Читаем из БД lesson-09 (memory.db)
    val messages = chatHistoryRepository.getMessagesBetween(startTime, endTime)
    
    CallToolResult(
        content = listOf(
            TextContent(text = messages.toJson())
        )
    )
}
```

**ChatHistoryRepository.kt:**
- Используй подход из `SqliteMemoryRepository` (lesson-09)
- Читай из `memory_entries` таблицы
- Фильтруй по `timestamp` между startTime и endTime

### Коммит 2: Telegram инструмент + Telegram Bot

**Структура:**
```
mcp-server/
├── src/main/kotlin/com/prike/mcpserver/
│   ├── tools/
│   │   └── TelegramMessagesTool.kt
│   ├── data/
│   │   ├── repository/
│   │   │   └── TelegramMessageRepository.kt  # Сохранение/чтение из БД
│   │   └── model/
│   │       └── TelegramMessage.kt
│   └── telegram/
│       ├── TelegramBotClient.kt  # Получение сообщений
│       └── TelegramBotService.kt  # Отправка summary пользователю
```

**TelegramMessageRepository.kt:**
- Создай таблицу `telegram_messages` в summary.db:
```sql
CREATE TABLE telegram_messages (
    id TEXT PRIMARY KEY,
    message_id INTEGER NOT NULL,  -- ID сообщения в Telegram
    group_id TEXT NOT NULL,
    content TEXT NOT NULL,
    author TEXT,
    timestamp INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
```

**TelegramBotClient.kt:**
- Используй библиотеку для Telegram Bot API (например, `com.github.pengrad:java-telegram-bot-api`)
- Long polling для получения новых сообщений
- При получении сообщения → сохраняй в БД через TelegramMessageRepository

**TelegramMessagesTool.kt:**
```kotlin
// MCP инструмент: get_telegram_messages
server.addTool(
    name = "get_telegram_messages",
    description = "Получить сообщения из Telegram группы за указанный период"
) { request ->
    val groupId = request.params.arguments?.get("groupId") as? String
        ?: throw IllegalArgumentException("groupId is required")
    val startTime = request.params.arguments?.get("startTime") as? Long
        ?: throw IllegalArgumentException("startTime is required")
    val endTime = request.params.arguments?.get("endTime") as? Long
        ?: throw IllegalArgumentException("endTime is required")
    
    val messages = telegramMessageRepository.getMessagesBetween(groupId, startTime, endTime)
    
    CallToolResult(
        content = listOf(
            TextContent(text = messages.toJson())
        )
    )
}
```

**TelegramBotService.kt:**
- Метод для отправки summary пользователю (как в уроке 11)
- Используй Telegram Bot API для отправки сообщения по userId

---

## 🧠 Реализация основного приложения (`server/`)

### Коммит 3: MCP клиент

**MCPClient.kt:**
- Подключение к MCP серверам (запуск процесса JAR)
- Методы: `listTools()`, `callTool(name, arguments)`
- Поддержка подключения к разным MCP серверам (веб-чат, Telegram)

**MCPConnectionController.kt:**
- API для подключения к MCP серверам
- Загрузка конфигурации из YAML

### Коммит 4: LLM интеграция

**LLMWithSummaryAgent.kt:**
```kotlin
class LLMWithSummaryAgent(
    private val mcpClient: MCPClient,
    private val aiRepository: AIRepository
) {
    suspend fun processUserMessage(userMessage: String): AgentResponse {
        // 1. Получаем список инструментов от MCP
        val tools = mcpClient.listTools()
        
        // 2. Формируем системный промпт
        val systemPrompt = buildSystemPromptWithTools(tools)
        
        // 3. Отправляем запрос LLM
        val llmResponse = aiRepository.getMessage(systemPrompt, userMessage)
        
        // 4. Парсим вызов инструмента
        val toolCall = parseToolCallFromLLM(llmResponse)
        
        if (toolCall != null) {
            // 5. Вызываем инструмент
            val toolResult = mcpClient.callTool(toolCall.name, toolCall.arguments)
            
            // 6. Отправляем результат обратно LLM
            val finalResponse = aiRepository.getMessage(
                systemPrompt,
                "Пользователь спросил: $userMessage\n" +
                "Я вызвал инструмент ${toolCall.name} и получил: $toolResult\n" +
                "Обработай результат и дай понятный ответ."
            )
            
            return AgentResponse.Success(finalResponse, toolCall.name, toolResult)
        }
        
        return AgentResponse.Success(llmResponse, null, null)
    }
    
    suspend fun generateSummary(source: String, startTime: Long, endTime: Long): String {
        // Аналогично, но для автоматической генерации summary
        // Вызывает соответствующий MCP инструмент
        // LLM формирует summary
    }
}
```

### Коммит 5: Планировщик

**SchedulerService.kt:**
```kotlin
class SchedulerService(
    private val llmWithSummaryAgent: LLMWithSummaryAgent,
    private val summaryRepository: SummaryRepository,
    private val telegramBotService: TelegramBotService,
    private val config: SummaryConfig
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun start() {
        scope.launch {
            while (isActive) {
                try {
                    // 1. Определяем активный источник
                    val activeSource = config.activeSource
                    
                    // 2. Вычисляем период
                    val endTime = System.currentTimeMillis()
                    val startTime = endTime - (config.scheduler.periodHours * 3600 * 1000)
                    
                    // 3. Генерируем summary через LLM
                    val summaryText = llmWithSummaryAgent.generateSummary(
                        source = activeSource,
                        startTime = startTime,
                        endTime = endTime
                    )
                    
                    // 4. Сохраняем в БД
                    val summary = Summary(
                        id = UUID.randomUUID().toString(),
                        source = activeSource,
                        periodStart = startTime,
                        periodEnd = endTime,
                        summaryText = summaryText,
                        generatedAt = System.currentTimeMillis()
                    )
                    summaryRepository.save(summary)
                    
                    // 5. Отправляем в Telegram пользователю
                    if (config.delivery.telegram.enabled) {
                        telegramBotService.sendSummaryToUser(
                            userId = config.delivery.telegram.userId,
                            summary = summaryText
                        )
                    }
                    
                    logger.info("Summary generated for source: $activeSource")
                } catch (e: Exception) {
                    logger.error("Error generating summary", e)
                }
                
                // Ждём до следующего запуска
                delay(config.scheduler.intervalMinutes * 60 * 1000L)
            }
        }
    }
    
    fun stop() {
        scope.cancel()
    }
}
```

**SummaryRepository.kt:**
- Сохранение summary в БД (таблица `summaries` в summary.db)
- Чтение истории summary

### Коммит 6: UI

**index.html:**
- Настройки источников (выбор активного источника, частота)
- Отображение summary в веб-чате
- История summary (карточки с временем, содержанием)
- Статус планировщика (вкл/выкл, последний запуск)

**app.js:**
- Загрузка настроек из конфига
- Отображение summary
- Обновление истории summary (polling)

---

## 🗄️ Структура БД

### summary.db (одна БД, разные таблицы)

```sql
-- Сообщения из Telegram (если источник = telegram)
CREATE TABLE telegram_messages (
    id TEXT PRIMARY KEY,
    message_id INTEGER NOT NULL,
    group_id TEXT NOT NULL,
    content TEXT NOT NULL,
    author TEXT,
    timestamp INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_telegram_timestamp ON telegram_messages(timestamp);
CREATE INDEX idx_telegram_group ON telegram_messages(group_id);

-- Сгенерированные summary
CREATE TABLE summaries (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,  -- 'web_chat', 'telegram', 'both'
    period_start INTEGER NOT NULL,
    period_end INTEGER NOT NULL,
    summary_text TEXT NOT NULL,
    message_count INTEGER NOT NULL,
    generated_at INTEGER NOT NULL,
    delivered_to_telegram BOOLEAN DEFAULT 0,
    llm_model TEXT
);

CREATE INDEX idx_summaries_generated_at ON summaries(generated_at);
CREATE INDEX idx_summaries_source ON summaries(source);
```

**Примечание:** Для веб-чата используем существующую БД lesson-09 (memory.db), не создаём дубликат.

---

## ✅ Финальная проверка

1. **Коммит 1:** Собери MCP сервер, протестируй инструмент `get_chat_history` отдельно
2. **Коммит 2:** Настрой Telegram бота, отправь тестовые сообщения, проверь сохранение в БД
3. **Коммит 3:** Подключись к MCP серверам, получи список инструментов
4. **Коммит 4:** Протестируй вызов инструментов через LLM
5. **Коммит 5:** Запусти планировщик, дождись генерации summary, проверь отправку в Telegram
6. **Коммит 6:** Проверь UI, настройки, отображение summary

---

## 🎯 Итоговый результат

1. ✅ MCP сервер с инструментами для веб-чата и Telegram
2. ✅ Telegram бот получает сообщения и сохраняет в БД
3. ✅ LLM агент знает о инструментах и вызывает их
4. ✅ Планировщик автоматически генерирует summary
5. ✅ Summary отправляется лично пользователю в Telegram
6. ✅ UI для настройки и просмотра summary
7. ✅ THEORY.md с объяснением концепций

**Важно:** 
- Реализуй пошагово, каждый коммит отдельно
- После каждого коммита покажи изменения пользователю
- Используй существующую БД lesson-09 для веб-чата
- Summary отправляется лично в Telegram (не в группу)

---

## 📝 Дополнительные рекомендации

- Используй подход из `SqliteMemoryRepository` для работы с БД
- Для Telegram Bot API используй проверенную библиотеку
- Обрабатывай ошибки подключения к MCP серверам
- Логируй все операции планировщика
- Учти таймзоны при работе с временем
- Создай файл `telegram_messages_example.txt` с примерами сообщений

**Готово к реализации!** 🚀
