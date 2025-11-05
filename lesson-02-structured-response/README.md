# Lesson 01: Простой чат-агент

## Описание

Простой AI-агент, который отвечает на вопросы пользователя через HTTP API. Сервер отдает веб-интерфейс и обрабатывает запросы к AI API.

## Демонстрация

[Вот тут лежит](https://disk.yandex.ru/i/J9rm8ifCdKIaXA)

## Технологии

- **Backend:** Kotlin + Ktor
- **Frontend:** HTML + JavaScript (Vanilla)
- **AI API:** OpenAI или OpenRouter (настраивается)

## Быстрый старт

1. Установите Java 17+

2. Настройте API ключ в `.env` файле (см. [server/README.md](./server/README.md#настройка))

3. Настройте AI в `config/ai.yaml` (опционально)

4. Запустите сервер:
   ```bash
   cd server
   ./gradlew run
   ```

5. Откройте браузер: `http://localhost:8080`

Подробная документация по настройке и запуску сервера: [server/README.md](./server/README.md)

## Структура проекта

```
lesson-01-simple-chat-agent/
├── config/
│   └── ai.yaml          
├── server/              # Kotlin + Ktor сервер
│
└── client/              # Веб-клиент
```

