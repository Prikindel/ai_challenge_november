# Chat Agent Server

Kotlin + Ktor сервер для чат-агента с интеграцией OpenAI API.

## Требования

- Java 17 или выше
- Gradle (будет загружен автоматически через wrapper)

## Настройка

### 1. API ключ

Создайте `.env` файл в корне урока (`lesson-01-simple-chat-agent/.env`):
```bash
OPENAI_API_KEY=your-api-key-here
```

**Важно:** Файл `.env` автоматически игнорируется git и не попадет в репозиторий.

### 2. Конфигурация AI (опционально)

Скопируйте пример конфигурации:
```bash
cp ../config/ai.yaml.example ../config/ai.yaml
```

Отредактируйте `config/ai.yaml`:
```yaml
ai:
  apiUrl: "https://api.openai.com/v1/chat/completions"
  model: "gpt-3.5-turbo"
  temperature: 0.7
  maxTokens: 500
  requestTimeout: 60
  systemPrompt: |
    Ты полезный AI-ассистент. Отвечай кратко и по делу.
```

**Приоритет настроек:**
1. Переменные окружения (`.env` файл) - наивысший приоритет
2. `config/ai.yaml` - настройки AI
3. Значения по умолчанию

## Запуск

```bash
cd server
./gradlew run
```

Сервер запустится на `http://localhost:8080` и автоматически отдаст веб-интерфейс по этому адресу.

## API Endpoints

### POST /chat

Отправка сообщения агенту.

**Request:**
```json
{
  "message": "Привет, как дела?"
}
```

**Response:**
```json
{
  "response": "Привет! У меня все отлично, спасибо за вопрос."
}
```

### GET /health

Проверка состояния сервера.

**Response:**
```json
{
  "status": "ok"
}
```

## Архитектура

Сервер построен согласно принципам **Clean Architecture** и **SOLID**.

### Структура проекта

```
com.prike/
├── domain/              # Бизнес-логика (не зависит от внешних библиотек)
│   ├── entity/         # ChatMessage - сущность сообщения
│   ├── repository/     # AIRepository - интерфейс для работы с AI
│   ├── usecase/        # ChatUseCase - обработка сообщений
│   └── exception/      # Исключения (ValidationException, AIServiceException)
│
├── data/                # Работа с внешними API
│   ├── client/         # OpenAIClient - клиент для OpenAI (настройка)
│   ├── repository/     # AIRepositoryImpl - использует клиент
│   └── dto/            # Форматы данных для OpenAI API
│
├── presentation/        # HTTP API слой
│   ├── controller/     # ServerController - обработка HTTP запросов /chat
│   │                 # ClientController - отдача статических файлов
│   └── dto/            # Форматы данных для HTTP запросов/ответов
│
├── di/                  # Dependency Injection
│   └── AppModule       # Создание зависимостей
│
├── config/             # Конфигурация
│   └── AIConfig        # Data class для настроек AI
│
├── Config.kt            # Загрузка конфигурации (.env + YAML)
└── Main.kt              # Точка входа, настройка Ktor
```

### Основные компоненты


1. **Domain** - чистая бизнес-логика, не зависит от внешних библиотек
    - `ChatUseCase` - валидация и обработка сообщений
    - `AIRepository` (интерфейс) - контракт для получения ответов от AI

2. **Data** - реализация работы с внешними API
    - `OpenAIClient` - HTTP клиент для OpenAI/OpenRouter API
    - `AIRepositoryImpl` - реализация репозитория, использует клиент

3. **Presentation** - HTTP слой
    - `ServerController` - обработка запросов `/chat`
    - `ClientController` - отдача статических файлов клиента

4. **DI** - связывание всех слоев
    - `AppModule` - создает зависимости и передает их в контроллеры

**Поток данных:** `HTTP запрос` → `ServerController` → `ChatUseCase` → `AIRepository` → `AIRepositoryImpl` → `OpenAIClient` → `AI API`

## Конфигурация

### Переменные окружения (.env)

- `OPENAI_API_KEY` - API ключ (обязательно)
- `SERVER_HOST` - хост сервера (по умолчанию: `0.0.0.0`)
- `SERVER_PORT` - порт сервера (по умолчанию: `8080`)

### YAML конфигурация (config/ai.yaml)

- `apiUrl` - URL API провайдера (по умолчанию: OpenAI)
- `model` - модель AI (по умолчанию: `gpt-3.5-turbo`)
- `temperature` - температура генерации (по умолчанию: `0.7`)
- `maxTokens` - максимальное количество токенов (по умолчанию: `500`)
- `requestTimeout` - таймаут запроса в секундах (по умолчанию: `60`)
- `systemPrompt` - системный промпт для задания контекста (опционально)

