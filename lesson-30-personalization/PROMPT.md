# Промпт для реализации: 🔥 День 30. Personalization

Ты — разработчик, создающий урок «🔥 День 30. Personalization» в модуле `lesson-30-personalization`. База проекта — приложение из Дня 29 (локальный аналитик) или Дня 24 (анализатор отзывов), но теперь добавляем персонализацию агента.

## 🎯 Цель урока

Добавить персонализацию агенту, чтобы он знал пользователя, его привычки, предпочтения и стиль работы. Персонализация настраивается через конфиг и влияет на поведение агента.

### Ключевая идея

```
Профиль пользователя → Конфиг персонализации → Адаптация промптов → Персонализированные ответы
```

**Система должна:**
- Хранить профиль пользователя (имя, предпочтения, привычки)
- Использовать персонализацию в промптах агента
- Адаптировать стиль общения под пользователя
- Учитывать контекст работы и проекты
- Сохранять историю взаимодействий для улучшения персонализации

---

## 📋 Поэтапная реализация (отдельные коммиты)

**КРИТИЧЕСКИ ВАЖНО:**
- Каждый перечисленный шаг = отдельный git-коммит.
- После каждого шага **СТОП**, покажи изменения пользователю и дождись «ок, продолжай».
- Пользователь сам делает merge/commit далее.

---

### Коммит 1: Выбор базового урока и подготовка

**Задача:** выбрать базовый урок и подготовить структуру для персонализации.

**⚠️ РЕКОМЕНДАЦИЯ: Использовать `lesson-24-real-task` (анализатор отзывов)**

**Почему урок 24:**
- ✅ Полноценный диалоговый AI-агент с Koog фреймворком
- ✅ Реальная практическая задача (анализ отзывов)
- ✅ Уже есть чат-интерфейс и история диалога
- ✅ Использует RAG, MCP, LLM - все технологии
- ✅ Логично сделать его "личным" - "мой анализатор отзывов"
- ✅ Максимальная демонстрация возможностей персонализации

**Альтернативные варианты:**
- `lesson-20-dev-assistant` — личный ассистент разработчика
- `lesson-19-rag-chat` — простой чат с RAG
- `lesson-29-local-analyst` — локальный аналитик

**Действия:**
1. Выбрать базовый урок (рекомендуется `lesson-24-real-task`)
2. Скопировать структуру проекта в `lesson-30-personalization/`
3. Обновить названия:
   - Папка проекта, Gradle settings, package references
   - Все текстовые упоминания → `lesson-30-personalization`
   - «День X» → «День 30»
4. Убедиться, что проект собирается и запускается

**После коммита:** остановиться, показать структуру проекта.

---

### Коммит 2: Модель профиля пользователя

**Задача:** создать модель данных для профиля пользователя.

**Компоненты:**

1. **Модель профиля** (`domain/model/UserProfile.kt`):
   ```kotlin
   data class UserProfile(
       val id: String = "default",
       val name: String = "Пользователь",
       val preferences: UserPreferences,
       val workStyle: WorkStyle,
       val communicationStyle: CommunicationStyle,
       val context: UserContext
   )
   
   data class UserPreferences(
       val language: String = "ru",  // ru, en
       val responseFormat: ResponseFormat = ResponseFormat.DETAILED,
       val timezone: String = "Europe/Moscow",
       val dateFormat: String = "dd.MM.yyyy"
   )
   
   enum class ResponseFormat {
       BRIEF,      // Краткие ответы
       DETAILED,   // Подробные ответы
       STRUCTURED  // Структурированные (списки, таблицы)
   }
   
   data class WorkStyle(
       val preferredWorkingHours: String? = null,  // "09:00-18:00"
       val focusAreas: List<String> = emptyList(),  // ["backend", "frontend", "devops"]
       val tools: List<String> = emptyList(),       // ["git", "docker", "kubernetes"]
       val projects: List<String> = emptyList()     // Названия проектов
   )
   
   data class CommunicationStyle(
       val tone: Tone = Tone.PROFESSIONAL,  // PROFESSIONAL, CASUAL, FRIENDLY
       val detailLevel: DetailLevel = DetailLevel.MEDIUM,  // LOW, MEDIUM, HIGH
       val useExamples: Boolean = true,
       val useEmojis: Boolean = false
   )
   
   enum class Tone {
       PROFESSIONAL,  // Деловой стиль
       CASUAL,        // Неформальный
       FRIENDLY       // Дружелюбный
   }
   
   enum class DetailLevel {
       LOW,     // Минимум деталей
       MEDIUM,  // Средний уровень
       HIGH     // Максимум деталей
   }
   
   data class UserContext(
       val currentProject: String? = null,
       val role: String? = null,  // "developer", "manager", "analyst"
       val team: String? = null,
       val goals: List<String> = emptyList()  // Цели пользователя
   )
   ```

