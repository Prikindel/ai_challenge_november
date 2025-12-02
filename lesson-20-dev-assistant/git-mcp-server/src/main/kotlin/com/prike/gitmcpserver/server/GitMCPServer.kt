package com.prike.gitmcpserver.server

import com.prike.gitmcpserver.tools.ToolRegistry
import com.prike.mcpcommon.server.BaseMCPServer
import io.modelcontextprotocol.kotlin.sdk.Implementation

/**
 * Git MCP Server
 * Наследуется от BaseMCPServer для использования общей логики запуска
 */
class GitMCPServer(
    serverInfo: Implementation,
    toolRegistry: ToolRegistry
) : BaseMCPServer(serverInfo, toolRegistry)

