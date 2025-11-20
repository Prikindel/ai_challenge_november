# Импорт примеров сообщений в БД

## Описание

Утилита для импорта примеров сообщений из `telegram_messages_example.txt` в базу данных Telegram.

## Использование

### Через Gradle:

```bash
cd telegram-mcp-server
./gradlew importExamples
```

### Через JAR:

```bash
cd telegram-mcp-server
java -cp "build/libs/telegram-mcp-server-1.0.0.jar:build/libs/*" com.prike.mcpserver.utils.ImportExampleMessagesKt
```

## Что делает утилита:

1. Читает файл `telegram_messages_example.txt` из корня урока
2. Парсит сообщения по формату:
   ```
   Пользователь 1 (Иван):
   Текст сообщения
   ---
   ```
3. Проверяет существующие сообщения в БД (по `message_id`)
4. Добавляет только новые сообщения (не дублирует существующие)
5. Распределяет сообщения по времени (каждые 5 минут, начиная с 24 часов назад)

## Требования:

- Файл `telegram_messages_example.txt` должен находиться в корне `lesson-12-reminder-mcp`
- Конфигурация `telegram-mcp-server.yaml` должна быть настроена
- Переменные окружения должны быть заданы (TELEGRAM_GROUP_ID, TELEGRAM_BOT_TOKEN и т.д.)

## Результат:

Сообщения будут добавлены в таблицу `telegram_messages` в БД `data/summary.db`.

