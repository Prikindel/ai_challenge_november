package com.prike.presentation.controller

import com.prike.domain.service.UserProfileService
import com.prike.domain.model.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Ответ при успешном обновлении профиля
 */
@Serializable
data class UpdateProfileResponse(
    val success: Boolean,
    val profile: UserProfile
)

/**
 * Контроллер для работы с профилем пользователя
 */
class ProfileController(
    private val userProfileService: UserProfileService
) {
    private val logger = LoggerFactory.getLogger(ProfileController::class.java)
    
    /**
     * Регистрирует маршруты для профиля
     */
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // GET /api/profile - получить профиль
            get("/api/profile") {
                try {
                    val profile = userProfileService.getProfile("default")
                    call.respond(profile)
                } catch (e: Exception) {
                    logger.error("Error getting profile: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        com.prike.presentation.controller.ErrorResponse("Failed to get profile: ${e.message}")
                    )
                }
            }
            
            // POST /api/profile - обновить профиль
            post("/api/profile") {
                try {
                    val request = call.receive<UpdateProfileRequest>()
                    val currentProfile = userProfileService.getProfile("default")
                    
                    // Обновляем профиль
                    val updatedProfile = request.toUserProfile(currentProfile)
                    
                    // Сохраняем профиль
                    userProfileService.saveProfile(updatedProfile)
                    
                    call.respond(UpdateProfileResponse(
                        success = true,
                        profile = updatedProfile
                    ))
                } catch (e: Exception) {
                    logger.error("Error updating profile: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        com.prike.presentation.controller.ErrorResponse("Failed to update profile: ${e.message}")
                    )
                }
            }
        }
    }
}

/**
 * Запрос на обновление профиля
 */
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val preferences: UpdatePreferencesRequest? = null,
    val workStyle: UpdateWorkStyleRequest? = null,
    val communicationStyle: UpdateCommunicationStyleRequest? = null,
    val context: UpdateUserContextRequest? = null
) {
    fun toUserProfile(current: UserProfile): UserProfile {
        return current.copy(
            name = name ?: current.name,
            preferences = preferences?.toUserPreferences(current.preferences) ?: current.preferences,
            workStyle = workStyle?.toWorkStyle(current.workStyle) ?: current.workStyle,
            communicationStyle = communicationStyle?.toCommunicationStyle(current.communicationStyle) ?: current.communicationStyle,
            context = context?.toUserContext(current.context) ?: current.context
        )
    }
}

@Serializable
data class UpdatePreferencesRequest(
    val language: String? = null,
    val responseFormat: String? = null,
    val timezone: String? = null,
    val dateFormat: String? = null
) {
    fun toUserPreferences(current: UserPreferences): UserPreferences {
        return current.copy(
            language = language ?: current.language,
            responseFormat = responseFormat?.let { parseResponseFormat(it) } ?: current.responseFormat,
            timezone = timezone ?: current.timezone,
            dateFormat = dateFormat ?: current.dateFormat
        )
    }
    
    private fun parseResponseFormat(format: String): ResponseFormat {
        return when (format.lowercase()) {
            "brief" -> ResponseFormat.BRIEF
            "detailed" -> ResponseFormat.DETAILED
            "structured" -> ResponseFormat.STRUCTURED
            else -> ResponseFormat.DETAILED
        }
    }
}

@Serializable
data class UpdateWorkStyleRequest(
    val preferredWorkingHours: String? = null,
    val focusAreas: List<String>? = null,
    val tools: List<String>? = null,
    val projects: List<String>? = null
) {
    fun toWorkStyle(current: WorkStyle): WorkStyle {
        return current.copy(
            preferredWorkingHours = preferredWorkingHours ?: current.preferredWorkingHours,
            focusAreas = focusAreas ?: current.focusAreas,
            tools = tools ?: current.tools,
            projects = projects ?: current.projects
        )
    }
}

@Serializable
data class UpdateCommunicationStyleRequest(
    val tone: String? = null,
    val detailLevel: String? = null,
    val useExamples: Boolean? = null,
    val useEmojis: Boolean? = null
) {
    fun toCommunicationStyle(current: CommunicationStyle): CommunicationStyle {
        return current.copy(
            tone = tone?.let { parseTone(it) } ?: current.tone,
            detailLevel = detailLevel?.let { parseDetailLevel(it) } ?: current.detailLevel,
            useExamples = useExamples ?: current.useExamples,
            useEmojis = useEmojis ?: current.useEmojis
        )
    }
    
    private fun parseTone(tone: String): Tone {
        return when (tone.lowercase()) {
            "professional" -> Tone.PROFESSIONAL
            "casual" -> Tone.CASUAL
            "friendly" -> Tone.FRIENDLY
            else -> Tone.PROFESSIONAL
        }
    }
    
    private fun parseDetailLevel(level: String): DetailLevel {
        return when (level.lowercase()) {
            "low" -> DetailLevel.LOW
            "medium" -> DetailLevel.MEDIUM
            "high" -> DetailLevel.HIGH
            else -> DetailLevel.MEDIUM
        }
    }
}

@Serializable
data class UpdateUserContextRequest(
    val currentProject: String? = null,
    val role: String? = null,
    val team: String? = null,
    val goals: List<String>? = null
) {
    fun toUserContext(current: UserContext): UserContext {
        return current.copy(
            currentProject = currentProject ?: current.currentProject,
            role = role ?: current.role,
            team = team ?: current.team,
            goals = goals ?: current.goals
        )
    }
}

