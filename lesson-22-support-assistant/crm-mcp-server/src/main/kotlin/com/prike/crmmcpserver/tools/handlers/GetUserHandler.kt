package com.prike.crmmcpserver.tools.handlers

import com.prike.crmmcpserver.storage.InMemoryCRMStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для получения пользователя
 */
data class GetUserParams(
    val userId: String? = null,
    val email: String? = null
)

/**
 * Обработчик для инструмента get_user
 */
class GetUserHandler(
    private val storage: InMemoryCRMStorage
) : ToolHandler<GetUserParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetUserHandler::class.java)
    
    override fun execute(params: GetUserParams): String {
        logger.info("Получение пользователя: userId=${params.userId}, email=${params.email}")
        
        val user = when {
            params.userId != null -> storage.getUser(params.userId)
            params.email != null -> storage.getUserByEmail(params.email)
            else -> throw IllegalArgumentException("Необходимо указать userId или email")
        }
        
        if (user == null) {
            return "Пользователь не найден"
        }
        
        return buildJsonObject {
            put("id", user.id)
            put("email", user.email)
            put("name", user.name ?: "")
            put("status", user.status.name)
            put("subscription", buildJsonObject {
                if (user.subscription != null) {
                    put("plan", user.subscription.plan)
                    user.subscription.expiresAt?.let { put("expiresAt", it) }
                } else {
                    put("plan", "")
                    put("expiresAt", JsonNull)
                }
            })
            put("createdAt", user.createdAt)
        }.toString()
    }
    
    override fun prepareResult(request: GetUserParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): GetUserParams {
            return GetUserParams(
                userId = arguments["userId"]?.jsonPrimitive?.content,
                email = arguments["email"]?.jsonPrimitive?.content
            )
        }
    }
}

