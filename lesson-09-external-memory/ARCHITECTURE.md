# 🏗️ Архитектура урока 9: Внешняя память

## 1. Что мы храним в БД/JSON

### Структура записи (MemoryEntry)

Каждое сообщение в диалоге сохраняется как запись `MemoryEntry`:

```kotlin
data class MemoryEntry(
    val id: String,                    // Уникальный ID (UUID)
    val role: MessageRole,             // USER или ASSISTANT
    val content: String,               // Текст сообщения
    val timestamp: Long,               // Время создания (миллисекунды)
    val metadata: MemoryMetadata?      // Метаданные (опционально)
)
```

### Пример данных в БД:

**Сообщение пользователя:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "role": "USER",
  "content": "Привет! Как дела?",
  "timestamp": 1700000000000,
  "metadata": null
}
```

**Ответ ассистента:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "role": "ASSISTANT",
  "content": "Привет! У меня всё отлично, спасибо!",
  "timestamp": 1700000001000,
  "metadata": {
    "model": "meta-llama/llama-3.1-8b-instruct",
    "promptTokens": 45,
    "completionTokens": 12,
    "totalTokens": 57
  }
}
```

### Что хранится:
- ✅ **Все сообщения пользователя** (role = USER)
- ✅ **Все ответы ассистента** (role = ASSISTANT)
- ✅ **Временные метки** (когда отправлено/получено)
- ✅ **Метаданные** (токены, модель LLM) - только для ответов ассистента
- ✅ **История диалога** - позволяет продолжить разговор после перезапуска

---

## 2. Процесс запросов (от пользователя до ответа)

### Полный цикл обработки сообщения:

```
Пользователь → API → MemoryOrchestrator → ConversationAgent → LLM API
                ↓
           MemoryService → MemoryRepository → БД/JSON
                ↑
Пользователь ← API ← MemoryOrchestrator ← ConversationAgent ← LLM API
```

### Детальный процесс:

```
1. ПОЛЬЗОВАТЕЛЬ отправляет сообщение
   POST /api/memory/message
   { "message": "Привет!" }
   
2. MemoryController получает запрос
   ↓
   
3. MemoryOrchestrator.handleMessage("Привет!")
   ├─ 3.1. Загружает историю из памяти (MemoryService.getHistory())
   │      → MemoryRepository.loadAll() → БД/JSON
   │
   ├─ 3.2. Создает запись для сообщения пользователя
   │      → MemoryService.createUserEntry("Привет!")
   │      → MemoryEntry(id="...", role=USER, content="Привет!")
   │
   ├─ 3.3. Преобразует историю в формат для LLM
   │      → MemoryService.toMessageDtos(history)
   │      → List<MessageDto> (для OpenAI API)
   │
   ├─ 3.4. Отправляет запрос в LLM через ConversationAgent
   │      → ConversationAgent.respond(messages)
   │      → AIRepository.getMessageWithHistory(messages)
   │      → OpenAIClient.getCompletionWithHistory(...)
   │      → OpenAI API
   │      ← Ответ от LLM
   │
   ├─ 3.5. Создает запись для ответа ассистента
   │      → MemoryService.createAssistantEntry(response.message, usage)
   │      → MemoryEntry(id="...", role=ASSISTANT, content="Привет!")
   │
   └─ 3.6. Сохраняет ОБА сообщения в память
          → MemoryService.saveEntries([userEntry, assistantEntry])
          → MemoryRepository.saveAll([...])
          → БД/JSON (INSERT/UPDATE)

4. Возврат ответа пользователю
   ← AgentResponse(message="Привет!", usage=...)
   ← HTTP 200 OK
```

---

## 3. Когда происходит сохранение и загрузка

### 📥 ЗАГРУЗКА из БД/JSON:

#### 1. При старте приложения (автоматически)

