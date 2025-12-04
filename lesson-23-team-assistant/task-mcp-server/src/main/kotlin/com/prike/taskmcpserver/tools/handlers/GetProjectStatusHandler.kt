package com.prike.taskmcpserver.tools.handlers

import com.prike.taskmcpserver.storage.TaskStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Обработчик для инструмента get_project_status
 */
class GetProjectStatusHandler(
    private val storage: TaskStorage
) : ToolHandler<Unit, String>() {
    
    override val logger = LoggerFactory.getLogger(GetProjectStatusHandler::class.java)
    
    override fun execute(params: Unit): String {
        logger.info("Получение статуса проекта")
        
        val project = storage.getProjectStatus()
        
        val tasksInProgress = project.tasksByStatus[com.prike.taskmcpserver.model.TaskStatus.IN_PROGRESS] ?: 0
        val tasksDone = project.tasksByStatus[com.prike.taskmcpserver.model.TaskStatus.DONE] ?: 0
        
        return buildJsonObject {
            put("totalTasks", project.totalTasks)
            put("tasksByStatus", buildJsonObject {
                project.tasksByStatus.forEach { (status, count) ->
                    put(status.name, count)
                }
            })
            put("tasksByPriority", buildJsonObject {
                project.tasksByPriority.forEach { (priority, count) ->
                    put(priority.name, count)
                }
            })
            put("blockedTasks", project.blockedTasks)
            put("tasksInProgress", tasksInProgress)
            put("tasksDone", tasksDone)
        }.toString()
    }
    
    override fun prepareResult(request: Unit, result: String): TextContent {
        return TextContent(text = result)
    }
}

