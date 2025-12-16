package com.voiceagent.domain.entity

data class ChatMessage(
    val content: String,
    val role: MessageRole
) {
    init {
        require(content.isNotBlank()) { "Содержимое сообщения не может быть пустым" }
        require(content.length <= 2000) { "Сообщение слишком длинное (максимум 2000 символов)" }
    }
}

enum class MessageRole {
    USER,
    ASSISTANT
}

