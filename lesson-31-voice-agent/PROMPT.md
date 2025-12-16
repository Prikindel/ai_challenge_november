# Промпт для реализации: 🔥 День 31. Голосовой агент (Speech → LLM → Text)

Ты — разработчик, создающий урок «🔥 День 31. Голосовой агент» в модуле `lesson-31-voice-agent`. База проекта — простой чат (рекомендуется `lesson-01-simple-chat-agent` или `lesson-19-rag-chat`), но теперь добавляем распознавание речи через Vosk.

## 🎯 Цель урока

Создать голосового агента, который распознаёт голосовые команды, отправляет их в локальную LLM и возвращает текстовый ответ.

### Ключевая идея

```
Голос → Запись в браузере → Отправка на сервер → Vosk распознавание → Текст → LLM → Текстовый ответ
```

**Система должна:**
- Записывать голос в браузере (Web Audio API)
- Распознавать речь через Vosk (локально)
- Отправлять текст в локальную LLM (VPS из урока 28)
- Возвращать текстовый ответ
- Работать полностью локально (без облачных сервисов)

---

## 📋 Поэтапная реализация (отдельные коммиты)

**КРИТИЧЕСКИ ВАЖНО:**
- Каждый перечисленный шаг = отдельный git-коммит.
- После каждого шага **СТОП**, покажи изменения пользователю и дождись «ок, продолжай».
- Пользователь сам делает merge/commit далее.

---

### Коммит 1: Выбор базового урока и подготовка

**Задача:** выбрать простой чат и подготовить структуру для голосового агента.

**Действия:**
1. Выбрать базовый урок (рекомендуется `lesson-01-simple-chat-agent` или `lesson-19-rag-chat`)
2. Скопировать структуру проекта в `lesson-31-voice-agent/`
3. Обновить названия:
   - Папка проекта, Gradle settings, package references
   - Все текстовые упоминания → `lesson-31-voice-agent`
   - «День X» → «День 31»
4. Убедиться, что проект собирается и запускается

**После коммита:** остановиться, показать структуру проекта.

---

### Коммит 2: Установка Vosk и зависимостей

**Задача:** добавить зависимости Vosk и подготовить структуру для распознавания речи.

**Компоненты:**

1. **Зависимости** (`server/build.gradle.kts`):
   ```kotlin
   dependencies {
       // Vosk для распознавания речи
       implementation("com.alphacephei:vosk:0.3.45")
       implementation("net.java.dev.jna:jna:5.13.0")  // JNI для Vosk
       
       // Для работы с аудио
       implementation("org.apache.commons:commons-compress:1.21")
       
       // Остальные зависимости из базового урока
   }
   ```

2. **Структура папок:**
   ```
   lesson-31-voice-agent/
   ├── models/
   │   └── vosk-model-small-ru-0.22/  # Модель Vosk (скачать отдельно)
   └── server/
   ```

3. **Инструкция по установке модели** (`VOSK_SETUP.md`):
   - Ссылка на скачивание модели
   - Инструкции по распаковке
   - Проверка установки

**После коммита:** остановиться, показать инструкции по установке модели.

---

### Коммит 3: Сервис распознавания речи (Vosk)

**Задача:** создать сервис для распознавания речи через Vosk.

**Компоненты:**

1. **Сервис распознавания** (`domain/service/SpeechRecognitionService.kt`):
   ```kotlin
   import com.alphacephei.vosk.Model
   import com.alphacephei.vosk.Recognizer
   import java.io.File
   
   class SpeechRecognitionService(
       private val modelPath: String = "models/vosk-model-small-ru-0.22"
   ) {
       private var model: Model? = null
       private var recognizer: Recognizer? = null
       
       init {
           loadModel()
       }
       
       private fun loadModel() {
           try {
               model = Model(modelPath)
               recognizer = Recognizer(model, 16000f)  // 16kHz sample rate
           } catch (e: Exception) {
               throw IllegalStateException("Failed to load Vosk model: ${e.message}")
           }
       }
       
       fun recognize(audioData: ByteArray): String {
           val rec = recognizer ?: throw IllegalStateException("Recognizer not initialized")
           
           // Vosk работает с 16kHz, mono, 16-bit PCM
           rec.acceptWaveForm(audioData, audioData.size)
           
           val result = rec.getResult()
           val finalResult = rec.getFinalResult()
           
           return parseResult(finalResult.ifEmpty { result })
       }
       
       private fun parseResult(json: String): String {
           // Парсинг JSON результата Vosk
           // {"text": "распознанный текст"}
           // Использовать kotlinx.serialization или простой парсинг
       }
       
       fun close() {
           recognizer?.close()
           model?.close()
       }
   }
   ```

