package com.prike.crmmcpserver.model

/**
 * Модель пользователя в CRM системе
 */
data class User(
    val id: String,
    val email: String,
    val name: String?,
    val status: UserStatus,
    val subscription: Subscription?,
    val createdAt: Long
)

/**
 * Статус пользователя
 */
enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}

/**
 * Подписка пользователя
 */
data class Subscription(
    val plan: String,
    val expiresAt: Long?
)

