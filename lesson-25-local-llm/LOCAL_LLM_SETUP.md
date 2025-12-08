# Инструкции по установке локальной LLM

## Выбранный провайдер

**Ollama** — выбранный провайдер для локальной LLM.

## Процесс установки

### Шаг 1: Установка Ollama

**macOS:**
```bash
brew install ollama
```

**Linux:**
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

**Windows:**
Скачать установщик с https://ollama.com/download

### Шаг 2: Запуск Ollama

```bash
ollama serve
```

Ollama запускается автоматически как сервис и слушает на порту `11434`.

### Шаг 3: Установка модели

**Важно:** В этом уроке мы используем Ollama для запуска **LLM моделей для генерации текста** (llama3.2, mistral и т.д.), которые заменяют внешний API (OpenRouter), а не для эмбеддингов.

**Установка LLM модели для генерации текста:**
```bash
ollama pull llama3.2
```

**Популярные LLM модели для генерации текста:**
- `llama3.2` — LLaMA 3.2 (3B параметров, быстрая) ✅ установлена
- `mistral` — Mistral 7B
- `phi3` — Phi-3 (3.8B, быстрая)
- `qwen2.5` — Qwen 2.5 (7B)
- `gemma2` — Gemma 2 (2B, очень быстрая)

**Примечание:** Модель `nomic-embed-text` используется для эмбеддингов в RAG (предыдущие уроки), а не для генерации текста.

## Проверка работы

### Через CLI

```bash
ollama run llama3.2 "Привет! Как дела?"
```

**Результат:**
```
Здорово! Как я могу помочь вам?
```

### Через API

**Проверка генерации текста:**
```bash
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2",
  "prompt": "Привет!",
  "stream": false
}'
```

**Результат:**
```json
{
  "model": "llama3.2",
  "created_at": "2025-12-08T20:01:54.41739Z",
  "response": "Здорово! Как я могу помочь вам?",
  "done": true,
  "done_reason": "stop",
  "context": [...],
  "total_duration": 3726197583,
  "load_duration": 3173556541,
  "prompt_eval_count": 29,
  "prompt_eval_duration": 252932000,
  "eval_count": 13,
  "eval_duration": 197623167
}
```

**Проверка списка моделей:**
```bash
curl http://localhost:11434/api/tags
```

**Проверка статуса:**
```bash
curl http://localhost:11434/api/version
```

## Конфигурация

**URL API:** `http://localhost:11434`

**Порт:** `11434`

**Модель:** `llama3.2`

**API Path:** `/api/generate` (для генерации текста)

**Важно:** 
- `/api/generate` — используется для генерации текста (LLM модели)
- `/api/embeddings` — используется для эмбеддингов (nomic-embed-text, используется в RAG)

## Примеры запросов

### Пример 1: Простой вопрос

```bash
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2",
  "prompt": "Привет!",
  "stream": false
}'
```

**Ответ:**
```json
{
  "response": "Здорово! Как я могу помочь вам?",
  "done": true,
  "model": "llama3.2"
}
```

### Пример 2: Вопрос с контекстом

```bash
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2",
  "prompt": "Что такое RAG?",
  "stream": false
}'
```

### Пример 3: Stream режим (для длинных ответов)

```bash
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2",
  "prompt": "Расскажи про MCP серверы",
  "stream": true
}'
```

## Структура API запроса

**Endpoint:** `POST http://localhost:11434/api/generate`

**Request:**
```json
{
  "model": "llama3.2",
  "prompt": "Текст промпта",
  "stream": false,
  "options": {
    "temperature": 0.7,
    "top_p": 0.9,
    "top_k": 40
  }
}
```

**Response:**
```json
{
  "model": "llama3.2",
  "created_at": "2025-12-08T20:01:54.41739Z",
  "response": "Ответ модели",
  "done": true,
  "done_reason": "stop",
  "context": [...],
  "total_duration": 3726197583,
  "load_duration": 3173556541,
  "prompt_eval_count": 29,
  "prompt_eval_duration": 252932000,
  "eval_count": 13,
  "eval_duration": 197623167
}
```

## Проблемы и решения

### Ollama не запускается

**Решение:**
1. Проверьте, что порт 11434 не занят: `lsof -i :11434`
2. Перезапустите Ollama: `ollama serve`
3. Проверьте логи: `ollama logs`

### Модель не найдена

**Решение:**
```bash
# Проверьте список установленных моделей
ollama list

# Если модели нет, установите её
ollama pull llama3.2
```

### Медленные ответы

**Решение:**
1. Используйте меньшую модель (gemma2, phi3)
2. Используйте GPU вместо CPU (если доступно)
3. Уменьшите параметры генерации (temperature, max_tokens)

### Недостаточно памяти

**Решение:**
1. Используйте квантованную модель (Q4_0)
2. Закройте другие приложения
3. Используйте меньшую модель

## Примечания

1. **Разница между LLM и эмбеддингами:**
   - **LLM модели** (llama3.2, mistral) — для генерации текста, используются в этом уроке
   - **Эмбеддинги** (nomic-embed-text) — для векторизации текста, используются в RAG (предыдущие уроки)

2. **Ollama используется в двух ролях:**
   - Для эмбеддингов (RAG) — `nomic-embed-text` через `/api/embeddings`
   - Для генерации текста (LLM) — `llama3.2` через `/api/generate` ← **этот урок**

3. **Проверка работы:**
   - ✅ Ollama установлен и запущен
   - ✅ Модель `llama3.2` установлена
   - ✅ Проверка через CLI работает
   - ✅ Проверка через API работает

4. **Следующие шаги:**
   - Создать клиент для локальной LLM (LocalLLMClient)
   - Интегрировать в LLMService
   - Протестировать в чате