2. **Модель результата** (`domain/model/SpeechRecognitionResult.kt`):
   ```kotlin
   data class SpeechRecognitionResult(
       val text: String,
       val confidence: Double? = null
   )
   ```

3. **Конфигурация** (`config/server.yaml`):
   ```yaml
   speechRecognition:
     enabled: true
     provider: "vosk"
     modelPath: "models/vosk-model-small-ru-0.22"
     sampleRate: 16000
   ```

**После коммита:** остановиться.

---

### Коммит 4: API endpoint для распознавания речи

**Задача:** создать API endpoint для приёма аудио и распознавания речи.

**Компоненты:**

1. **Контроллер** (`presentation/controller/VoiceController.kt`):
   ```kotlin
   class VoiceController(
       private val speechRecognitionService: SpeechRecognitionService,
       private val llmService: LLMService
   ) {
       fun Application.voiceRoutes() {
           route("/api/voice") {
               post("/recognize") {
                   try {
                       val multipart = call.receiveMultipart()
                       var audioData: ByteArray? = null
                       var contentType: String? = null
                       
                       multipart.forEachPart { part ->
                           when (part) {
                               is PartData.FileItem -> {
                                   audioData = part.streamProvider().readBytes()
                                   contentType = part.contentType?.toString()
                               }
                               else -> {}
                           }
                           part.dispose()
                       }
                       
                       if (audioData == null) {
                           call.respond(HttpStatusCode.BadRequest, 
                               mapOf("error" to "No audio data provided"))
                           return@post
                       }
                       
                       // Распознавание речи
                       val recognizedText = speechRecognitionService.recognize(audioData)
                       
                       call.respond(mapOf(
                           "text" to recognizedText,
                           "status" to "success"
                       ))
                   } catch (e: Exception) {
                       call.respond(HttpStatusCode.InternalServerError,
                           mapOf("error" to e.message))
                   }
               }
               
               post("/process") {
                   // Полный цикл: распознавание → LLM → ответ
                   try {
                       val multipart = call.receiveMultipart()
                       var audioData: ByteArray? = null
                       
                       multipart.forEachPart { part ->
                           when (part) {
                               is PartData.FileItem -> {
                                   audioData = part.streamProvider().readBytes()
                               }
                               else -> {}
                           }
                           part.dispose()
                       }
                       
                       if (audioData == null) {
                           call.respond(HttpStatusCode.BadRequest,
                               mapOf("error" to "No audio data provided"))
                           return@post
                       }
                       
                       // 1. Распознавание речи
                       val recognizedText = speechRecognitionService.recognize(audioData)
                       
                       if (recognizedText.isBlank()) {
                           call.respond(mapOf(
                               "error" to "Could not recognize speech",
                               "text" to ""
                           ))
                           return@post
                       }
                       
                       // 2. Отправка в LLM
                       val llmResponse = llmService.generateResponse(recognizedText)
                       
                       // 3. Возврат результата
                       call.respond(mapOf(
                           "recognizedText" to recognizedText,
                           "response" to llmResponse,
                           "status" to "success"
                       ))
                   } catch (e: Exception) {
                       call.respond(HttpStatusCode.InternalServerError,
                           mapOf("error" to e.message))
                   }
               }
           }
       }
   }
   ```

2. **Регистрация routes** (`Main.kt`):
   ```kotlin
   val voiceController = VoiceController(speechRecognitionService, llmService)
   voiceController.voiceRoutes(application)
   ```

**После коммита:** остановиться.

---

### Коммит 5: Frontend для записи голоса

**Задача:** создать UI для записи голоса и отправки на сервер.

**Компоненты:**

1. **HTML** (`client/index.html` или `client/voice.html`):
   ```html
   <div class="voice-interface">
       <h2>Голосовой агент</h2>
       
       <div class="recording-controls">
           <button id="recordBtn" class="record-button">
               🎤 Начать запись
           </button>
           <button id="stopBtn" class="stop-button" disabled>
               ⏹ Остановить
           </button>
       </div>
       
       <div id="status" class="status"></div>
       
       <div class="results">
           <div class="recognized-text">
               <h3>Распознанный текст:</h3>
               <p id="recognizedText">-</p>
           </div>
           
           <div class="llm-response">
               <h3>Ответ агента:</h3>
               <p id="llmResponse">-</p>
           </div>
       </div>
   </div>
   ```

