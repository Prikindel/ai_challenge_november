package com.prike.data.repository

import com.prike.config.Config
import com.prike.domain.model.UserProfile
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter

/**
 * Репозиторий для работы с профилем пользователя
 */
interface UserProfileRepository {
    fun getProfile(userId: String = "default"): UserProfile
    fun saveProfile(profile: UserProfile)
    fun updateProfile(userId: String, updates: Map<String, Any>)
    fun reloadProfile()  // Перезагрузка из файла
}

/**
 * Репозиторий профиля на основе конфига YAML
 */
class ConfigUserProfileRepository(
    private val config: Config
) : UserProfileRepository {
    private val logger = LoggerFactory.getLogger(ConfigUserProfileRepository::class.java)
    private var cachedProfile: UserProfile? = null
    private val lessonRoot = findLessonRoot()
    private val profileFile = File(lessonRoot, "config/user-profile.yaml")
    
    override fun getProfile(userId: String): UserProfile {
        // Пока поддерживаем только одного пользователя (default)
        if (userId != "default") {
            logger.warn("Only default user is supported, returning default profile")
        }
        
        // Используем кэш или загружаем из конфига
        return cachedProfile ?: config.userProfile.also { cachedProfile = it }
    }
    
    override fun saveProfile(profile: UserProfile) {
        // Сохранение в config/user-profile.yaml
        saveToYaml(profile, profileFile)
        cachedProfile = profile  // Обновляем кэш
        logger.info("Profile saved for user: ${profile.id}")
    }
    
    override fun updateProfile(userId: String, updates: Map<String, Any>) {
        val currentProfile = getProfile(userId)
        // Простое обновление полей (можно расширить для более сложных случаев)
        val updatedProfile = currentProfile.copy(
            name = updates["name"] as? String ?: currentProfile.name,
            preferences = updates["preferences"] as? com.prike.domain.model.UserPreferences ?: currentProfile.preferences,
            workStyle = updates["workStyle"] as? com.prike.domain.model.WorkStyle ?: currentProfile.workStyle,
            communicationStyle = updates["communicationStyle"] as? com.prike.domain.model.CommunicationStyle ?: currentProfile.communicationStyle,
            context = updates["context"] as? com.prike.domain.model.UserContext ?: currentProfile.context
        )
        saveProfile(updatedProfile)
    }
    
    override fun reloadProfile() {
        // Перезагрузка из файла (для динамического обновления)
        try {
            val yaml = Yaml()
            @Suppress("UNCHECKED_CAST")
            val profileMap = yaml.load<Map<String, Any>>(FileInputStream(profileFile)) as Map<String, Any>
            cachedProfile = parseUserProfile(profileMap)
            logger.info("Profile reloaded from file")
        } catch (e: Exception) {
            logger.error("Failed to reload profile: ${e.message}", e)
            // В случае ошибки используем профиль из конфига
            cachedProfile = config.userProfile
        }
    }
    
    private fun saveToYaml(profile: UserProfile, file: File) {
        try {
            val yamlData = mapOf(
                "user" to mapOf(
                    "id" to profile.id,
                    "name" to profile.name,
                    "preferences" to mapOf(
                        "language" to profile.preferences.language,
                        "responseFormat" to profile.preferences.responseFormat.name.lowercase(),
                        "timezone" to profile.preferences.timezone,
                        "dateFormat" to profile.preferences.dateFormat
                    ),
                    "workStyle" to buildMap {
                        if (profile.workStyle.preferredWorkingHours != null) {
                            put("preferredWorkingHours", profile.workStyle.preferredWorkingHours)
                        }
                        put("focusAreas", profile.workStyle.focusAreas)
                        put("tools", profile.workStyle.tools)
                        put("projects", profile.workStyle.projects)
                        // Сохраняем дополнительные поля
                        putAll(profile.workStyle.extraFields)
                    },
                    "communicationStyle" to mapOf(
                        "tone" to profile.communicationStyle.tone.name.lowercase(),
                        "detailLevel" to profile.communicationStyle.detailLevel.name.lowercase(),
                        "useExamples" to profile.communicationStyle.useExamples,
                        "useEmojis" to profile.communicationStyle.useEmojis
                    ),
                    "context" to mapOf(
                        "currentProject" to (profile.context.currentProject ?: ""),
                        "role" to (profile.context.role ?: ""),
                        "team" to (profile.context.team ?: ""),
                        "goals" to profile.context.goals
                    )
                )
            )
            
            val options = DumperOptions()
            options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            options.isPrettyFlow = true
            val yaml = Yaml(options)
            
            FileWriter(file).use { writer ->
                yaml.dump(yamlData, writer)
            }
        } catch (e: Exception) {
            logger.error("Failed to save profile to YAML: ${e.message}", e)
            throw e
        }
    }
    
    private fun parseUserProfile(yaml: Map<String, Any>): UserProfile {
        @Suppress("UNCHECKED_CAST")
        val user = (yaml["user"] as? Map<String, Any>) ?: throw IllegalArgumentException("User section not found in profile")
        
        @Suppress("UNCHECKED_CAST")
        val preferencesMap = user["preferences"] as? Map<String, Any> ?: emptyMap()
        val preferences = com.prike.domain.model.UserPreferences(
            language = preferencesMap["language"] as? String ?: "ru",
            responseFormat = parseResponseFormat(preferencesMap["responseFormat"] as? String),
            timezone = preferencesMap["timezone"] as? String ?: "Europe/Moscow",
            dateFormat = preferencesMap["dateFormat"] as? String ?: "dd.MM.yyyy"
        )
        
        @Suppress("UNCHECKED_CAST")
        val workStyleMap = user["workStyle"] as? Map<String, Any> ?: emptyMap()
        
        // Обрабатываем дополнительные поля, которые не входят в стандартные параметры
        val standardFields = setOf("preferredWorkingHours", "focusAreas", "tools", "projects")
        val extraFields = workStyleMap
            .filterKeys { it !in standardFields }
            .mapValues { (_, value) -> value.toString() }
            .toMap()
        
        val workStyle = com.prike.domain.model.WorkStyle(
            preferredWorkingHours = (workStyleMap["preferredWorkingHours"] as? String)?.takeIf { it.isNotEmpty() },
            focusAreas = (workStyleMap["focusAreas"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            tools = (workStyleMap["tools"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            projects = (workStyleMap["projects"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            extraFields = extraFields
        )
        
        @Suppress("UNCHECKED_CAST")
        val communicationMap = user["communicationStyle"] as? Map<String, Any> ?: emptyMap()
        val communicationStyle = com.prike.domain.model.CommunicationStyle(
            tone = parseTone(communicationMap["tone"] as? String),
            detailLevel = parseDetailLevel(communicationMap["detailLevel"] as? String),
            useExamples = communicationMap["useExamples"] as? Boolean ?: true,
            useEmojis = communicationMap["useEmojis"] as? Boolean ?: false
        )
        
        @Suppress("UNCHECKED_CAST")
        val contextMap = user["context"] as? Map<String, Any> ?: emptyMap()
        val context = com.prike.domain.model.UserContext(
            currentProject = (contextMap["currentProject"] as? String)?.takeIf { it.isNotEmpty() },
            role = (contextMap["role"] as? String)?.takeIf { it.isNotEmpty() },
            team = (contextMap["team"] as? String)?.takeIf { it.isNotEmpty() },
            goals = (contextMap["goals"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        )
        
        return UserProfile(
            id = user["id"] as? String ?: "default",
            name = user["name"] as? String ?: "Пользователь",
            preferences = preferences,
            workStyle = workStyle,
            communicationStyle = communicationStyle,
            context = context
        )
    }
    
    private fun parseResponseFormat(format: String?): com.prike.domain.model.ResponseFormat {
        return when (format?.lowercase()) {
            "brief" -> com.prike.domain.model.ResponseFormat.BRIEF
            "detailed" -> com.prike.domain.model.ResponseFormat.DETAILED
            "structured" -> com.prike.domain.model.ResponseFormat.STRUCTURED
            else -> com.prike.domain.model.ResponseFormat.DETAILED
        }
    }
    
    private fun parseTone(tone: String?): com.prike.domain.model.Tone {
        return when (tone?.lowercase()) {
            "professional" -> com.prike.domain.model.Tone.PROFESSIONAL
            "casual" -> com.prike.domain.model.Tone.CASUAL
            "friendly" -> com.prike.domain.model.Tone.FRIENDLY
            else -> com.prike.domain.model.Tone.PROFESSIONAL
        }
    }
    
    private fun parseDetailLevel(level: String?): com.prike.domain.model.DetailLevel {
        return when (level?.lowercase()) {
            "low" -> com.prike.domain.model.DetailLevel.LOW
            "medium" -> com.prike.domain.model.DetailLevel.MEDIUM
            "high" -> com.prike.domain.model.DetailLevel.HIGH
            else -> com.prike.domain.model.DetailLevel.MEDIUM
        }
    }
    
    private fun findLessonRoot(): File {
        var currentDir = File(System.getProperty("user.dir"))
        
        if (currentDir.name == "server") {
            currentDir = currentDir.parentFile
        }
        
        var searchDir: File? = currentDir
        while (searchDir != null) {
            if (searchDir.name == "lesson-32-god-agent") {
                return searchDir
            }
            
            val lessonDir = File(searchDir, "lesson-32-god-agent")
            if (lessonDir.exists()) {
                return lessonDir
            }
            
            searchDir = searchDir.parentFile
        }
        
        return currentDir
    }
}

