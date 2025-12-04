package com.prike.ragmcpserver.server

import com.prike.ragmcpserver.tools.ToolRegistry
import com.prike.mcpcommon.server.BaseMCPServer
import io.modelcontextprotocol.kotlin.sdk.Implementation

/**
 * RAG MCP Server
 * Наследуется от BaseMCPServer для использования общей логики запуска
 */
class RagMCPServer(
    serverInfo: Implementation,
    toolRegistry: ToolRegistry
) : BaseMCPServer(serverInfo, toolRegistry)

