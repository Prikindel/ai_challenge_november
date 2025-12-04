# Схема базы данных

## Обзор

Проект использует SQLite базу данных для хранения:
- Документов и их чанков (база знаний)
- Сессий и сообщений чата

База данных находится в файле `data/knowledge_base.db`.

## Таблицы

### documents

Хранит информацию о проиндексированных документах.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | TEXT | Уникальный идентификатор документа (PRIMARY KEY) |
| `file_path` | TEXT | Путь к файлу документа |
| `title` | TEXT | Заголовок документа (может быть NULL) |
| `content` | TEXT | Полное содержимое документа |
| `indexed_at` | INTEGER | Временная метка индексации (Unix timestamp) |
| `chunk_count` | INTEGER | Количество чанков документа |

**Пример:**
```sql
CREATE TABLE documents (
    id TEXT PRIMARY KEY,
    file_path TEXT NOT NULL,
    title TEXT,
    content TEXT NOT NULL,
    indexed_at INTEGER NOT NULL,
    chunk_count INTEGER NOT NULL
);
```

### document_chunks

Хранит чанки документов с их эмбеддингами для векторного поиска.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | TEXT | Уникальный идентификатор чанка (PRIMARY KEY) |
| `document_id` | TEXT | ID документа (FOREIGN KEY → documents.id) |
| `chunk_index` | INTEGER | Индекс чанка в документе (0-based) |
| `content` | TEXT | Текст чанка |
| `start_index` | INTEGER | Начальная позиция в исходном документе |
| `end_index` | INTEGER | Конечная позиция в исходном документе |
| `token_count` | INTEGER | Количество токенов в чанке |
| `embedding` | TEXT | JSON-массив эмбеддинга (вектор) |
| `created_at` | INTEGER | Временная метка создания (Unix timestamp) |

**Индексы:**
- `idx_chunks_document` на `document_id` - для быстрого поиска чанков документа
- `idx_chunks_created` на `created_at` - для сортировки по дате

**Пример:**
```sql
CREATE TABLE document_chunks (
    id TEXT PRIMARY KEY,
    document_id TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    start_index INTEGER NOT NULL,
    end_index INTEGER NOT NULL,
    token_count INTEGER NOT NULL,
    embedding TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (document_id) REFERENCES documents(id)
);
```

### chat_sessions

Хранит сессии чата.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | TEXT | Уникальный идентификатор сессии (PRIMARY KEY, UUID) |
| `title` | TEXT | Заголовок сессии (может быть NULL) |
| `created_at` | INTEGER | Временная метка создания (Unix timestamp) |
| `updated_at` | INTEGER | Временная метка последнего обновления (Unix timestamp) |

**Индексы:**
- `idx_sessions_updated` на `updated_at` - для сортировки по дате обновления

**Пример:**
```sql
CREATE TABLE chat_sessions (
    id TEXT PRIMARY KEY,
    title TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

### chat_messages

Хранит сообщения чата с цитатами.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | TEXT | Уникальный идентификатор сообщения (PRIMARY KEY, UUID) |
| `session_id` | TEXT | ID сессии (FOREIGN KEY → chat_sessions.id) |
| `role` | TEXT | Роль отправителя: "USER" или "ASSISTANT" |
| `content` | TEXT | Текст сообщения |
| `citations` | TEXT | JSON-массив цитат (может быть NULL) |
| `created_at` | INTEGER | Временная метка создания (Unix timestamp) |

**Индексы:**
- `idx_messages_session` на `session_id` - для быстрого поиска сообщений сессии
- `idx_messages_created` на `created_at` - для сортировки по дате

**Пример:**
```sql
CREATE TABLE chat_messages (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    citations TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
);
```

## Формат данных

### Эмбеддинги

Эмбеддинги хранятся в таблице `document_chunks` в поле `embedding` как JSON-массив чисел:

```json
[0.123, -0.456, 0.789, ...]
```

### Цитаты

Цитаты хранятся в таблице `chat_messages` в поле `citations` как JSON-массив объектов:

```json
[
  {
    "text": "[Источник: Название](путь/к/документу.md)",
    "documentPath": "путь/к/документу.md",
    "documentTitle": "Название",
    "chunkId": "chunk-123"
  }
]
```

## Связи

```
documents (1) ──< (N) document_chunks
chat_sessions (1) ──< (N) chat_messages
```

- Один документ может иметь множество чанков
- Одна сессия может иметь множество сообщений
- При удалении сессии все связанные сообщения удаляются автоматически (CASCADE)

## Метаданные документов

Для фильтрации документов в RAG используются метаданные, которые хранятся в пути файла:
- `documents/` - обычные документы для индексации
- `project/docs/` - документация проекта (для команды `/help`)
- `project/README.md` - корневой README проекта

Тип документа определяется по пути и используется для фильтрации в RAG-поиске.