2. **JavaScript** (`client/voice.js`):
   ```javascript
   let mediaRecorder;
   let audioChunks = [];
   let isRecording = false;
   
   const recordBtn = document.getElementById('recordBtn');
   const stopBtn = document.getElementById('stopBtn');
   const status = document.getElementById('status');
   const recognizedText = document.getElementById('recognizedText');
   const llmResponse = document.getElementById('llmResponse');
   
   recordBtn.addEventListener('click', async () => {
       try {
           const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
           
           // Настройка MediaRecorder для 16kHz, mono
           const options = {
               mimeType: 'audio/webm;codecs=opus',
               audioBitsPerSecond: 16000
           };
           
           mediaRecorder = new MediaRecorder(stream, options);
           audioChunks = [];
           
           mediaRecorder.ondataavailable = (event) => {
               audioChunks.push(event.data);
           };
           
           mediaRecorder.onstop = async () => {
               const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
               await sendAudioToServer(audioBlob);
               
               stream.getTracks().forEach(track => track.stop());
           };
           
           mediaRecorder.start();
           isRecording = true;
           recordBtn.disabled = true;
           stopBtn.disabled = false;
           status.textContent = '🎤 Запись...';
       } catch (error) {
           console.error('Error accessing microphone:', error);
           status.textContent = 'Ошибка доступа к микрофону';
       }
   });
   
   stopBtn.addEventListener('click', () => {
       if (mediaRecorder && isRecording) {
           mediaRecorder.stop();
           isRecording = false;
           recordBtn.disabled = false;
           stopBtn.disabled = true;
           status.textContent = '⏳ Обработка...';
       }
   });
   
   async function sendAudioToServer(audioBlob) {
       const formData = new FormData();
       formData.append('audio', audioBlob, 'recording.webm');
       
       try {
           const response = await fetch('/api/voice/process', {
               method: 'POST',
               body: formData
           });
           
           const result = await response.json();
           
           if (result.status === 'success') {
               recognizedText.textContent = result.recognizedText || '-';
               llmResponse.textContent = result.response || '-';
               status.textContent = '✅ Готово';
           } else {
               status.textContent = '❌ Ошибка: ' + (result.error || 'Неизвестная ошибка');
           }
       } catch (error) {
           console.error('Error sending audio:', error);
           status.textContent = '❌ Ошибка отправки';
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 6: Конвертация аудио формата

**Задача:** добавить конвертацию аудио в формат, поддерживаемый Vosk (16kHz, mono, 16-bit PCM).

**Компоненты:**

1. **Сервис конвертации** (`domain/service/AudioConversionService.kt`):
   ```kotlin
   import java.io.File
   import java.io.ByteArrayOutputStream
   
   class AudioConversionService {
       /**
        * Конвертирует аудио в формат для Vosk (16kHz, mono, 16-bit PCM WAV)
        * Использует ffmpeg через ProcessBuilder
        */
       fun convertToVoskFormat(
           inputAudio: ByteArray,
           inputFormat: String = "webm"
       ): ByteArray {
           // Создаём временный файл для входного аудио
           val inputFile = File.createTempFile("input_", ".$inputFormat")
           val outputFile = File.createTempFile("output_", ".wav")
           
           try {
               inputFile.writeBytes(inputAudio)
               
               // Конвертация через ffmpeg
               val process = ProcessBuilder(
                   "ffmpeg",
                   "-i", inputFile.absolutePath,
                   "-ar", "16000",      // Sample rate 16kHz
                   "-ac", "1",           // Mono
                   "-f", "s16le",        // 16-bit PCM
                   "-y",                 // Overwrite
                   outputFile.absolutePath
               ).start()
               
               process.waitFor()
               
               if (process.exitValue() != 0) {
                   throw IllegalStateException("FFmpeg conversion failed")
               }
               
               return outputFile.readBytes()
           } finally {
               inputFile.delete()
               outputFile.delete()
           }
       }
       
       /**
        * Альтернатива: конвертация через библиотеку (если ffmpeg недоступен)
        */
       fun convertToVoskFormatAlternative(audioData: ByteArray): ByteArray {
           // Использовать библиотеку для конвертации (например, TarsosDSP)
           // Или возвращать как есть, если формат уже подходит
           return audioData
       }
   }
   ```

2. **Обновление VoiceController**:
   ```kotlin
   class VoiceController(
       private val speechRecognitionService: SpeechRecognitionService,
       private val llmService: LLMService,
       private val audioConversionService: AudioConversionService
   ) {
       // В методе process добавить конвертацию перед распознаванием
       val convertedAudio = audioConversionService.convertToVoskFormat(audioData)
       val recognizedText = speechRecognitionService.recognize(convertedAudio)
   }
   ```

3. **Проверка ffmpeg** (`domain/service/SystemCheckService.kt`):
   ```kotlin
   class SystemCheckService {
       fun checkFFmpeg(): Boolean {
           return try {
               val process = ProcessBuilder("ffmpeg", "-version").start()
               process.waitFor()
               process.exitValue() == 0
           } catch (e: Exception) {
               false
           }
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 7: Интеграция с локальной LLM

**Задача:** подключить локальную LLM из урока 28 для генерации ответов.

**Компоненты:**

1. **Обновление конфигурации** (`config/server.yaml`):
   ```yaml
   localLLM:
     enabled: true
     provider: "ollama"
     baseUrl: "https://185.31.165.227"  # VPS из урока 28
     model: "llama3.2"
     auth:
       type: "basic"
       user: "user"
       password: "pass"
     parameters:
       temperature: 0.7
       maxTokens: 2048
   ```

2. **Использование LLMService** (уже есть в VoiceController):
   - Убедиться, что LLMService использует локальную LLM
   - Проверить подключение к VPS

**После коммита:** остановиться.

---

### Коммит 8: Тестирование и примеры запросов

**Задача:** протестировать на разных типах запросов и создать примеры.

**Действия:**
1. Создать файл `TESTING.md` с тестовыми запросами:
   - "Посчитай 2 плюс 2"
   - "Дай определение рефакторинга"
   - "Скажи анекдот"
   - "Что такое искусственный интеллект?"
   - "Объясни разницу между async и await"

2. Протестировать каждый запрос:
   - Записать голосом
   - Проверить распознавание
   - Проверить ответ LLM
   - Зафиксировать результаты

3. Создать `EXAMPLES.md` с примерами использования

**После коммита:** остановиться.

---

### Коммит 9: Документация и инструкции

**Задача:** создать полную документацию по установке и использованию.

**Действия:**
1. Обновить `README.md`:
   - Описание урока
   - Инструкции по установке Vosk
   - Инструкции по установке ffmpeg
   - Примеры использования

2. Создать `VOSK_SETUP.md`:
   - Пошаговая установка модели Vosk
   - Проверка установки
   - Решение проблем

3. Создать `AUDIO_FORMAT.md`:
   - Требования к формату аудио
   - Конвертация форматов
   - Альтернативы ffmpeg

**После коммита:** финал.

---

## Технические детали

### Требования к аудио для Vosk

- **Формат:** WAV, 16-bit PCM
- **Sample rate:** 16 kHz
- **Каналы:** Mono (1 канал)
- **Размер:** до 10 МБ (рекомендуется)

### Установка Vosk модели

1. **Скачать модель:**
   ```bash
   # С официального сайта
   wget https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip
   
   # Или через браузер
   # https://alphacephei.com/vosk/models
   ```

2. **Распаковать:**
   ```bash
   unzip vosk-model-small-ru-0.22.zip -d models/
   ```

3. **Проверить структуру:**
   ```
   models/vosk-model-small-ru-0.22/
   ├── am/
   ├── graph/
   ├── ivector/
   └── conf/
   ```

### Установка ffmpeg

**macOS:**
```bash
brew install ffmpeg
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install ffmpeg
```

**Windows:**
```bash
# Через Chocolatey
choco install ffmpeg

# Или скачать с https://ffmpeg.org/download.html
```

### Альтернатива без ffmpeg

Если ffmpeg недоступен, можно:
- Использовать библиотеку для конвертации аудио (TarsosDSP, JAudioTagger)
- Принимать аудио уже в нужном формате (WAV, 16kHz)
- Использовать браузерную конвертацию (Web Audio API)

## Риски и рекомендации

- **Модель Vosk:** убедиться, что модель скачана и распакована
- **ffmpeg:** проверить наличие перед запуском
- **Формат аудио:** конвертировать в правильный формат
- **Память:** Vosk модель загружается в память при старте
- **Производительность:** распознавание может быть медленным на слабых машинах

