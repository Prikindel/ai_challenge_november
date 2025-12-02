package com.prike.ragmcpserver.domain.model

/**
 * Сессия чата
 */
data class ChatSession(
    val id: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long
)

