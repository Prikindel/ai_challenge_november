package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для запроса к ассистенту команды
 */
@Serializable
data class TeamQuestionRequest(
    val question: String
)

/**
 * DTO для рекомендации
 */
@Serializable
data class RecommendationDto(
    val priority: String,
    val task: TaskDto? = null,
    val reason: String
)

/**
 * DTO для действия
 */
@Serializable
data class ActionDto(
    val type: String,
    val description: String,
    val task: TaskDto? = null
)

/**
 * DTO для ответа ассистента команды
 */
@Serializable
data class TeamQuestionResponse(
    val answer: String,
    val tasks: List<TaskDto> = emptyList(),
    val recommendations: List<RecommendationDto> = emptyList(),
    val actions: List<ActionDto> = emptyList(),
    val sources: List<SourceDto> = emptyList()
)

/**
 * DTO для задачи команды
 */
@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val assignee: String? = null,
    val dueDate: Long? = null,
    val blockedBy: List<String> = emptyList(),
    val blocks: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * DTO для создания задачи
 */
@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String,
    val priority: String,
    val assignee: String? = null,
    val dueDate: Long? = null
)

/**
 * DTO для обновления задачи
 */
@Serializable
data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val assignee: String? = null,
    val dueDate: Long? = null
)

/**
 * DTO для списка задач
 */
@Serializable
data class TasksListResponse(
    val tasks: List<TaskDto>
)

/**
 * DTO для статуса проекта
 */
@Serializable
data class ProjectStatusDto(
    val totalTasks: Int,
    val tasksByStatus: Map<String, Int>,
    val tasksByPriority: Map<String, Int>,
    val blockedTasks: Int,
    val tasksInProgress: Int,
    val tasksDone: Int
)

