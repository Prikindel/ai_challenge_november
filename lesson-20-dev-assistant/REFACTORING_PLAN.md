# План рефакторинга: Полная модульная архитектура

## Цель
Полностью отделить RAG функциональность от основного сервера, сделав её независимым MCP сервером с четким разделением ответственности.

## Текущая архитектура (проблемы)

### Основной сервер содержит:
- ✅ LLMService (нужен для чата)
- ✅ ChatService, ChatRepository (основная функциональность)
- ❌ RAGService (должен быть в MCP)
- ❌ KnowledgeBaseSearchService (должен быть в MCP)
- ❌ DocumentIndexer (должен быть в MCP)
- ❌ EmbeddingService (должен быть в MCP)
- ❌ RerankerService (должен быть в MCP)
- ❌ KnowledgeBaseRepository (для RAG - должен быть в MCP)
- ✅ GitMCPService, RagMCPService (MCP клиенты)
- ✅ RequestRouterService (роутинг)

## Новая архитектура

### RAG MCP Server (`rag-mcp-server/`)
**Ответственность:** Вся работа с RAG
- Индексация документов
- Поиск по базе знаний
- Реранкинг результатов
- Управление базой знаний

**Инструменты:**
1. `rag_index_documents` - индексация документов из папки
2. `rag_index_project_docs` - индексация документации проекта
3. `rag_search` - поиск по всем документам
4. `rag_search_project_docs` - поиск только в project docs
5. `rag_query` - полный RAG запрос (поиск + генерация ответа)

**Внутренние компоненты:**
- EmbeddingService
- KnowledgeBaseRepository
- DocumentIndexer
- KnowledgeBaseSearchService
- RerankerService
- VectorNormalizer
- TextChunker
- DocumentLoader

### Основной сервер (`server/`)
**Ответственность:** Обработка чата и координация MCP серверов
- ChatService (обработка сообщений)
- ChatRepository (история чата)
- LLMService (генерация ответов)
- RequestRouterService (роутинг запросов)
- MCP клиенты (GitMCPService, RagMCPService)

**Убрать:**
- Все RAG компоненты
- EmbeddingService
- KnowledgeBaseRepository (для RAG)
- DocumentIndexer
- KnowledgeBaseSearchService
- RAGService
- RerankerService
- ComparisonService
- CitationAnalyzer

## План реализации

### Этап 1: Расширение RAG MCP сервера
1. Перенести все RAG компоненты в rag-mcp-server
2. Добавить инструменты для индексации
3. Добавить инструмент rag_query для полного RAG запроса
4. Настроить работу с базой данных внутри MCP сервера

### Этап 2: Обновление основного сервера
1. Убрать все RAG компоненты из Main.kt
2. Обновить ChatService: использовать только MCP инструменты
3. Обновить IndexingController: использовать MCP инструменты
4. Убрать RAG контроллеры (RAGController, SearchController для RAG)
5. Обновить конфигурацию

### Этап 3: Тестирование и очистка
1. Проверить компиляцию
2. Удалить неиспользуемые файлы
3. Обновить документацию

## Преимущества новой архитектуры

1. **Четкое разделение ответственности**
   - RAG = отдельный сервис
   - Чат = основной сервер

2. **Масштабируемость**
   - Легко добавить новые MCP серверы
   - RAG можно масштабировать независимо

3. **Переиспользование**
   - RAG MCP сервер можно использовать в других проектах
   - Нет дублирования кода

4. **Гибкость**
   - Можно заменить RAG реализацию без изменения основного сервера
   - Можно использовать несколько RAG серверов