```kotlin
// Main.kt
fun Application.module() {
    val memoryController = AppModule.createMemoryController()
    
    // Инициализация оркестратора (загрузка истории)
    memoryController?.let {
        runBlocking {
            AppModule.createMemoryOrchestrator()?.initialize()
        }
    }
}

// MemoryOrchestrator.initialize()
suspend fun initialize() {
    memoryService.loadHistory()  // ← Загрузка из БД/JSON
}
```

**Происходит:**
- При запуске сервера
- Загружает всю историю диалога из хранилища
- Сохраняет в памяти (кэш) для быстрого доступа

#### 2. При обработке нового сообщения

```kotlin
// MemoryOrchestrator.handleMessage()
val history = memoryService.getHistory()  // ← Получает из кэша (не из БД!)
```

**Важно:** История берется из кэша (память приложения), а не каждый раз из БД!

### 💾 СОХРАНЕНИЕ в БД/JSON:

#### 1. После каждого сообщения пользователя

```kotlin
// MemoryOrchestrator.handleMessage()
// ... обработка сообщения ...

// 6. Сохранить оба сообщения в память
memoryService.saveEntries(listOf(userEntry, assistantEntry))
```

**Происходит:**
- После получения ответа от LLM
- Сохраняет ОБА сообщения:
  - Сообщение пользователя
  - Ответ ассистента

#### 2. При очистке памяти (reset)

```kotlin
// MemoryController - POST /api/memory/reset
orchestrator.reset()

// MemoryService.clear()
repository.clear()  // ← DELETE FROM memory_entries
```

### 📊 Получение статистики

```kotlin
// GET /api/memory/stats
orchestrator.getStats()

// MemoryRepository.getStats()
// SELECT COUNT(*), MIN(timestamp), MAX(timestamp) FROM memory_entries
```

---

## 4. Взаимодействие кода (схема работы)

### 🏛️ Архитектура (Clean Architecture):

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  (Ktor Controllers, DTO)                                    │
├─────────────────────────────────────────────────────────────┤
│  MemoryController                                           │
│    ├─ POST /api/memory/message                              │
│    ├─ GET  /api/memory/history                              │
│    ├─ POST /api/memory/reset                                │
│    └─ GET  /api/memory/stats                                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                     DOMAIN LAYER                             │
│  (Бизнес-логика, агенты, сервисы)                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  MemoryOrchestrator                                │    │
│  │  (Координация работы)                              │    │
│  │    ├─ initialize()     → загрузка истории          │    │
│  │    ├─ handleMessage()  → обработка сообщения       │    │
│  │    ├─ reset()          → сброс памяти              │    │
│  │    └─ getStats()       → статистика                │    │
│  └────────────────────────────────────────────────────┘    │
│                           ↓                    ↓            │
│  ┌─────────────────────┐    ┌─────────────────────────┐   │
│  │  ConversationAgent  │    │   MemoryService         │   │
│  │  (Работа с LLM)     │    │   (Управление памятью)  │   │
│  │    └─ respond()     │    │     ├─ loadHistory()    │   │
│  └─────────────────────┘    │     ├─ saveEntries()    │   │
│           ↓                  │     ├─ createUserEntry()│   │
│  ┌─────────────────────┐    │     ├─ createAssistant()│   │
│  │   AIRepository      │    │     └─ toMessageDtos()  │   │
│  │   (LLM API calls)   │    └─────────────────────────┘   │
│  └─────────────────────┘              ↓                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                             │
│  (Репозитории, модели, клиенты)                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  MemoryRepository (интерфейс)                       │   │
│  │    ├─ save()                                        │   │
│  │    ├─ saveAll()                                     │   │
│  │    ├─ loadAll()                                     │   │
│  │    ├─ findById()                                    │   │
│  │    ├─ findByDateRange()                            │   │
│  │    ├─ clear()                                       │   │
│  │    └─ getStats()                                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                           ↓                                  │
│  ┌────────────────────┐      ┌───────────────────────┐    │
│  │ SqliteMemoryRepo   │      │  JsonMemoryRepo       │    │
│  │ (SQLite БД)        │      │  (JSON файл)          │    │
│  └────────────────────┘      └───────────────────────┘    │
│                           ↓                                  │
│                    [БД/JSON файл]                           │
└─────────────────────────────────────────────────────────────┘
```

### 🔄 Последовательность вызовов:

#### Сценарий 1: Запуск приложения

```
1. Main.main()
   ↓
