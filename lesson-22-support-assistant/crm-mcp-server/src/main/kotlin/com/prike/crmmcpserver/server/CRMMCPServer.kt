package com.prike.crmmcpserver.server

import com.prike.crmmcpserver.tools.ToolRegistry
import com.prike.mcpcommon.server.BaseMCPServer
import io.modelcontextprotocol.kotlin.sdk.Implementation

/**
 * CRM MCP Server
 * Наследуется от BaseMCPServer для использования общей логики запуска
 */
class CRMMCPServer(
    serverInfo: Implementation,
    toolRegistry: ToolRegistry
) : BaseMCPServer(serverInfo, toolRegistry)

