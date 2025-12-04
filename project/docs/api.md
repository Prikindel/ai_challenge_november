# API Документация

## Обзор

API для ассистента разработчика предоставляет следующие возможности:
- Управление чат-сессиями
- Отправка сообщений с RAG-поиском
- Индексация документов
- Поиск по базе знаний
- Работа с документами

## Базовый URL

```
http://localhost:8080
```

## Чат API

### 1. Создать сессию

**POST** `/api/chat/sessions`

Создаёт новую сессию чата.

**Request:**
```json
{
  "title": "Обсуждение проекта"
}
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Обсуждение проекта",
  "createdAt": 1705320600000,
  "updatedAt": 1705320600000
}
```

### 2. Получить список сессий

**GET** `/api/chat/sessions`

Возвращает список всех сессий чата.

**Response:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Обсуждение проекта",
    "createdAt": 1705320600000,
    "updatedAt": 1705320700000
  }
]
```

### 3. Получить сессию

**GET** `/api/chat/sessions/{sessionId}`

Возвращает информацию о конкретной сессии.

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Обсуждение проекта",
  "createdAt": 1705320600000,
  "updatedAt": 1705320700000
}
```

### 4. Удалить сессию

**DELETE** `/api/chat/sessions/{sessionId}`

Удаляет сессию и все связанные сообщения.

**Response:** `204 No Content`

### 5. Отправить сообщение

**POST** `/api/chat/sessions/{sessionId}/messages`

Отправляет сообщение в сессию. Выполняет RAG-поиск и возвращает ответ с цитатами.

**Request:**
```json
{
  "message": "Как работает индексация документов?",
  "topK": 5,
  "minSimilarity": 0.4,
  "applyFilter": true,
  "strategy": "hybrid",
  "historyStrategy": "sliding"
}
```

**Параметры:**
- `message` (string, required) - текст сообщения
- `topK` (number, optional) - количество чанков для поиска (по умолчанию: 5)
- `minSimilarity` (number, optional) - минимальная схожесть (по умолчанию: 0.4)
- `applyFilter` (boolean, optional) - применять фильтрацию (по умолчанию: true)
- `strategy` (string, optional) - стратегия фильтрации: "hybrid", "threshold" (по умолчанию: "hybrid")
- `historyStrategy` (string, optional) - стратегия истории: "sliding", "token_limit", "none" (по умолчанию: "sliding")

**Response:**
```json
{
  "message": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "role": "ASSISTANT",
    "content": "Индексация документов работает через разбиение на чанки...",
    "citations": [
      {
        "text": "[Источник: Индексация документов](project/docs/schema.md)",
        "documentPath": "project/docs/schema.md",
        "documentTitle": "Схема данных",
        "chunkId": "chunk-123"
      }
    ],
    "createdAt": 1705320610000
  },
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 6. Получить историю сообщений

**GET** `/api/chat/sessions/{sessionId}/messages`

Возвращает все сообщения сессии.

**Response:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "messages": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "sessionId": "550e8400-e29b-41d4-a716-446655440000",
      "role": "USER",
      "content": "Как работает индексация?",
      "citations": [],
      "createdAt": 1705320600000
    },
    {
      "id": "660e8400-e29b-41d4-a716-446655440002",
      "sessionId": "550e8400-e29b-41d4-a716-446655440000",
      "role": "ASSISTANT",
      "content": "Индексация работает через...",
      "citations": [...],
      "createdAt": 1705320610000
    }
  ]
}
```

## Индексация API

### Индексировать документы

**POST** `/api/indexing/index`

Индексирует документы из указанной директории.

**Request:**
```json
{
  "documentsPath": "documents"
}
```

**Response:**
```json
{
  "status": "success",
  "indexedCount": 10,
  "message": "Documents indexed successfully"
}
```

### Получить список документов

**GET** `/api/indexing/documents`

Возвращает список всех проиндексированных документов.

**Response:**
```json
{
  "documents": [
    {
      "id": "doc-123",
      "filePath": "documents/01-intro.md",
      "title": "Введение",
      "indexedAt": 1705320600000
    }
  ]
}
```

## Поиск API

### Поиск по запросу

**POST** `/api/search/query`

Выполняет поиск по базе знаний.

**Request:**
```json
{
  "query": "как работает RAG",
  "topK": 5,
  "minSimilarity": 0.4
}
```

**Response:**
```json
{
  "results": [
    {
      "chunkId": "chunk-123",
      "documentId": "doc-123",
      "documentPath": "documents/01-intro.md",
      "content": "RAG работает через...",
      "similarity": 0.85
    }
  ]
}
```

## RAG API

### RAG-запрос

**POST** `/api/rag/query`

Выполняет RAG-запрос с фильтрацией и реранкингом.

**Request:**
```json
{
  "query": "как работает индексация",
  "topK": 5,
  "minSimilarity": 0.4,
  "applyFilter": true,
  "strategy": "hybrid"
}
```

**Response:**
```json
{
  "query": "как работает индексация",
  "chunks": [
    {
      "chunkId": "chunk-123",
      "documentId": "doc-123",
      "documentPath": "documents/01-intro.md",
      "content": "Индексация работает...",
      "similarity": 0.85,
      "rerankScore": 0.92
    }
  ],
  "answer": "Индексация документов работает через..."
}
```

## Документы API

### Получить документ

**GET** `/api/documents/{documentPath}`

Возвращает содержимое документа.

**Response:**
```json
{
  "path": "documents/01-intro.md",
  "content": "# Введение\n\nТекст документа...",
  "title": "Введение"
}
```

## Ошибки

Все ошибки возвращаются в следующем формате:

```json
{
  "error": "Error message",
  "details": "Additional error details"
}
```

**Коды статусов:**
- `200` - Успешный запрос
- `400` - Неверный запрос
- `404` - Ресурс не найден
- `500` - Внутренняя ошибка сервера

