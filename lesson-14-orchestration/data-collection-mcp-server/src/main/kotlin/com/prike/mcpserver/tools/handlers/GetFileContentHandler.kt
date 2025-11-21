package com.prike.mcpserver.tools.handlers

import com.prike.mcpserver.data.repository.FileRepository
import io.modelcontextprotocol.kotlin.sdk.TextContent
import org.slf4j.LoggerFactory

/**
 * Обработчик для инструмента get_file_content
 * Читает содержимое файла по указанному пути
 */
class GetFileContentHandler(
    private val fileRepository: FileRepository
) : ToolHandler<GetFileContentHandler.Params, String>() {

    override val logger = LoggerFactory.getLogger(GetFileContentHandler::class.java)

    override fun execute(params: Params): String {
        logger.info("Чтение файла: ${params.filePath}")
        val content = fileRepository.readFileContent(params.filePath)
        logger.info("Файл прочитан, размер: ${content.length} символов")
        return content
    }

    override fun prepareResult(request: Params, result: String): TextContent {
        val resultText = buildString {
            append("Содержимое файла: ${request.filePath}\n")
            append("- Размер: ${result.length} символов\n\n")
            append("Содержимое:\n")
            append(result)
        }

        return TextContent(text = resultText)
    }

    data class Params(
        val filePath: String
    )
}

