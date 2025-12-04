package com.prike.taskmcpserver.server

import com.prike.taskmcpserver.tools.ToolRegistry
import com.prike.mcpcommon.server.BaseMCPServer
import io.modelcontextprotocol.kotlin.sdk.Implementation

/**
 * Task MCP Server
 * Наследуется от BaseMCPServer для использования общей логики запуска
 */
class TaskMCPServer(
    serverInfo: Implementation,
    toolRegistry: ToolRegistry
) : BaseMCPServer(serverInfo, toolRegistry)

