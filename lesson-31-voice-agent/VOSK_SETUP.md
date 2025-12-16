# Установка Vosk для распознавания речи

Пошаговая инструкция по установке и настройке Vosk для голосового агента.

## Что такое Vosk?

Vosk — это библиотека для офлайн-распознавания речи с открытым исходным кодом. Поддерживает множество языков, включая русский.

**Официальный сайт:** https://alphacephei.com/vosk/

## Шаг 1: Скачивание модели

### Вариант 1: Через wget/curl

```bash
# Создать папку для моделей
mkdir -p models

# Скачать русскую модель (small, ~40 МБ)
cd models
wget https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip

# Или через curl
curl -L -o vosk-model-small-ru-0.22.zip \
  https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip
```

### Вариант 2: Через браузер

1. Откройте: https://alphacephei.com/vosk/models
2. Найдите модель `vosk-model-small-ru-0.22`
3. Скачайте ZIP файл
4. Сохраните в папку `lesson-31-voice-agent/models/`

## Шаг 2: Распаковка модели

```bash
cd models
unzip vosk-model-small-ru-0.22.zip

# Проверить структуру
ls -la vosk-model-small-ru-0.22/
```

**Ожидаемая структура:**
```
vosk-model-small-ru-0.22/
├── am/
│   └── final.mdl
├── graph/
│   ├── HCLG.fst
│   └── words.txt
├── ivector/
│   └── final.ie
└── conf/
    ├── mfcc.conf
    └── model.conf
```

## Шаг 3: Проверка установки

### Проверка структуры

```bash
# Должна существовать папка с моделью
ls models/vosk-model-small-ru-0.22/

# Должны быть файлы:
# - am/final.mdl
# - graph/HCLG.fst
# - conf/model.conf
```

### Проверка через код

Создайте тестовый файл `test-vosk.kt`:

```kotlin
import com.alphacephei.vosk.Model
import com.alphacephei.vosk.Recognizer

fun main() {
    try {
        val model = Model("models/vosk-model-small-ru-0.22")
        println("✅ Модель загружена успешно!")
        model.close()
    } catch (e: Exception) {
        println("❌ Ошибка загрузки модели: ${e.message}")
    }
}
```

## Доступные модели

### Русские модели

| Модель | Размер | Точность | Скорость | Рекомендация |
|--------|--------|----------|----------|--------------|
| `vosk-model-small-ru-0.22` | ~40 МБ | Средняя | Быстрая | ✅ **Рекомендуется** для начала |
| `vosk-model-ru-0.22` | ~1.5 ГБ | Высокая | Средняя | Для лучшей точности |
| `vosk-model-ru-0.42` | ~1.8 ГБ | Очень высокая | Медленная | Для максимальной точности |

### Английские модели

| Модель | Размер | Описание |
|--------|--------|----------|
| `vosk-model-small-en-us-0.15` | ~40 МБ | Английская (US) |
| `vosk-model-en-us-0.22` | ~1.8 ГБ | Английская (US), высокая точность |

**Ссылки на все модели:** https://alphacephei.com/vosk/models

## Настройка пути к модели

### В конфигурации

**config/server.yaml:**
```yaml
speechRecognition:
  enabled: true
  provider: "vosk"
  modelPath: "models/vosk-model-small-ru-0.22"  # Относительный путь
  # или абсолютный путь:
  # modelPath: "/full/path/to/models/vosk-model-small-ru-0.22"
  sampleRate: 16000
```

### В коде

```kotlin
// Относительный путь (от корня проекта)
val model = Model("models/vosk-model-small-ru-0.22")

// Абсолютный путь
val model = Model("/Users/username/projects/lesson-31-voice-agent/models/vosk-model-small-ru-0.22")
```

## Требования

### Системные требования

- **Java:** 8+ (рекомендуется 11+)
- **Память:** минимум 512 МБ свободной RAM для small модели
- **Диск:** ~50 МБ для small модели, ~2 ГБ для большой модели

### Зависимости

Vosk требует JNA (Java Native Access) для работы с нативными библиотеками. Зависимости добавляются автоматически через Gradle:

```kotlin
dependencies {
    implementation("com.alphacephei:vosk:0.3.45")
    implementation("net.java.dev.jna:jna:5.13.0")
}
```

## Решение проблем

### Ошибка: "Model not found"

**Проблема:** Модель не найдена по указанному пути.

**Решение:**
1. Проверьте путь к модели в конфигурации
2. Убедитесь, что модель распакована
3. Проверьте права доступа к папке модели
4. Используйте абсолютный путь для отладки

### Ошибка: "Failed to load native library"

**Проблема:** Не удалось загрузить нативную библиотеку Vosk.

**Решение:**
1. Убедитесь, что JNA установлена: `implementation("net.java.dev.jna:jna:5.13.0")`
2. Проверьте совместимость с вашей ОС (Windows/Mac/Linux)
3. Пересоберите проект: `./gradlew clean build`

### Ошибка: "Out of memory"

**Проблема:** Недостаточно памяти для загрузки модели.

**Решение:**
1. Используйте smaller модель (small вместо большой)
2. Увеличьте heap size: `-Xmx1g` в JVM опциях
3. Закройте другие приложения

### Модель не распознаёт речь

**Проблема:** Модель загружается, но не распознаёт речь.

**Решение:**
1. Проверьте формат аудио (16kHz, mono, 16-bit PCM)
2. Убедитесь, что используете правильную модель (ru для русского)
3. Проверьте качество аудио (шум, громкость)
4. Попробуйте другую модель (больше размер = лучше точность)

## Альтернативные модели

Если `vosk-model-small-ru-0.22` не подходит, можно использовать:

1. **vosk-model-ru-0.22** — большая модель, выше точность
2. **vosk-model-ru-0.42** — самая точная русская модель
3. **vosk-model-small-en-us-0.15** — для английского языка

## Обновление модели

Для обновления модели:

```bash
# Удалить старую модель
rm -rf models/vosk-model-small-ru-0.22

# Скачать новую версию
wget https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip

# Распаковать
unzip vosk-model-small-ru-0.22.zip
```

## Проверка работоспособности

После установки проверьте:

1. ✅ Модель скачана и распакована
2. ✅ Путь к модели указан правильно в конфиге
3. ✅ Зависимости добавлены в `build.gradle.kts`
4. ✅ Проект собирается без ошибок
5. ✅ Модель загружается при старте сервера

## Дополнительные ресурсы

- **Официальный сайт:** https://alphacephei.com/vosk/
- **GitHub:** https://github.com/alphacephei/vosk
- **Документация:** https://alphacephei.com/vosk/
- **Модели:** https://alphacephei.com/vosk/models

