package com.prike.presentation.dto

import com.prike.data.dto.TechnicalSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * DTO для ответа в чат
 * Может содержать либо продолжение диалога, либо готовое ТЗ
 * Используется @JsonClassDiscriminator для полиморфной десериализации по полю "type"
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class ChatResponseDto {
    /**
     * Продолжение диалога (агент задает вопросы или отвечает)
     */
    @Serializable
    @SerialName("continue")
    data class Continue(
        val message: String,
        val debug: DebugInfo? = null
    ) : ChatResponseDto()
    
    /**
     * ТЗ готово
     */
    @Serializable
    @SerialName("tzReady")
    data class TZReady(
        val technicalSpec: TechnicalSpec,
        val debug: DebugInfo? = null
    ) : ChatResponseDto()
    
    /**
     * Отладочная информация (JSON запрос и ответ от LLM)
     */
    @Serializable
    data class DebugInfo(
        val llmRequest: String,
        val llmResponse: String
    )
}
