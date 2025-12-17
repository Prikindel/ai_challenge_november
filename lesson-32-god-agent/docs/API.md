# API Reference

Полная документация API для God Agent.

## Base URL

```
http://localhost:8080/api
```

## Authentication

Все endpoints требуют аутентификации через Bearer token (если включена):

```bash
curl -H "Authorization: Bearer YOUR_TOKEN" \
     http://localhost:8080/api/chat/message
```

## Endpoints

### Chat API

#### POST `/api/chat/message`

Отправка текстового сообщения агенту.

**Request Body:**
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

Отправка голосового сообщения.

**Request:**
- Content-Type: `multipart/form-data`
- Field: `audio` (audio file)

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

Получение истории диалога.

**Query Parameters:**
- `sessionId` (required)

**Response:**
```json
{
  "sessionId": "session-123",
  "messages": [
    {
      "role": "user",
      "content": "Hello",
      "timestamp": 1234567890
    }
  ]
}
```

### Knowledge Base API

#### POST `/api/knowledge-base/index`

Индексация базы знаний.

**Response:**
```json
{
  "status": "success",
  "documentsIndexed": 42
}
```

#### GET `/api/knowledge-base/search`

Поиск в базе знаний.

**Query Parameters:**
- `query` (required)
- `category` (optional)
- `limit` (optional, default: 5)

**Response:**
```json
{
  "query": "проект",
  "results": [
    {
      "document": "projects/project-x/README.md",
      "chunk": "...",
      "score": 0.95
    }
  ]
}
```

### MCP Servers API

#### GET `/api/mcp/servers`

Список MCP серверов.

**Response:**
```json
{
  "servers": [
    {
      "name": "git",
      "enabled": true,
      "toolsCount": 3
    }
  ]
}
```

#### POST `/api/mcp/servers/{name}/toggle`

Включить/выключить MCP сервер.

**Request:**
```json
{
  "enabled": true
}
```

## Error Responses

```json
{
  "error": "Error message",
  "code": "ERROR_CODE"
}
```

**HTTP Status Codes:**
- `200` - Success
- `400` - Bad Request
- `404` - Not Found
- `500` - Internal Server Error

