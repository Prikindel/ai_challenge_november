package com.prike.data.repository

import com.prike.data.client.MCPClient
import com.prike.data.dto.Tool
import com.prike.data.dto.Resource
import com.prike.domain.exception.MCPException
import java.io.InputStream
import java.io.OutputStream

class MCPRepository(
    private val mcpClient: MCPClient
) {
    suspend fun connectToServer(
        inputStream: InputStream,
        outputStream: OutputStream
    ) {
        try {
            mcpClient.connectStdio(inputStream, outputStream)
        } catch (e: Exception) {
            throw MCPException("Failed to connect to MCP server: ${e.message}", e)
        }
    }
    
    suspend fun getTools(): List<Tool> {
        return try {
            mcpClient.listTools()
        } catch (e: Exception) {
            throw MCPException("Failed to list tools: ${e.message}", e)
        }
    }
    
    suspend fun getResources(): List<Resource> {
        return try {
            mcpClient.listResources()
        } catch (e: Exception) {
            // Некоторые MCP серверы не поддерживают ресурсы - это нормально
            // Возвращаем пустой список вместо ошибки
            if (e.message?.contains("does not support resources") == true ||
                e.message?.contains("not support resources") == true) {
                emptyList()
            } else {
                throw MCPException("Failed to list resources: ${e.message}", e)
            }
        }
    }
    
    suspend fun disconnect() {
        mcpClient.disconnect()
    }
    
    fun isConnected(): Boolean = mcpClient.isConnected()
}