2. AppModule.createMemoryController()
   ↓
3. AppModule.createMemoryOrchestrator()
   ├─ AppModule.createConversationAgent()
   └─ AppModule.createMemoryService()
      └─ AppModule.createMemoryRepository()
         └─ MemoryRepositoryFactory.create()
            └─ SqliteMemoryRepository / JsonMemoryRepository
   ↓
4. MemoryOrchestrator.initialize()
   ↓
5. MemoryService.loadHistory()
   ↓
6. MemoryRepository.loadAll()
   ↓
7. SqliteMemoryRepository: SELECT * FROM memory_entries
   ↓
8. История загружена в кэш (MemoryService.cachedHistory)
```

#### Сценарий 2: Пользователь отправляет сообщение

```
1. POST /api/memory/message { "message": "Привет!" }
   ↓
2. MemoryController.handleMessage()
   ↓
3. MemoryOrchestrator.handleMessage("Привет!")
   
   3.1. MemoryService.getHistory()
        → Возвращает cachedHistory (не идет в БД!)
   
   3.2. MemoryService.createUserEntry("Привет!")
        → Создает MemoryEntry(role=USER, ...)
   
   3.3. MemoryService.toMessageDtos(history)
        → Преобразует MemoryEntry[] → MessageDto[]
   
   3.4. ConversationAgent.respond(allMessages)
        → AIRepository.getMessageWithHistory(messages)
        → OpenAIClient.getCompletionWithHistory(...)
        → OpenAI API
        ← Получает ответ от LLM
   
   3.5. MemoryService.createAssistantEntry(response.message, usage)
        → Создает MemoryEntry(role=ASSISTANT, ...)
   
   3.6. MemoryService.saveEntries([userEntry, assistantEntry])
        → MemoryRepository.saveAll([...])
        → SqliteMemoryRepository: INSERT INTO memory_entries ...
        → БД обновлена!
   
   3.7. Обновляется кэш: cachedHistory += [userEntry, assistantEntry]
   
4. Возврат ответа пользователю
```

### 📝 Ключевые моменты:

1. **Двухуровневое кэширование:**
   - `MemoryService.cachedHistory` - история в памяти приложения
   - БД/JSON - постоянное хранилище
   
2. **Загрузка из БД:**
   - Только при старте приложения
   - Загружается вся история сразу
   
3. **Сохранение в БД:**
   - После каждого сообщения
   - Сохраняются оба сообщения (user + assistant)
   - Атомарно (транзакция)
   
4. **Разделение ответственности:**
   - `MemoryOrchestrator` - только координация
   - `MemoryService` - бизнес-логика работы с памятью
   - `MemoryRepository` - работа с хранилищем (БД/JSON)
   - `ConversationAgent` - работа с LLM API

### 🎯 Пример полного цикла:

```
┌─────────────────────────────────────────────────────────────┐
│                    ПРИМЕР ПОЛНОГО ЦИКЛА                      │
└─────────────────────────────────────────────────────────────┘

ЗАПУСК ПРИЛОЖЕНИЯ:
──────────────────
1. Сервер стартует
2. MemoryOrchestrator.initialize()
3. Загрузка истории из БД:
   - Загружено 10 сообщений из прошлой сессии
   - История загружена в кэш

ПЕРВОЕ СООБЩЕНИЕ:
─────────────────
Пользователь: "Привет!"

