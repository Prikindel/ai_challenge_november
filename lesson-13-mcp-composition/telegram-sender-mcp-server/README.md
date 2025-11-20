# Telegram Sender MCP Server

MCP сервер для отправки сообщений пользователям в Telegram.

## Описание

Этот MCP сервер предоставляет инструмент `send_telegram_message` для отправки личных сообщений пользователям через Telegram Bot API.

## Структура

```
telegram-sender-mcp-server/
├── src/main/kotlin/com/prike/mcpserver/
│   ├── Main.kt                    # Точка входа
│   ├── Config.kt                  # Загрузка конфигурации
│   ├── server/
│   │   └── MCPServer.kt           # MCP сервер
│   ├── telegram/
│   │   └── TelegramBotClient.kt   # Клиент для Telegram Bot API
│   └── tools/
│       ├── SendTelegramMessageTool.kt
│       └── handlers/
│           └── SendTelegramMessageHandler.kt
└── src/main/resources/
    └── logback.xml
```

## Инструмент

### send_telegram_message

Отправляет личное сообщение пользователю в Telegram.

**Параметры:**
- `userId` (string, обязательный) - ID пользователя для отправки
- `message` (string, обязательный) - Текст сообщения (поддерживает Markdown)

**Возвращает:**
```json
{
  "success": true,
  "messageId": 123,
  "sentAt": 1234567890
}
```

Или в случае ошибки:
```json
{
  "success": false,
  "error": "Error message"
}
```

## Настройка

1. Создайте файл `.env` в корне проекта (`ai_challenge_november/.env`):
```env
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_USER_ID=your_telegram_user_id
```

2. Настройте `config/telegram-sender-mcp-server.yaml`:
```yaml
mcpServer:
  info:
    name: "Telegram Sender MCP Server"
    version: "1.0.0"
    description: "MCP Server for sending messages to Telegram users"

telegram:
  botToken: "${TELEGRAM_BOT_TOKEN}"
  defaultUserId: "${TELEGRAM_USER_ID}"
```

## Сборка

```bash
cd telegram-sender-mcp-server
./gradlew build
```

Создаст JAR файл: `build/libs/telegram-sender-mcp-server-1.0.0.jar`

## Запуск

```bash
./gradlew run
```

Или через JAR:
```bash
java -jar build/libs/telegram-sender-mcp-server-1.0.0.jar
```

## Тестирование

MCP сервер работает в stdio режиме. Для тестирования можно использовать MCP клиент или подключить его к основному приложению через конфигурацию.

## Требования

- Java 17+
- Gradle 8.14+
- Telegram Bot Token (получить через [@BotFather](https://t.me/BotFather))
- Telegram User ID (получить через [@userinfobot](https://t.me/userinfobot))

