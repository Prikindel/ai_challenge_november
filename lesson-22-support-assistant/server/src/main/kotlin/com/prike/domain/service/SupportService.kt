package com.prike.domain.service

import com.prike.domain.koog.SupportAgentKoog
import com.prike.domain.model.*
import org.slf4j.LoggerFactory

/**
 * Сервис для обработки вопросов пользователей поддержки
 * Использует Koog для работы с AI агентом и MCP инструментами
 */
class SupportService(
    private val crmMCPService: CRMMCPService?,
    private val ragMCPService: RagMCPService?,
    private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(SupportService::class.java)
    
    private val supportAgent = SupportAgentKoog(crmMCPService, ragMCPService, apiKey)
    
    /**
     * Ответить на вопрос пользователя
     * Использует Koog агента для обработки вопроса
     */
    suspend fun answerQuestion(request: SupportRequest): SupportResponse {
        logger.info("Processing support question with Koog: ticketId=${request.ticketId}, userId=${request.userId}, question=${request.question.take(100)}...")
        
        return supportAgent.answerQuestion(request)
    }
    
}