1. MemoryOrchestrator.handleMessage("Привет!")
2. getHistory() → возвращает 10 старых сообщений (из кэша)
3. Создана запись: userEntry (id="abc", role=USER, content="Привет!")
4. Отправка в LLM:
   [
     {role: "user", content: "Старое сообщение 1"},
     {role: "assistant", content: "Старый ответ 1"},
     ...
     {role: "user", content: "Привет!"}  ← новое сообщение
   ]
5. LLM отвечает: "Привет! Как дела?"
6. Создана запись: assistantEntry (id="def", role=ASSISTANT, ...)
7. Сохранение в БД:
   - INSERT userEntry
   - INSERT assistantEntry
   - Теперь в БД 12 записей (было 10, стало 12)

ВТОРОЕ СООБЩЕНИЕ:
─────────────────
Пользователь: "Расскажи анекдот"

1. MemoryOrchestrator.handleMessage("Расскажи анекдот")
2. getHistory() → возвращает 12 сообщений (из кэша, включая предыдущие)
3. ... (аналогично)
4. Сохранение → теперь в БД 14 записей

ПЕРЕЗАПУСК:
───────────
1. Сервер перезапускается
2. MemoryOrchestrator.initialize()
3. Загрузка из БД:
   - Загружено 14 сообщений
   - История восстановлена!
   - Пользователь может продолжить диалог с контекстом
```

---

## 📦 Хранение данных

### SQLite (по умолчанию):

**Таблица `memory_entries`:**
```sql
CREATE TABLE memory_entries (
    id TEXT PRIMARY KEY,
    role TEXT NOT NULL,              -- "USER" или "ASSISTANT"
    content TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    model TEXT,                      -- Модель LLM
    prompt_tokens INTEGER,           -- Токены в промпте
    completion_tokens INTEGER,       -- Токены в ответе
    total_tokens INTEGER             -- Всего токенов
);

CREATE INDEX idx_timestamp ON memory_entries(timestamp);
```

**Файл:** `data/memory.db`

### JSON (альтернатива):

**Структура файла:**
```json
{
  "entries": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "role": "USER",
      "content": "Привет!",
      "timestamp": 1700000000000,
      "metadata": null
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "role": "ASSISTANT",
      "content": "Привет! Как дела?",
      "timestamp": 1700000001000,
      "metadata": {
        "model": "meta-llama/llama-3.1-8b-instruct",
        "promptTokens": 45,
        "completionTokens": 12,
        "totalTokens": 57
      }
    }
  ]
}
```

**Файл:** `data/memory.json`

---

## ✅ Итоговая схема:

```
┌──────────────┐
│  Пользователь│
└──────┬───────┘
       │ POST /api/memory/message
       ↓
┌──────────────────┐
│ MemoryController │  ← HTTP endpoint
└──────┬───────────┘
       │
       ↓
┌──────────────────────┐
│ MemoryOrchestrator   │  ← Координатор
│  ┌────────────────┐  │
│  │ 1. getHistory()│  │  ← Из кэша
│  │ 2. createUser()│  │
│  │ 3. toMessageDtos│ │
│  │ 4. Conversation│  │  ← Запрос в LLM
│  │    Agent       │  │
│  │ 5. createAssist│  │
│  │ 6. saveEntries │  │  ← Сохранение в БД
│  └────────────────┘  │
└──────┬───────────────┘
       │
       ↓
┌──────────────────┐
│ MemoryService    │  ← Бизнес-логика
│  - cachedHistory │  ← Кэш в памяти
└──────┬───────────┘
       │
       ↓
┌──────────────────┐
│ MemoryRepository │  ← Интерфейс
└──────┬───────────┘
       │
       ↓
┌──────────────────────┐
│ SqliteMemoryRepo     │  или  JsonMemoryRepo
│  └─ БД/JSON файл     │
└──────────────────────┘
```

---

**Главное правило:** 
- **Загрузка из БД** → только при старте приложения
- **Сохранение в БД** → после каждого сообщения (user + assistant)
- **Чтение истории** → из кэша (память приложения), не из БД!

