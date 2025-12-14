# Форматы данных для локального аналитика

Описание поддерживаемых форматов данных и рекомендации по их использованию.

## CSV (Comma-Separated Values)

### Формат

**Структура:**
- Первая строка — заголовки (названия колонок)
- Каждая последующая строка — запись данных
- Разделитель: запятая (`,`) или точка с запятой (`;`)

**Пример:**
```csv
timestamp,level,component,message,error
2024-01-01 10:00:00,ERROR,Database,Connection failed,Connection timeout
2024-01-01 10:01:00,ERROR,API,Request failed,Database error
2024-01-01 10:02:00,WARN,Auth,Login attempt,Authentication failed
```

### Требования

- ✅ Кодировка: UTF-8
- ✅ Разделитель: запятая или точка с запятой
- ✅ Первая строка — заголовки
- ✅ Каждая строка — одна запись

### Рекомендации

- Используйте понятные названия колонок
- Избегайте специальных символов в заголовках
- Для дат используйте ISO 8601 формат: `YYYY-MM-DD HH:MM:SS`
- Для чисел используйте точку как разделитель: `123.45`

---

## JSON (JavaScript Object Notation)

### Формат

**Массив объектов (рекомендуется):**
```json
[
  {
    "timestamp": "2024-01-01T10:00:00Z",
    "level": "ERROR",
    "component": "Database",
    "message": "Connection failed",
    "error": "Connection timeout"
  },
  {
    "timestamp": "2024-01-01T10:01:00Z",
    "level": "ERROR",
    "component": "API",
    "message": "Request failed",
    "error": "Database error"
  }
]
```

**Один объект:**
```json
{
  "users": [
    {"id": 1, "name": "John", "city": "New York"},
    {"id": 2, "name": "Jane", "city": "London"}
  ]
}
```

### Требования

- ✅ Валидный JSON
- ✅ Кодировка: UTF-8
- ✅ Поддерживаются вложенные объекты и массивы

### Рекомендации

- Предпочитайте массив объектов для однородных данных
- Используйте понятные ключи
- Для дат используйте ISO 8601: `"2024-01-01T10:00:00Z"`

---

## Логи (Logs)

### Формат JSON логов

**Структура:**
```json
{"timestamp": "2024-01-01T10:00:00Z", "level": "ERROR", "message": "Connection failed"}
{"timestamp": "2024-01-01T10:01:00Z", "level": "INFO", "message": "User logged in"}
```

**Требования:**
- Каждая строка — отдельный JSON объект (JSONL формат)
- Обязательные поля: `timestamp`, `level`, `message`
- Опциональные: `component`, `error`, `stackTrace`

### Формат Plain Text логов

**Структура:**
```
2024-01-01 10:00:00 ERROR [Database] Connection failed: Connection timeout
2024-01-01 10:01:00 INFO [API] Request completed successfully
2024-01-01 10:02:00 WARN [Auth] Login attempt failed
```

**Формат:**
```
TIMESTAMP LEVEL [COMPONENT] MESSAGE
```

**Уровни логирования:**
- `ERROR` — ошибки
- `WARN` — предупреждения
- `INFO` — информационные сообщения
- `DEBUG` — отладочная информация

### Поддерживаемые форматы логов

1. **JSON Lines (JSONL)** — каждая строка отдельный JSON
2. **Plain Text** — текстовые логи с временными метками
3. **Log4j** — формат Apache Log4j
4. **Winston** — формат Winston (Node.js)

### Рекомендации

- Используйте JSON логи для структурированных данных
- Plain text логи должны содержать timestamp и level
- Для больших файлов используйте JSONL формат

---

## Примеры файлов

### Пример CSV: errors.csv

```csv
timestamp,level,component,message,error,count
2024-01-01 10:00:00,ERROR,Database,Connection failed,Connection timeout,5
2024-01-01 10:01:00,ERROR,API,Request failed,Database error,3
2024-01-01 10:02:00,WARN,Auth,Login attempt,Authentication failed,2
2024-01-01 10:03:00,ERROR,Database,Connection failed,Connection timeout,4
```

### Пример JSON: users.json

```json
[
  {
    "id": 1,
    "name": "John",
    "email": "john@example.com",
    "city": "New York",
    "status": "active",
    "created_at": "2024-01-01T10:00:00Z"
  },
  {
    "id": 2,
    "name": "Jane",
    "email": "jane@example.com",
    "city": "London",
    "status": "inactive",
    "created_at": "2024-01-02T11:00:00Z"
  }
]
```

### Пример JSON логов: app.log

```json
{"timestamp": "2024-01-01T10:00:00Z", "level": "ERROR", "component": "Database", "message": "Connection failed", "error": "Connection timeout"}
{"timestamp": "2024-01-01T10:01:00Z", "level": "INFO", "component": "API", "message": "Request completed"}
{"timestamp": "2024-01-01T10:02:00Z", "level": "WARN", "component": "Auth", "message": "Login attempt failed"}
```

### Пример Plain Text логов: app.log

```
2024-01-01 10:00:00 ERROR [Database] Connection failed: Connection timeout
2024-01-01 10:01:00 INFO [API] Request completed successfully
2024-01-01 10:02:00 WARN [Auth] Login attempt failed: Invalid credentials
2024-01-01 10:03:00 ERROR [Database] Connection failed: Connection timeout
```

---

## Ограничения

### Размер файла

- **Максимальный размер:** 10 МБ
- **Рекомендуемый размер:** до 5 МБ
- **Количество записей:** до 10,000 за файл

### Кодировка

- **Поддерживается:** UTF-8
- **Не поддерживается:** другие кодировки (ISO-8859-1, Windows-1251 и т.д.)

### Структура данных

- **CSV:** до 50 колонок
- **JSON:** до 5 уровней вложенности
- **Логи:** до 1000 строк за раз

---

## Рекомендации по подготовке данных

### Для лучшего анализа

1. **Структурируйте данные:**
   - Используйте понятные названия полей
   - Стандартизируйте форматы (даты, числа)
   - Избегайте пустых значений

2. **Организуйте данные:**
   - Группируйте связанные данные
   - Используйте временные метки
   - Добавляйте категории/типы

3. **Оптимизируйте размер:**
   - Удаляйте ненужные поля
   - Агрегируйте данные при необходимости
   - Разбивайте большие файлы на части

### Примеры хороших данных

✅ **Хорошо:**
```csv
date,user_id,action,status,duration_ms
2024-01-01,123,login,success,150
2024-01-01,456,login,failed,50
```

❌ **Плохо:**
```csv
d,u,a,s,d
2024-01-01,123,login,success,150
```

---

## Часто задаваемые вопросы

### Можно ли загрузить несколько файлов?

Да, можно загрузить несколько файлов разных типов. Данные будут объединены в одной БД.

### Как обрабатываются дубликаты?

Дубликаты определяются по комбинации полей. При загрузке можно выбрать: пропустить или обновить.

### Поддерживаются ли вложенные структуры в JSON?

Да, поддерживаются вложенные объекты и массивы до 5 уровней.

### Можно ли загрузить файл с другой кодировкой?

Нет, поддерживается только UTF-8. Конвертируйте файл перед загрузкой.

### Какой формат лучше для анализа?

Для структурированных данных — CSV или JSON. Для логов — JSON логи (JSONL).