2. **Конфигурация** (`config/user-profile.yaml`):
   ```yaml
   user:
     id: "default"
     name: "Иван"
     preferences:
       language: "ru"
       responseFormat: "detailed"  # brief, detailed, structured
       timezone: "Europe/Moscow"
       dateFormat: "dd.MM.yyyy"
     workStyle:
       preferredWorkingHours: "09:00-18:00"
       focusAreas:
         - "backend"
         - "devops"
       tools:
         - "git"
         - "docker"
         - "kubernetes"
       projects:
         - "ai-challenge"
         - "mobile-app"
     communicationStyle:
       tone: "professional"  # professional, casual, friendly
       detailLevel: "medium"  # low, medium, high
       useExamples: true
       useEmojis: false
     context:
       currentProject: "ai-challenge"
       role: "developer"
       team: "backend"
       goals:
         - "Изучить LLM"
         - "Автоматизировать задачи"
   ```

3. **Загрузка конфига в Config.kt** (`config/Config.kt`):
   ```kotlin
   data class Config(
       val localLLM: LocalLLMConfig,
       val userProfile: UserProfile  // Добавляем загрузку профиля
   ) {
       companion object {
           fun load(): Config {
               val configYaml = loadYaml("config/server.yaml")
               val profileYaml = loadYaml("config/user-profile.yaml")  // Загружаем профиль
               
               return Config(
                   localLLM = parseLocalLLMConfig(configYaml),
                   userProfile = parseUserProfile(profileYaml)  // Парсим профиль
               )
           }
           
           private fun parseUserProfile(yaml: Map<String, Any>): UserProfile {
               val user = yaml["user"] as Map<String, Any>
               // Парсинг всех полей профиля из YAML
           }
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 3: Репозиторий и сервис профиля

**Задача:** создать репозиторий и сервис для работы с профилем пользователя.

**Компоненты:**

1. **Репозиторий** (`data/repository/UserProfileRepository.kt`):
   ```kotlin
   interface UserProfileRepository {
       fun getProfile(userId: String = "default"): UserProfile
       fun saveProfile(profile: UserProfile)
       fun updateProfile(userId: String, updates: Map<String, Any>)
       fun reloadProfile()  // Перезагрузка из файла
   }
   
   class ConfigUserProfileRepository(
       private val config: Config
   ) : UserProfileRepository {
       private var cachedProfile: UserProfile? = null
       
       override fun getProfile(userId: String): UserProfile {
           // Используем кэш или загружаем из конфига
           return cachedProfile ?: config.userProfile.also { cachedProfile = it }
       }
       
       override fun saveProfile(profile: UserProfile) {
           // Сохранение в config/user-profile.yaml
           saveToYaml(profile, "config/user-profile.yaml")
           cachedProfile = profile  // Обновляем кэш
       }
       
       override fun reloadProfile() {
           // Перезагрузка из файла (для динамического обновления)
           val yaml = loadYaml("config/user-profile.yaml")
           cachedProfile = parseUserProfile(yaml)
       }
       
       private fun saveToYaml(profile: UserProfile, path: String) {
           // Сериализация профиля в YAML и сохранение в файл
           // Можно использовать библиотеку snakeyaml или kotlinx.serialization
       }
   }
   ```

2. **Сервис профиля** (`domain/service/UserProfileService.kt`):
   ```kotlin
   class UserProfileService(
       private val repository: UserProfileRepository
   ) {
       fun getProfile(userId: String = "default"): UserProfile {
           return repository.getProfile(userId)
       }
       
       fun buildPersonalizedPrompt(
           basePrompt: String,
           userId: String = "default"
       ): String {
           val profile = getProfile(userId)
           return personalizePrompt(basePrompt, profile)
       }
       
       private fun personalizePrompt(
           prompt: String,
           profile: UserProfile
       ): String {
           val personalization = buildString {
               appendLine("Контекст пользователя:")
               appendLine("- Имя: ${profile.name}")
               if (profile.context.currentProject != null) {
                   appendLine("- Текущий проект: ${profile.context.currentProject}")
               }
               if (profile.context.role != null) {
                   appendLine("- Роль: ${profile.context.role}")
               }
               if (profile.workStyle.focusAreas.isNotEmpty()) {
                   appendLine("- Области интересов: ${profile.workStyle.focusAreas.joinToString(", ")}")
               }
               if (profile.communicationStyle.tone != Tone.PROFESSIONAL) {
                   appendLine("- Стиль общения: ${profile.communicationStyle.tone.name.lowercase()}")
               }
               appendLine("- Формат ответа: ${profile.preferences.responseFormat.name.lowercase()}")
               appendLine("- Уровень детализации: ${profile.communicationStyle.detailLevel.name.lowercase()}")
           }
           
           return """
           $personalization
           
           $prompt
           
           Учти предпочтения пользователя при ответе.
           """.trimIndent()
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 4: Интеграция персонализации в LLMService

**Задача:** интегрировать персонализацию в процесс генерации ответов.

**Компоненты:**

1. **Обновление LLMService** (`domain/service/LLMService.kt`):
   ```kotlin
   class LLMService(
       private val localLLMClient: LocalLLMClient,
       private val userProfileService: UserProfileService,
       private val config: Config
   ) {
       suspend fun generateResponse(
           userMessage: String,
           context: String? = null,
           templateId: String? = null,
           userId: String = "default"
       ): String {
           // Получаем профиль пользователя
           val profile = userProfileService.getProfile(userId)
           
           // Персонализируем промпт
           val basePrompt = buildPrompt(userMessage, context, templateId)
           val personalizedPrompt = userProfileService.buildPersonalizedPrompt(
               basePrompt,
               userId
           )
           
           // Генерируем ответ с учетом персонализации
           val parameters = adjustParametersForProfile(config.localLLM.parameters, profile)
           
           return localLLMClient.generate(
               model = config.localLLM.model,
               prompt = personalizedPrompt,
               parameters = parameters
           )
       }
       
       private fun adjustParametersForProfile(
           baseParameters: LLMParameters,
           profile: UserProfile
       ): LLMParameters {
           // Настройка параметров на основе предпочтений
           val maxTokens = when (profile.preferences.responseFormat) {
               ResponseFormat.BRIEF -> 512
               ResponseFormat.DETAILED -> 2048
               ResponseFormat.STRUCTURED -> 1024
           }
           
           val temperature = when (profile.communicationStyle.detailLevel) {
               DetailLevel.LOW -> 0.5  // Более детерминированные ответы
               DetailLevel.MEDIUM -> 0.7
               DetailLevel.HIGH -> 0.8  // Более разнообразные ответы
           }
           
           return baseParameters.copy(
               maxTokens = maxTokens,
               temperature = temperature
           )
       }
   }
   ```

2. **Обновление ChatController** (`presentation/controller/ChatController.kt`):
   - Добавить параметр `userId` в запросы
   - Передавать `userId` в `LLMService`

**После коммита:** остановиться.

---

### Коммит 5: Персонализированные шаблоны промптов

**Задача:** создать шаблоны промптов с учетом персонализации.

**Компоненты:**

1. **Обновление PromptTemplateService** (`domain/service/PromptTemplateService.kt`):
   ```kotlin
   class PromptTemplateService(
       private val repository: PromptTemplateRepository,
       private val userProfileService: UserProfileService
   ) {
       fun applyTemplate(
           templateId: String,
           userMessage: String,
           context: String? = null,
           userId: String = "default"
       ): String {
           val template = repository.getTemplate(templateId) ?: 
               repository.getTemplate("default")!!
           
           val profile = userProfileService.getProfile(userId)
           
           var result = template.template
           result = result.replace("{user_message}", userMessage)
           if (context != null) {
               result = result.replace("{context}", context)
           }
           
           // Добавляем персонализацию
           result = addPersonalization(result, profile)
           
           return result
       }
       
       private fun addPersonalization(
           prompt: String,
           profile: UserProfile
       ): String {
           val personalization = buildString {
               appendLine("\nПерсонализация:")
               appendLine("- Имя пользователя: ${profile.name}")
               if (profile.context.currentProject != null) {
                   appendLine("- Работаю над проектом: ${profile.context.currentProject}")
               }
               if (profile.workStyle.focusAreas.isNotEmpty()) {
                   appendLine("- Интересуюсь: ${profile.workStyle.focusAreas.joinToString(", ")}")
               }
               appendLine("- Предпочитаю ${profile.preferences.responseFormat.name.lowercase()} ответы")
               appendLine("- Стиль общения: ${profile.communicationStyle.tone.name.lowercase()}")
           }
           
           return "$prompt\n$personalization"
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 6: UI для настройки профиля

**Задача:** создать UI для настройки профиля пользователя.

**Компоненты:**

1. **HTML** (`client/profile.html`):
   - Форма для настройки профиля
   - Поля: имя, язык, формат ответа, стиль общения
   - Настройки работы: рабочие часы, области интересов, инструменты
   - Контекст: текущий проект, роль, команда, цели
   - Кнопка "Сохранить профиль"

2. **JavaScript** (`client/profile.js`):
   - Загрузка текущего профиля
   - Сохранение изменений через API
   - Валидация формы
   - Отображение текущих настроек

3. **API endpoint** (`presentation/controller/ProfileController.kt`):
   ```kotlin
   get("/api/profile") {
       val profile = userProfileService.getProfile()
       call.respond(profile)
   }
   
   post("/api/profile") {
       val request = call.receive<UpdateProfileRequest>()
       userProfileService.updateProfile(request)
       call.respond(mapOf("success" to true))
   }
   ```

**После коммита:** остановиться.

---

### Коммит 7: Адаптация ответов под стиль пользователя

**Задача:** адаптировать формат и стиль ответов под предпочтения пользователя.

**Компоненты:**

1. **Форматирование ответов** (`domain/service/ResponseFormatter.kt`):
   ```kotlin
   class ResponseFormatter(
       private val userProfileService: UserProfileService
   ) {
       fun formatResponse(
           response: String,
           userId: String = "default"
       ): String {
           val profile = userProfileService.getProfile(userId)
           
           return when (profile.preferences.responseFormat) {
               ResponseFormat.BRIEF -> formatBrief(response)
               ResponseFormat.DETAILED -> response
               ResponseFormat.STRUCTURED -> formatStructured(response)
           }
       }
       
       private fun formatBrief(text: String): String {
           // Сокращение ответа до ключевых моментов
           val sentences = text.split(".")
           return sentences.take(3).joinToString(". ") + "."
       }
       
       private fun formatStructured(text: String): String {
           // Форматирование в списки, таблицы
           // Добавление маркеров, заголовков
           return text
       }
   }
   ```

2. **Интеграция в ChatController**:
   - Использовать `ResponseFormatter` перед отправкой ответа
   - Применять форматирование на основе профиля

**После коммита:** остановиться.

---

### Коммит 8: История взаимодействий и обучение

**Задача:** сохранять историю взаимодействий для улучшения персонализации.

**Компоненты:**

1. **Модель истории** (`domain/model/InteractionHistory.kt`):
   ```kotlin
   data class InteractionHistory(
       val userId: String,
       val timestamp: Long,
       val question: String,
       val answer: String,
       val feedback: Feedback? = null  // положительный/отрицательный
   )
   
   data class Feedback(
       val rating: Int,  // 1-5
       val comment: String? = null
   )
   ```

2. **Репозиторий истории** (`data/repository/InteractionHistoryRepository.kt`):
   ```kotlin
   interface InteractionHistoryRepository {
       suspend fun saveInteraction(history: InteractionHistory)
       suspend fun getRecentInteractions(userId: String, limit: Int = 10): List<InteractionHistory>
       suspend fun getFeedback(userId: String): List<Feedback>
   }
   ```

3. **Сервис обучения** (`domain/service/PersonalizationLearningService.kt`):
   ```kotlin
   class PersonalizationLearningService(
       private val historyRepository: InteractionHistoryRepository
   ) {
       fun analyzePreferences(userId: String): UserPreferences {
           // Анализ истории для выявления предпочтений
           val interactions = historyRepository.getRecentInteractions(userId, 50)
           val feedback = historyRepository.getFeedback(userId)
           
           // Определение предпочтений на основе истории
           // Например: если пользователь часто просит краткие ответы → ResponseFormat.BRIEF
       }
   }
   ```

**После коммита:** остановиться.

---

### Коммит 9: Документация и примеры

**Задача:** создать документацию и примеры конфигурации.

**Действия:**
1. Создать `PERSONALIZATION_GUIDE.md`:
   - Описание всех параметров персонализации
   - Примеры конфигураций для разных ролей
   - Рекомендации по настройке

2. Создать примеры конфигов:
   - `config/user-profile-developer.yaml` — для разработчика
   - `config/user-profile-manager.yaml` — для менеджера
   - `config/user-profile-analyst.yaml` — для аналитика

3. Обновить `README.md`:
   - Инструкции по настройке профиля
   - Примеры использования
   - Описание персонализации

**После коммита:** финал.

---

## Технические детали

### Параметры персонализации

**Предпочтения:**
- `language` — язык общения (ru, en)
- `responseFormat` — формат ответа (brief, detailed, structured)
- `timezone` — часовой пояс
- `dateFormat` — формат даты

**Стиль работы:**
- `preferredWorkingHours` — предпочтительные рабочие часы
- `focusAreas` — области интересов
- `tools` — используемые инструменты
- `projects` — проекты пользователя

**Стиль общения:**
- `tone` — тон общения (professional, casual, friendly)
- `detailLevel` — уровень детализации (low, medium, high)
- `useExamples` — использовать примеры
- `useEmojis` — использовать эмодзи

**Контекст:**
- `currentProject` — текущий проект
- `role` — роль пользователя
- `team` — команда
- `goals` — цели пользователя

### Влияние на ответы

**Формат ответа:**
- `BRIEF` — краткие ответы (512 токенов)
- `DETAILED` — подробные ответы (2048 токенов)
- `STRUCTURED` — структурированные (списки, таблицы)

**Тон общения:**
- `PROFESSIONAL` — деловой стиль
- `CASUAL` — неформальный
- `FRIENDLY` — дружелюбный

**Уровень детализации:**
- `LOW` — минимум деталей, temperature 0.5
- `MEDIUM` — средний уровень, temperature 0.7
- `HIGH` — максимум деталей, temperature 0.8

## Риски и рекомендации

- **Конфиг vs БД:** для простоты используем конфиг, но можно добавить БД для динамических изменений
- **Множественные пользователи:** поддержка нескольких профилей через `userId`
- **Обучение:** история взаимодействий для автоматической настройки предпочтений
- **Приватность:** профиль хранится локально, не отправляется в облако

