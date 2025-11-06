package com.prike.data.mapper

import com.prike.data.dto.TZResponse
import com.prike.data.dto.TechnicalSpec
import com.prike.domain.exception.AIServiceException
import kotlinx.serialization.json.Json

/**
 * Маппер для преобразования ответов от LLM в объекты ТЗ
 * Выполняет парсинг и валидацию данных
 */
class TZMapper {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Распарсить ответ от LLM
     * Ожидается JSON в формате TZResponse (status и content)
     * 
     * @param message ответ от LLM (должен быть валидным JSON)
     * @return результат парсинга: либо TechnicalSpec (если status="ready"), либо строка (если status="continue")
     */
    fun parseResponse(message: String): TZResponseResult {
        // Пытаемся распарсить ответ как TZResponse
        val response = try {
            json.decodeFromString<TZResponse>(message)
        } catch (e: Exception) {
            // Если не удалось распарсить как TZResponse, значит это не JSON или неправильный формат
            throw AIServiceException("Ответ от LLM не является валидным JSON в формате TZResponse: ${e.message}", e)
        }
        
        // Проверяем статус
        return when (response.status.lowercase()) {
            "ready" -> {
                // Если готово, парсим content как TechnicalSpec
                // content может быть либо JSON строкой, либо уже JSON объектом
                val technicalSpec = try {
                    // Пытаемся распарсить content как TechnicalSpec
                    // Если content - это JSON объект в виде строки, decodeFromString его распарсит
                    json.decodeFromString<TechnicalSpec>(response.content)
                } catch (e: Exception) {
                    throw AIServiceException("Не удалось распарсить TechnicalSpec из content. Content должен быть валидным JSON объектом TechnicalSpec: ${e.message}", e)
                }
                TZResponseResult.Ready(technicalSpec)
            }
            "continue" -> {
                // Если продолжаем, возвращаем content как строку
                TZResponseResult.Continue(response.content)
            }
            else -> {
                throw AIServiceException("Неизвестный статус в ответе: ${response.status}. Ожидается 'ready' или 'continue'")
            }
        }
    }
    
    /**
     * Результат парсинга ответа от LLM
     */
    sealed class TZResponseResult {
        /**
         * ТЗ готово
         */
        data class Ready(val technicalSpec: TechnicalSpec) : TZResponseResult()
        
        /**
         * Продолжаем диалог
         */
        data class Continue(val message: String) : TZResponseResult()
    }
}

