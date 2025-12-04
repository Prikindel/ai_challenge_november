package com.prike.domain.model

/**
 * Запрос к ассистенту команды
 */
data class TeamRequest(
    val question: String
)

/**
 * Контекст команды для ответа
 */
data class TeamContext(
    val projectStatus: ProjectStatus? = null,
    val tasks: List<Task> = emptyList(),
    val ragContext: String = ""
)

/**
 * Ответ ассистента команды
 */
data class TeamResponse(
    val answer: String,
    val tasks: List<Task> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val actions: List<Action> = emptyList(),
    val sources: List<Source> = emptyList()
)

/**
 * Рекомендация по задаче
 */
data class Recommendation(
    val priority: String,
    val task: Task?,
    val reason: String
)

/**
 * Действие для выполнения
 */
data class Action(
    val type: ActionType,
    val description: String,
    val task: Task? = null
)

/**
 * Тип действия
 */
enum class ActionType {
    UPDATE_TASK,
    CREATE_TASK,
    VIEW_TASK,
    VIEW_STATUS
}

/**
 * Статус проекта
 */
data class ProjectStatus(
    val totalTasks: Int,
    val tasksByStatus: Map<String, Int>,
    val tasksByPriority: Map<String, Int>,
    val blockedTasks: Int,
    val tasksInProgress: Int,
    val tasksDone: Int
)

/**
 * Задача команды
 */
data class Task(
    val id: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: Priority, // Используем общий Priority из SupportModels
    val assignee: String?,
    val dueDate: Long?,
    val blockedBy: List<String> = emptyList(),
    val blocks: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Статус задачи
 */
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,
    BLOCKED
}

// Source уже определён в SupportModels.kt

