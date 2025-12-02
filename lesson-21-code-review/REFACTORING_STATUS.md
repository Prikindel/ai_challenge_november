# Статус рефакторинга: Модульная архитектура

## ✅ Выполнено

### 1. RAG MCP Server - полностью готов
- ✅ Добавлены инструменты для индексации (`rag_index_documents`, `rag_index_project_docs`)
- ✅ Перенесена вся логика RAG в `rag-mcp-server`:
  - EmbeddingService
  - KnowledgeBaseRepository
  - DocumentIndexer
  - KnowledgeBaseSearchService
  - RerankerService
  - VectorNormalizer, TextChunker, CosineSimilarityCalculator
- ✅ Создан `InternalRagServiceProvider` для использования компонентов напрямую
- ✅ Конфигурация через переменные окружения
- ✅ **Компиляция успешна! ✅**

## 🔄 В процессе

### 2. Основной сервер - рефакторинг
- ⏳ Убрать RAG компоненты из Main.kt
- ⏳ Обновить ChatService для использования только MCP инструментов
- ⏳ Обновить IndexingController для использования MCP
- ⏳ Убрать RAG контроллеры (RAGController, SearchController, RagMCPController)
- ⏳ Обновить конфигурацию

## 📋 Следующие шаги

1. **Убрать из Main.kt:**
   - EmbeddingService
   - KnowledgeBaseRepository (для RAG)
   - DocumentIndexer
   - KnowledgeBaseSearchService
   - RAGService
   - RerankerService
   - ComparisonService
   - CitationAnalyzer

2. **Обновить ChatService:**
   - Убрать зависимость от RAGService
   - Использовать только RagMCPService для всех RAG операций

3. **Обновить IndexingController:**
   - Использовать RagMCPService.callTool() вместо прямых вызовов

4. **Удалить контроллеры:**
   - RAGController
   - SearchController (для RAG)
   - RagMCPController (HTTP API больше не нужен)

5. **Обновить конфигурацию:**
   - Убрать RAG настройки из server.yaml
   - Оставить только настройки для подключения к RAG MCP серверу
