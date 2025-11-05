package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * Структурированная информация о животном
 */
@Serializable
data class AnimalInfo(
    val name: String,
    val description: String,
    val diet: String,
    val lifespan: String,
    val habitat: String
)

