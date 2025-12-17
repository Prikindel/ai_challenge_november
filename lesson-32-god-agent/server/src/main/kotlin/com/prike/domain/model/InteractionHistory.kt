package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * История взаимодействия пользователя с агентом
 */
@Serializable
data class InteractionHistory(
    val userId: String,
    val timestamp: Long,
    val question: String,
    val answer: String,
    val feedback: Feedback? = null  // положительный/отрицательный
)

/**
 * Обратная связь от пользователя
 */
@Serializable
data class Feedback(
    val rating: Int,  // 1-5
    val comment: String? = null
)

