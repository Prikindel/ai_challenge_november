package com.prike.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Ответ энциклопедии животных
 * Может быть либо успешным ответом с информацией о животном,
 * либо ошибкой валидации темы
 * 
 * Используется @JsonClassDiscriminator для полиморфной десериализации по полю "type"
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class AnimalEncyclopediaResponse {
    /**
     * Успешный ответ с информацией о животном
     * Используется через полиморфную десериализацию JSON
     */
    @Serializable
    @SerialName("success")
    @Suppress("unused")
    data class Success(
        val data: AnimalInfo
    ) : AnimalEncyclopediaResponse()
    
    /**
     * Ошибка валидации темы
     * Используется через полиморфную десериализацию JSON
     */
    @Serializable
    @SerialName("error")
    @Suppress("unused")
    data class Error(
        val error: TopicValidationError
    ) : AnimalEncyclopediaResponse()
}

