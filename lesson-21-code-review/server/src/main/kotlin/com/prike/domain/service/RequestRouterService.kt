package com.prike.domain.service

import com.prike.data.client.MCPTool
import com.prike.mcpcommon.dto.MessageDto
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Результат роутинга запроса
 */
data class RoutingDecision(
    val action: ActionType,
    val toolName: String? = null,  // Имя MCP инструмента
    val toolArguments: JsonObject? = null,  // Аргументы для инструмента
    val reasoning: String? = null  // Объяснение решения
)

enum class ActionType {
    RAG_SEARCH,           // Использовать RAG поиск (rag_search)
    RAG_SEARCH_PROJECT,   // Использовать RAG поиск в project docs (rag_search_project_docs)
    MCP_TOOL,             // Использовать MCP инструмент (read_file, list_directory и т.д.)
    DIRECT_ANSWER         // Ответить напрямую без инструментов
}

/**
 * Сервис для динамического роутинга запросов через LLM
 * LLM видит список доступных инструментов и решает, что использовать
 */
class RequestRouterService(
    private val llmService: LLMService,
    private val gitMCPService: GitMCPService?,
    private val ragMCPService: RagMCPService?
) {
    private val logger = LoggerFactory.getLogger(RequestRouterService::class.java)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Определяет, какое действие нужно выполнить для запроса
     * 
     * @param question вопрос пользователя
     * @return решение о роутинге
     */
    suspend fun route(question: String): RoutingDecision {
        logger.info("Routing request: $question")
        
        // Получаем список доступных инструментов
        val allTools = mutableListOf<MCPTool>()
        
        try {
            if (gitMCPService != null && gitMCPService.isConnected()) {
                val gitTools = gitMCPService.listTools()
                allTools.addAll(gitTools)
                logger.debug("Found ${gitTools.size} Git MCP tools")
            }
        } catch (e: Exception) {
            logger.warn("Failed to get Git MCP tools: ${e.message}")
        }
        
        try {
            if (ragMCPService != null && ragMCPService.isConnected()) {
                val ragTools = ragMCPService.listTools()
                allTools.addAll(ragTools)
                logger.debug("Found ${ragTools.size} RAG MCP tools")
            }
        } catch (e: Exception) {
            logger.warn("Failed to get RAG MCP tools: ${e.message}")
        }
        
        if (allTools.isEmpty()) {
            logger.warn("No MCP tools available, falling back to RAG_SEARCH")
            return RoutingDecision(
                action = ActionType.RAG_SEARCH,
                reasoning = "No MCP tools available"
            )
        }
        
        // Формируем промпт для LLM с описанием всех инструментов
        val toolsDescription = buildToolsDescription(allTools)
        val prompt = buildRoutingPrompt(question, toolsDescription)
        
        // Формируем messages для LLM
        val messages = listOf(
            MessageDto(role = "system", content = buildSystemPrompt()),
            MessageDto(role = "user", content = prompt)
        )
        
        // Вызываем LLM для принятия решения с JSON mode для структурированного ответа
        val response = try {
            llmService.generateStructuredJsonAnswer(messages)
        } catch (e: Exception) {
            logger.error("Failed to get routing decision from LLM: ${e.message}", e)
            // Fallback на RAG поиск
            return RoutingDecision(
                action = ActionType.RAG_SEARCH,
                reasoning = "LLM routing failed: ${e.message}"
            )
        }
        
        // Парсим ответ LLM (теперь это гарантированно валидный JSON)
        return parseRoutingDecision(response.answer, allTools)
    }
    
    /**
     * Формирует описание инструментов для промпта
     */
    private fun buildToolsDescription(tools: List<MCPTool>): String {
        return buildString {
            appendLine("Доступные инструменты:")
            appendLine()
            
            // Группируем по категориям
            val ragTools = tools.filter { it.name.startsWith("rag_") }
            val gitTools = tools.filter { !it.name.startsWith("rag_") }
            
            if (ragTools.isNotEmpty()) {
                appendLine("RAG инструменты (для поиска по документации):")
                ragTools.forEach { tool ->
                    appendLine("- ${tool.name}: ${tool.description}")
                }
                appendLine()
            }
            
            if (gitTools.isNotEmpty()) {
                appendLine("Git/File инструменты (для работы с файлами и репозиторием):")
                gitTools.forEach { tool ->
                    appendLine("- ${tool.name}: ${tool.description}")
                }
                appendLine()
            }
        }
    }
    
    /**
     * Формирует системный промпт для роутера
     */
    private fun buildSystemPrompt(): String {
        return """Ты — роутер запросов для ассистента разработчика.

Твоя задача — определить, какой инструмент нужно использовать для ответа на вопрос пользователя.

Правила выбора инструмента:
1. **rag_search_project_docs** - используй для:
   - Вопросов об API (как работает API, API endpoints, методы API)
   - Вопросов о структуре проекта
   - Вопросов о схеме данных
   - Вопросов о правилах стиля кода
   - Вопросов о документации проекта (project/docs/ и project/README.md)

2. **rag_search** - используй для:
   - Общих вопросов о проекте (не связанных с API или структурой)
   - Вопросов о функциях и возможностях системы
   - Вопросов, на которые может ответить общая документация

3. **read_file** - используй для:
   - Запросов на чтение конкретного файла ("покажи содержимое X.md", "прочитай файл Y")
   - Когда нужно увидеть исходный код или содержимое файла
   - Параметр: {"path": "путь_к_файлу"} (например, {"path": "project/docs/api.md"})

4. **list_directory** - используй для:
   - Запросов на список файлов ("какие файлы в X", "что в директории Y")
   - Когда нужно узнать структуру директории
   - Параметр: {"path": "путь_к_директории"} (например, {"path": "project/docs"})

5. **get_current_branch** - используй для:
   - Запросов о текущей ветке git репозитория

ВАЖНО: 
- Для вопросов об API ВСЕГДА используй RAG_SEARCH_PROJECT (rag_search_project_docs)
- Ты должен вернуть ТОЛЬКО валидный JSON объект без дополнительного текста или markdown

Формат JSON:
{
  "action": "RAG_SEARCH" | "RAG_SEARCH_PROJECT" | "MCP_TOOL" | "DIRECT_ANSWER",
  "toolName": "имя инструмента" | null,
  "toolArguments": {"param": "value"} | null,
  "reasoning": "краткое объяснение"
}

Если action = "MCP_TOOL", обязательно укажи toolName (строку) и toolArguments (объект).
Если action = "RAG_SEARCH" или "RAG_SEARCH_PROJECT", установи toolName и toolArguments в null.

ВАЖНО для MCP_TOOL:
- Для list_directory: toolArguments = {"path": "путь_к_директории"}
- Для read_file: toolArguments = {"path": "путь_к_файлу"}
- НЕ используй action = "DIRECT_ANSWER" если нужно вызвать инструмент - используй "MCP_TOOL"!"""
    }
    
    /**
     * Формирует промпт для роутера
     */
    private fun buildRoutingPrompt(question: String, toolsDescription: String): String {
        return """
$toolsDescription

Вопрос пользователя: "$question"

Определи, какой инструмент использовать, и верни JSON с решением.
""".trimIndent()
    }
    
    /**
     * Парсит решение роутера из ответа LLM
     */
    private fun parseRoutingDecision(
        llmResponse: String,
        availableTools: List<MCPTool>
    ): RoutingDecision {
        return try {
            // Извлекаем JSON из ответа (может быть обернут в markdown код)
            val jsonText = extractJsonFromResponse(llmResponse)
            val json = json.parseToJsonElement(jsonText)
            
            if (json !is JsonObject) {
                throw IllegalArgumentException("Response is not a JSON object")
            }
            
            val actionStr = json["action"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing 'action' field")
            
            // Если action = DIRECT_ANSWER, но указан toolName, меняем на MCP_TOOL
            val rawAction = when (actionStr) {
                "RAG_SEARCH" -> ActionType.RAG_SEARCH
                "RAG_SEARCH_PROJECT" -> ActionType.RAG_SEARCH_PROJECT
                "MCP_TOOL" -> ActionType.MCP_TOOL
                "DIRECT_ANSWER" -> ActionType.DIRECT_ANSWER
                else -> {
                    logger.warn("Unknown action: $actionStr, falling back to RAG_SEARCH")
                    ActionType.RAG_SEARCH
                }
            }
            
            // Если action = DIRECT_ANSWER, но указан toolName, меняем на MCP_TOOL
            val action = if (rawAction == ActionType.DIRECT_ANSWER) {
                val toolNameElement = json["toolName"]
                val hasToolName = when (toolNameElement) {
                    is JsonPrimitive -> toolNameElement.content.isNotEmpty()
                    is JsonNull, null -> false
                    else -> false
                }
                
                if (hasToolName) {
                    logger.debug("DIRECT_ANSWER with toolName detected, changing to MCP_TOOL")
                    ActionType.MCP_TOOL
                } else {
                    rawAction
                }
            } else {
                rawAction
            }
            
            // Обрабатываем toolName (может быть null или JsonNull)
            val toolName = when (val toolNameElement = json["toolName"]) {
                is JsonPrimitive -> toolNameElement.content
                is JsonNull, null -> null
                else -> null
            }
            
            // Обрабатываем toolArguments (может быть null или JsonNull)
            val toolArguments = when (val toolArgsElement = json["toolArguments"]) {
                is JsonObject -> toolArgsElement
                is JsonNull, null -> null
                else -> null
            }
            
            val reasoning = json["reasoning"]?.jsonPrimitive?.content
            
            // Валидация: если MCP_TOOL, должен быть указан toolName
            if (action == ActionType.MCP_TOOL) {
                if (toolName == null) {
                    logger.warn("MCP_TOOL action without toolName, falling back to RAG_SEARCH")
                    return RoutingDecision(
                        action = ActionType.RAG_SEARCH,
                        reasoning = "MCP_TOOL without toolName"
                    )
                }
                
                // Проверяем, что инструмент существует
                val toolExists = availableTools.any { it.name == toolName }
                if (!toolExists) {
                    logger.warn("Tool $toolName not found, falling back to RAG_SEARCH")
                    return RoutingDecision(
                        action = ActionType.RAG_SEARCH,
                        reasoning = "Tool $toolName not found"
                    )
                }
            }
            
            RoutingDecision(
                action = action,
                toolName = toolName,
                toolArguments = toolArguments,
                reasoning = reasoning
            )
        } catch (e: Exception) {
            logger.error("Failed to parse routing decision: ${e.message}. Response: $llmResponse", e)
            // Fallback на RAG поиск
            RoutingDecision(
                action = ActionType.RAG_SEARCH,
                reasoning = "Failed to parse LLM response: ${e.message}"
            )
        }
    }
    
    /**
     * Извлекает JSON из ответа LLM (может быть обернут в markdown)
     */
    private fun extractJsonFromResponse(response: String): String {
        // Убираем markdown код блоки
        var text = response.trim()
        
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json").trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }
        
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }
        
        return text
    }
}

