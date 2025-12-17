# API Endpoints Reference

## Chat API

### Создать сессию
```
POST /api/chat/sessions
Body: { "title": "Новая сессия" }
Response: { "id": "...", "title": "...", "createdAt": ..., "updatedAt": ... }
```

### Получить список сессий
```
GET /api/chat/sessions
Response: [{ "id": "...", "title": "...", ... }]
```

### Отправить сообщение
```
POST /api/chat/sessions/{sessionId}/messages
Body: { "message": "Текст сообщения" }
Response: { "message": { ... }, "sessionId": "..." }
```

### Голосовое сообщение
```
POST /api/chat/sessions/{sessionId}/voice
Body: FormData с файлом audio (webm)
Response: { "recognizedText": "...", "message": { ... }, "sessionId": "..." }
```

### Получить историю
```
GET /api/chat/sessions/{sessionId}/messages
Response: { "sessionId": "...", "messages": [...] }
```

## Knowledge Base API

### Индексировать всё
```
POST /api/knowledge-base/index
Response: { "status": "success", "message": "..." }
```

### Индексировать категорию
```
POST /api/knowledge-base/index/category/{categoryName}
Response: { "status": "success", "message": "..." }
```

### Поиск
```
GET /api/knowledge-base/search?query=...&category=...&limit=5
Response: [{ "content": "...", "source": "...", "similarity": 0.95, ... }]
```

### Статистика
```
GET /api/knowledge-base/statistics
Response: { "totalChunks": 100, "chunksByCategory": { ... } }
```

### Категории
```
GET /api/knowledge-base/categories
Response: ["projects", "learning", "personal", "references"]
```

## MCP Servers API

### Список серверов
```
GET /api/mcp-servers
Response: { "enabled": true, "servers": [...] }
```

### Список инструментов
```
GET /api/mcp-servers/tools
Response: [{ "serverName": "...", "name": "...", "description": "...", ... }]
```

### Подключить все
```
POST /api/mcp-servers/connect
Response: { "status": "success", "message": "..." }
```

### Отключить все
```
POST /api/mcp-servers/disconnect
Response: { "status": "success", "message": "..." }
```

## Health Check

```
GET /api/health
Response: { "status": "ok", "service": "lesson-32-god-agent-server" }
```

