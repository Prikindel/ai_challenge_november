package com.prike.mcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Обработчик для инструмента generate_report
 * Генерирует отчёт из заголовка и содержимого
 */
class GenerateReportHandler : ToolHandler<GenerateReportHandler.Params, GenerateReportHandler.ReportResult>() {

    override val logger = LoggerFactory.getLogger(GenerateReportHandler::class.java)

    override fun execute(params: Params): ReportResult {
        logger.info("Генерация отчёта: ${params.title} (формат: ${params.format}, длина содержимого: ${params.content.length})")
        
        val format = params.format.lowercase()
        if (format !in listOf("markdown", "text")) {
            throw IllegalArgumentException("Формат должен быть 'markdown' или 'text', получен: ${params.format}")
        }
        
        // Формируем отчёт
        val report = when (format) {
            "markdown" -> {
                buildString {
                    append("# ${params.title}\n\n")
                    append(params.content)
                }
            }
            else -> {
                buildString {
                    append("${params.title}\n")
                    append("=".repeat(params.title.length))
                    append("\n\n")
                    append(params.content)
                }
            }
        }
        
        logger.info("Отчёт сгенерирован, размер: ${report.length} символов")
        
        return ReportResult(
            report = report,
            format = format,
            length = report.length
        )
    }

    override fun prepareResult(request: Params, result: ReportResult): TextContent {
        val resultJson = buildJsonObject {
            put("report", result.report)
            put("format", result.format)
            put("length", result.length)
        }
        
        return TextContent(text = resultJson.toString())
    }

    data class Params(
        val title: String,
        val content: String,
        val format: String = "markdown"  // "markdown" или "text"
    )
    
    data class ReportResult(
        val report: String,
        val format: String,
        val length: Int
    )
}

