package com.prike.taskmcpserver.model

/**
 * Модель проекта
 * Содержит статистику по задачам проекта
 */
data class Project(
    val totalTasks: Int,
    val tasksByStatus: Map<TaskStatus, Int>,
    val tasksByPriority: Map<Priority, Int>,
    val blockedTasks: Int,
    val tasksInProgress: Int,
    val tasksDone: Int
)

