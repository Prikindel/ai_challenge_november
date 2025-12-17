# 🔥 День 32. God Agent

Финальный персональный AI-помощник, объединяющий все наработки: RAG для базы знаний, модульные MCP серверы (как плагины), голосовой ввод, персонализацию и аналитику данных.

## 📑 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Usage](#-usage)
- [API Reference](#-api-reference)
- [Troubleshooting](#-troubleshooting)
- [Related Lessons](#-related-lessons)

## ✨ Features

- 🔍 **Semantic Search** - RAG-powered knowledge base search with citations
- 🔌 **Modular Plugins** - Extensible MCP server architecture (Git, Telegram, Analytics, File System)
- 🎤 **Voice Input** - Speech recognition via Vosk (offline, local)
- 🎯 **Personalization** - User profile-based responses and context awareness
- 📊 **Data Analytics** - Multi-source data analysis (CSV, JSON, databases)
- 🔐 **Privacy-First** - Fully local operation, no cloud dependencies
- 📝 **Knowledge Base** - Personal knowledge management (like Obsidian)
- 🔄 **Auto-indexing** - Automatic document indexing and updates
- 💬 **Chat History** - Persistent conversation history with sessions
- 🛠️ **Extensible** - Easy to add custom MCP servers and tools

## 🎥 Демонстрация

> 📹 Видео появится после записи

**Сценарий использования:**
1. Открытие единого интерфейса God Agent
2. Запрос на естественном языке (текст или голос)
3. Автоматический роутинг: RAG поиск, MCP инструменты, аналитика
4. Персонализированный ответ с учетом профиля пользователя
5. Управление MCP серверами и базой знаний через UI

## 🎯 Цель урока

Создать **персонального AI-помощника**, который:
- **Объединяет все наработки** из предыдущих уроков
- **Работает как персональная база знаний** (как Obsidian)
- **Поддерживает модульные MCP серверы** (как плагины)
- **Понимает контекст пользователя** и его данные
- **Анализирует данные** из разных источников
- **Персонализирует ответы** под пользователя

## Что делаем

- Берем базовый урок с RAG, MCP и персонализацией (lesson-30)
- Создаём систему конфигурации для модульных MCP серверов
- Реализуем динамический MCP Router
- Расширяем базу знаний для личных документов
- Интегрируем голосовой ввод (Vosk)
- Создаём Analytics MCP сервер
- Объединяем всё в единый God Agent Service
- Создаём UI для управления MCP серверами
- Добавляем примеры контента и документацию

## Пайплайн

1. **Выбор базы** — копируем lesson-30-personalization
2. **Конфигурация MCP** — система конфигурации для плагинов
3. **MCP Router** — динамический роутер для MCP серверов
4. **Расширенная RAG** — база знаний с категориями
5. **Голосовой ввод** — интеграция Vosk из урока 31
6. **Analytics MCP** — сервер для анализа данных
7. **God Agent Service** — главный сервис, объединяющий всё
8. **UI управления** — интерфейс для настройки
9. **Документация** — примеры и инструкции

## Архитектура

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

## 🚀 Installation

### Prerequisites

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Java | 17+ | 17+ |
| Gradle | 8.0+ | 8.0+ |
| RAM | 2GB | 8GB+ |
| Storage | 500MB | 2GB+ |
| CPU | 2 cores | 4+ cores |

**Additional Requirements:**
- ffmpeg 4.0+ (for voice input)
- Local LLM server (Ollama recommended) on VPS
- Vosk model (~40MB for small Russian model)

### Step 1: Clone or Copy Project

```bash
# Option 1: Copy from lesson-30
cp -r lesson-30-personalization lesson-32-god-agent
cd lesson-32-god-agent

# Option 2: Clone repository (if available)
# git clone https://github.com/yourusername/god-agent.git
# cd god-agent
```

### Step 2: Install Dependencies

```bash
cd server
./gradlew build
```

### Step 3: Download Vosk Model

```bash
# Create models directory
mkdir -p models
cd models

# Download Russian model (small, ~40MB)
wget https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip

# Extract
unzip vosk-model-small-ru-0.22.zip

# Verify installation
ls -la vosk-model-small-ru-0.22/
# Should see: am/, graph/, ivector/, conf/ directories
```

**Alternative:** Download via browser from [Vosk Models](https://alphacephei.com/vosk/models)

### Step 4: Install ffmpeg

**macOS:**
```bash
brew install ffmpeg
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install ffmpeg
```

**Windows:**
```bash
choco install ffmpeg
# Or download from https://ffmpeg.org/download.html
```

**Verify:**
```bash
ffmpeg -version
```

### Step 5: Setup Environment

Create `.env` file in project root:

```bash
# Copy example (if exists)
cp .env.example .env

# Or create new
cat > .env << EOF
# LLM Configuration
LLM_PROVIDER=ollama
LLM_BASE_URL=https://your-vps.com
LLM_MODEL=llama3.2
LLM_API_KEY=your_key_here

# Vosk Model
VOSK_MODEL_PATH=models/vosk-model-small-ru-0.22

# Knowledge Base
KB_AUTO_INDEX=true
KB_WATCH_CHANGES=true
KB_BASE_PATH=knowledge-base

# MCP Servers
MCP_CONFIG_PATH=config/mcp-servers.yaml

# Telegram (optional)
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
EOF
```

## ⚙️ Configuration

### Main Configuration (`config/server.yaml`)

```yaml
god_agent:
  enabled: true
  
  # Server settings
  server:
    host: "0.0.0.0"
    port: 8080
  
  # MCP Servers configuration
  mcp_servers:
    enabled: true
    config_path: "config/mcp-servers.yaml"
  
  # Knowledge Base settings
  knowledge_base:
    enabled: true
    base_path: "knowledge-base"
    auto_index: true
    watch_changes: true
    chunk_size: 500
    chunk_overlap: 50
  
  # Personalization
  personalization:
    enabled: true
    profile_path: "config/user-profile.yaml"
    learning_enabled: true
  
  # Voice input
  voice:
    enabled: true
    vosk_model_path: "models/vosk-model-small-ru-0.22"
    sample_rate: 16000
    audio_format: "wav"
  
  # Local LLM
  local_llm:
    enabled: true
    provider: "ollama"  # ollama, openrouter
    base_url: "https://your-vps.com"
    model: "llama3.2"
    temperature: 0.7
    max_tokens: 2048
    timeout_seconds: 60
  
  # Logging
  logging:
    level: "INFO"  # DEBUG, INFO, WARN, ERROR
    file: "logs/god-agent.log"
```

### MCP Servers Configuration (`config/mcp-servers.yaml`)

```yaml
mcp_servers:
  enabled: true
  
  # Git MCP - Work with git repositories
  git:
    enabled: true
    name: "Git MCP"
    description: "Work with git repositories and files"
    repositories:
      - path: "${HOME}/projects/my-project"
        name: "My Project"
      - path: "${HOME}/projects/other-project"
        name: "Other Project"
  
  # Telegram MCP - Notifications and messages
  telegram:
    enabled: false  # Set to true if you want Telegram integration
    name: "Telegram MCP"
    description: "Send notifications and messages via Telegram"
    bot_token: "${TELEGRAM_BOT_TOKEN}"
    chat_id: "${TELEGRAM_CHAT_ID}"
  
  # Analytics MCP - Data analysis
  analytics:
    enabled: true
    name: "Analytics MCP"
    description: "Analyze data from CSV, JSON, databases"
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
  
  # File System MCP - File operations
  filesystem:
    enabled: true
    name: "File System MCP"
    description: "Search and read files"
    allowed_paths:
      - "${HOME}/Documents"
      - "${HOME}/projects"
      - "knowledge-base"
  
  # Calendar MCP - Events and reminders (optional)
  calendar:
    enabled: false
    name: "Calendar MCP"
    description: "Manage events and reminders"
    storage_path: "data/calendar/events.json"
```

### User Profile (`config/user-profile.yaml`)

```yaml
user_profile:
  id: "default"
  name: "Your Name"
  
  preferences:
    language: "ru"  # ru, en
    response_format: "detailed"  # brief, detailed, structured
    timezone: "Europe/Moscow"
  
  work_style:
    preferred_working_hours: "09:00-18:00"
    focus_areas:
      - "backend"
      - "ai"
    tools:
      - "kotlin"
      - "ktor"
    projects:
      - "god-agent"
  
  communication_style:
    tone: "professional"  # casual, professional, friendly
    verbosity: "medium"  # low, medium, high
  
  context:
    current_projects:
      - "God Agent development"
    interests:
      - "AI agents"
      - "Knowledge management"
```

### Step 6: Start Server

```bash
cd server
./gradlew run
```

**Verify installation:**
```bash
# Check server is running
curl http://localhost:8080/health

# Or open in browser
open http://localhost:8080
```

**Expected output:**
```
Server started at http://0.0.0.0:8080
Knowledge base indexed: 42 documents
MCP servers loaded: 4
Voice recognition ready
```

```bash
cd server
./gradlew build
./gradlew run
```

Откройте: `http://localhost:8080`

## Использование

### Основной чат

1. Откройте главную страницу
2. Введите запрос или используйте голосовой ввод
3. Получите персонализированный ответ

### Примеры запросов

- **"Найди информацию о проекте X"**
  - RAG ищет в базе знаний
  - Git MCP читает файлы проекта
  - Возвращает ответ с источниками

- **"Проанализируй метрики за последний месяц"**
  - Analytics MCP анализирует данные
  - LLM генерирует отчет
  - Визуализация результатов

- **"Напомни мне о встрече завтра"**
  - Calendar MCP создает напоминание
  - Telegram MCP отправляет сообщение

- **"Что я писал про архитектуру?"**
  - RAG ищет в личных заметках
  - Возвращает релевантные фрагменты

### Управление MCP серверами

1. Откройте страницу настроек (`/settings`)
2. Включите/выключите MCP серверы
3. Настройте параметры каждого сервера
4. Добавьте свои MCP серверы

### Управление базой знаний

1. Добавьте документы в `knowledge-base/`
2. Организуйте по категориям:
   - `projects/` — документация проектов
   - `learning/` — заметки и обучение
   - `personal/` — личные заметки
   - `references/` — справочные материалы
3. Переиндексируйте через UI или API

## Структура базы знаний

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

## MCP Серверы (Плагины)

### Встроенные серверы

- **Git MCP** — работа с git репозиториями и файлами
- **Telegram MCP** — напоминания и отправка сообщений
- **Analytics MCP** — анализ данных из CSV, JSON, БД
- **File System MCP** — поиск и чтение файлов
- **Calendar MCP** — управление событиями (опционально)

### Создание своего MCP сервера

См. документацию: `docs/MCP_SERVERS.md`

## 📚 API Reference

Base URL: `http://localhost:8080/api`

### Chat API

#### POST `/api/chat/message`

Send text message to agent.

**Request:**
```json
{
  "message": "Найди информацию о проекте X",
  "sessionId": "session-123",
  "userId": "user-456"
}
```

**Response:**
```json
{
  "message": "Вот информация о проекте X...",
  "sources": [
    {
      "document": "projects/project-x/README.md",
      "chunk": "...",
      "score": 0.95
    }
  ],
  "toolsUsed": ["rag_search", "git_read_file"],
  "sessionId": "session-123"
}
```

#### POST `/api/chat/voice`

Send voice message (audio file).

**Request:**
- Content-Type: `multipart/form-data`
- Field: `audio` (audio file, webm/wav format)

**Response:**
```json
{
  "recognizedText": "Найди информацию о проекте",
  "response": {
    "message": "...",
    "sources": []
  }
}
```

#### GET `/api/chat/history`

Get chat history for session.

**Query Parameters:**
- `sessionId` (required) - Session ID

**Response:**
```json
{
  "sessionId": "session-123",
  "messages": [
    {
      "role": "user",
      "content": "Hello",
      "timestamp": 1234567890
    },
    {
      "role": "assistant",
      "content": "Hi! How can I help?",
      "timestamp": 1234567891
    }
  ]
}
```

### Knowledge Base API

#### POST `/api/knowledge-base/index`

Index all documents in knowledge base.

**Response:**
```json
{
  "status": "success",
  "documentsIndexed": 42,
  "categories": ["projects", "learning", "personal", "references"]
}
```

#### GET `/api/knowledge-base/search`

Search in knowledge base.

**Query Parameters:**
- `query` (required) - Search query
- `category` (optional) - Filter by category
- `limit` (optional) - Max results (default: 5)

**Response:**
```json
{
  "query": "проект",
  "results": [
    {
      "document": "projects/project-x/README.md",
      "chunk": "...",
      "score": 0.95,
      "category": "projects"
    }
  ],
  "total": 1
}
```

#### GET `/api/knowledge-base/categories`

Get list of categories.

**Response:**
```json
{
  "categories": ["projects", "learning", "personal", "references"]
}
```

### MCP Servers API

#### GET `/api/mcp/servers`

Get list of all MCP servers and their status.

**Response:**
```json
{
  "servers": [
    {
      "name": "git",
      "enabled": true,
      "description": "Work with git repositories",
      "toolsCount": 3
    }
  ]
}
```

#### POST `/api/mcp/servers/{name}/toggle`

Enable or disable MCP server.

**Request:**
```json
{
  "enabled": true
}
```

#### GET `/api/mcp/tools`

Get list of all available tools from all MCP servers.

**Response:**
```json
{
  "tools": [
    {
      "server": "git",
      "name": "read_file",
      "description": "Read file from repository"
    }
  ]
}
```

### User Profile API

#### GET `/api/profile`

Get user profile.

**Response:**
```json
{
  "id": "default",
  "name": "Your Name",
  "preferences": {
    "language": "ru",
    "responseFormat": "detailed"
  }
}
```

#### PUT `/api/profile`

Update user profile.

**Request:**
```json
{
  "name": "New Name",
  "preferences": {
    "language": "en"
  }
}
```

### Error Responses

All endpoints may return errors:

```json
{
  "error": "Error message",
  "code": "ERROR_CODE",
  "details": {}
}
```

**HTTP Status Codes:**
- `200` - Success
- `400` - Bad Request
- `401` - Unauthorized
- `404` - Not Found
- `500` - Internal Server Error

See [Full API Documentation](docs/API.md) for details.

## Файлы урока

- `PROMPT.md` — промпт для реализации (по коммитам)
- `README.md` — этот файл
- `CHAT_PROMPT.txt` — краткий промпт для агента
- `docs/MCP_SERVERS.md` — как создавать MCP серверы
- `docs/KNOWLEDGE_BASE.md` — организация базы знаний
- `docs/ANALYTICS.md` — использование аналитики
- `docs/PERSONALIZATION.md` — настройка персонализации

## 💡 Советы

1. **Организуйте базу знаний** — используйте категории и структуру папок
2. **Настройте MCP серверы** — включите только нужные
3. **Обновляйте профиль** — персонализация улучшается с профилем
4. **Регулярно индексируйте** — новые документы нужно индексировать
5. **Используйте голосовой ввод** — удобно для быстрых запросов

## 🐛 Troubleshooting

### Common Issues

#### MCP Server Connection Failed

**Problem:** MCP server not responding or connection timeout

**Solutions:**
1. **Check server status:**
   ```bash
   curl http://localhost:8001/health
   # Replace 8001 with your MCP server port
   ```

2. **Verify configuration:**
   ```bash
   # Check config file syntax
   cat config/mcp-servers.yaml | grep -A 5 "git:"
   ```

3. **Check server is enabled:**
   ```yaml
   # config/mcp-servers.yaml
   git:
     enabled: true  # Must be true
   ```

4. **Check logs:**
   ```bash
   tail -f logs/god-agent.log | grep -i "mcp"
   ```

5. **Restart server:**
   ```bash
   # Stop server (Ctrl+C)
   # Start again
   ./gradlew run
   ```

#### Knowledge Base Not Indexing

**Problem:** Documents not appearing in search results

**Solutions:**
1. **Check file format:**
   - Supported: `.md`, `.txt`, `.markdown`
   - Not supported: `.docx`, `.pdf` (without conversion)

2. **Verify file location:**
   ```bash
   ls -la knowledge-base/projects/
   # Files should be in correct category directories
   ```

3. **Check file permissions:**
   ```bash
   ls -l knowledge-base/projects/my-project/README.md
   # Should be readable
   ```

4. **Manual indexing:**
   ```bash
   # Via API
   curl -X POST http://localhost:8080/api/knowledge-base/index
   
   # Or via UI: Settings → Knowledge Base → Reindex
   ```

5. **Check configuration:**
   ```yaml
   knowledge_base:
     auto_index: true  # Should be true
     base_path: "knowledge-base"  # Correct path
   ```

6. **Check logs for errors:**
   ```bash
   tail -f logs/god-agent.log | grep -i "index"
   ```

#### Voice Recognition Not Working

**Problem:** Vosk not recognizing speech or microphone not accessible

**Solutions:**
1. **Verify Vosk model:**
   ```bash
   ls -la models/vosk-model-small-ru-0.22/
   # Should see: am/, graph/, ivector/, conf/ directories
   ```

2. **Check model path in config:**
   ```yaml
   voice:
     vosk_model_path: "models/vosk-model-small-ru-0.22"  # Correct path
   ```

3. **Test ffmpeg:**
   ```bash
   ffmpeg -version
   # Should show version 4.0+
   ```

4. **Check browser permissions:**
   - Chrome: Settings → Privacy → Microphone → Allow
   - Firefox: Preferences → Privacy → Permissions → Microphone
   - Safari: Preferences → Websites → Microphone

5. **Test audio format:**
   - Vosk requires: 16kHz, mono, 16-bit PCM WAV
   - Check conversion is working:
     ```bash
     ffmpeg -i input.webm -ar 16000 -ac 1 -f s16le output.wav
     ```

6. **Check server logs:**
   ```bash
   tail -f logs/god-agent.log | grep -i "voice\|vosk"
   ```

#### LLM Connection Failed

**Problem:** Cannot connect to local LLM on VPS

**Solutions:**
1. **Test VPS connection:**
   ```bash
   curl https://your-vps.com/health
   # Or
   curl https://your-vps.com/api/tags
   ```

2. **Check configuration:**
   ```yaml
   local_llm:
     base_url: "https://your-vps.com"  # Correct URL
     model: "llama3.2"  # Model exists on VPS
   ```

3. **Check authentication:**
   ```bash
   # If using API key
   curl -H "Authorization: Bearer YOUR_KEY" \
        https://your-vps.com/api/generate
   ```

4. **Check network:**
   ```bash
   ping your-vps.com
   # Should respond
   ```

5. **Check timeout:**
   ```yaml
   local_llm:
     timeout_seconds: 60  # Increase if slow connection
   ```

#### Slow Response Times

**Problem:** Agent responds slowly

**Solutions:**
1. **Check LLM response time:**
   ```bash
   time curl -X POST https://your-vps.com/api/generate \
        -d '{"model":"llama3.2","prompt":"test"}'
   ```

2. **Reduce context size:**
   ```yaml
   knowledge_base:
     chunk_size: 300  # Reduce from 500
   ```

3. **Limit search results:**
   ```yaml
   rag:
     max_results: 3  # Reduce from 5
   ```

4. **Disable unused MCP servers:**
   ```yaml
   telegram:
     enabled: false  # Disable if not using
   ```

### Debug Mode

Enable detailed logging:

```yaml
# config/server.yaml
logging:
  level: "DEBUG"  # Change from INFO
  file: "logs/god-agent.log"
```

Then check logs:
```bash
tail -f logs/god-agent.log
```

### Getting Help

- 📖 Check [Documentation](docs/)
- 🔍 Search [Issues](https://github.com/yourusername/god-agent/issues)
- 💬 Ask in [Discussions](https://github.com/yourusername/god-agent/discussions)

## 🔗 Связанные уроки

- **Урок 19-20** — RAG и MCP (базовая функциональность)
- **Урок 30** — Персонализация (профиль пользователя)
- **Урок 31** — Голосовой ввод (Vosk)
- **Урок 29** — Аналитика (анализ данных)
- **Урок 27-28** — Локальная LLM (VPS)

## 🎉 Результат

Готовый продукт — персональный AI-помощник, который:
- ✅ Объединяет все наработки
- ✅ Работает как персональная база знаний
- ✅ Поддерживает модульные плагины (MCP)
- ✅ Персонализирован под пользователя
- ✅ Анализирует данные из разных источников
- ✅ Поддерживает голосовой ввод

