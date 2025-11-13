package com.prike.config

import java.io.File

/**
 * Настройки урока по сжатию диалога.
 */
data class DialogCompressionConfig(
    val lesson: LessonSettings,
    val scenarios: List<Scenario>
) {
    data class LessonSettings(
        val summaryInterval: Int,
        val maxSummariesInContext: Int,
        val rawHistoryLimit: Int,
        val compressionModel: String,
        val compressionPromptTemplate: String,
        val defaultScenarioId: String?
    )

    data class Scenario(
        val id: String,
        val description: String,
        val seedMessages: List<Message>
    ) {
        data class Message(
            val role: String,
            val content: String
        )
    }
}

object DialogCompressionConfigLoader {
    fun load(configFile: File, yamlProvider: (File) -> Map<String, Any?>): DialogCompressionConfig {
        val root = yamlProvider(configFile)

        val lessonSection = (root["lesson"] as? Map<*, *>)?.asStringMap()
            ?: error("Секция lesson в ${configFile.absolutePath} не найдена")

        val summaryInterval = lessonSection["summaryInterval"]?.toIntOrNull()
            ?: error("lesson.summaryInterval должен быть числом")
        val maxSummariesInContext = lessonSection["maxSummariesInContext"]?.toIntOrNull()
            ?: error("lesson.maxSummariesInContext должен быть числом")
        val rawHistoryLimit = lessonSection["rawHistoryLimit"]?.toIntOrNull()
            ?: error("lesson.rawHistoryLimit должен быть числом")
        val compressionModel = lessonSection["compressionModel"].orEmpty()
        require(compressionModel.isNotBlank()) { "lesson.compressionModel не должен быть пустым" }

        val compressionPromptTemplate = lessonSection["compressionPromptTemplate"].orEmpty()
        require(compressionPromptTemplate.isNotBlank()) { "lesson.compressionPromptTemplate не должен быть пустым" }

        val defaultScenarioId = lessonSection["defaultScenarioId"]?.takeIf { !it.isNullOrBlank() }

        val scenariosSection = (root["scenarios"] as? List<*>)
            ?: error("Секция scenarios в ${configFile.absolutePath} должна быть массивом")

        val scenarios = scenariosSection.mapIndexed { index, node ->
            val map = (node as? Map<*, *>)?.asStringMap()
                ?: error("scenarios[$index] должен быть объектом")

            val id = map["id"].orEmpty()
            require(id.isNotBlank()) { "scenarios[$index].id не должен быть пустым" }

            val description = map["description"].orEmpty()
            val seedMessagesSection = (node as? Map<*, *>)?.get("seedMessages") as? List<*>
                ?: emptyList<Any?>()

            val seedMessages = seedMessagesSection.mapIndexed { messageIndex, messageNode ->
                val messageMap = (messageNode as? Map<*, *>)?.asStringMap()
                    ?: error("scenarios[$index].seedMessages[$messageIndex] должен быть объектом")
                val role = messageMap["role"].orEmpty()
                val content = messageMap["content"].orEmpty()
                require(role.isNotBlank()) { "scenarios[$index].seedMessages[$messageIndex].role не должен быть пустым" }
                require(content.isNotBlank()) { "scenarios[$index].seedMessages[$messageIndex].content не должен быть пустым" }
                DialogCompressionConfig.Scenario.Message(role = role, content = content)
            }

            DialogCompressionConfig.Scenario(
                id = id,
                description = description,
                seedMessages = seedMessages
            )
        }

        return DialogCompressionConfig(
            lesson = DialogCompressionConfig.LessonSettings(
                summaryInterval = summaryInterval,
                maxSummariesInContext = maxSummariesInContext,
                rawHistoryLimit = rawHistoryLimit,
                compressionModel = compressionModel,
                compressionPromptTemplate = compressionPromptTemplate,
                defaultScenarioId = defaultScenarioId
            ),
            scenarios = scenarios
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.asStringMap(): Map<String, String?> =
        entries.associate { (key, value) -> key.toString() to value?.toString() }

    private fun String?.orEmpty(): String = this ?: ""
}
