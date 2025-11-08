package com.prike.domain.agent.reasoning

object ReasoningPromptTemplates {
    const val BASE_SYSTEM_PROMPT = """
        Ты — внимательный логик-аналитик. Работай аккуратно, не выдумывай факты.
        В финале перечисли соответствия между участниками и объектами задачи в формате «Имя — предмет».
    """

    const val STEP_BY_STEP_INSTRUCTION = """
        Решай задачу пошагово: перечисли ограничения, рассмотри возможные комбинации, исключи невозможные варианты и сделай вывод.
        В финале перечисли пары «Имя — предмет» на основе найденного решения.
    """

    fun directPrompt(task: String): String = buildString {
        appendLine(task)
        appendLine()
        append("Сделай краткое рассуждение и выведи финальный ответ в требуемом формате.")
    }

    fun stepPrompt(task: String): String = buildString {
        appendLine(task)
        appendLine()
        append(STEP_BY_STEP_INSTRUCTION.trim())
    }

    fun promptGeneratorSystem(): String =
        "Ты генерируешь промты для другой модели. Помоги ей решить логическую задачу корректно."

    fun promptGeneratorUser(task: String): String = buildString {
        appendLine("Составь оптимальный промт для решения задачи другой моделью.")
        appendLine("Промт должен включать чёткие инструкции по анализу и формат финального ответа.")
        appendLine()
        appendLine(task)
        appendLine()
        appendLine("Верни результат строго в JSON формате:")
        appendLine("{")
        appendLine("  \"prompt\": \"...\",")
        appendLine("  \"overview\": \"короткое пояснение почему промт хорош\"")
        appendLine("}")
    }

    fun fallbackPrompt(task: String): String = buildString {
        appendLine("Ты — логический помощник. Вот задача:")
        appendLine()
        appendLine(task)
        appendLine()
        appendLine("Проанализируй ограничения, перечисли возможные варианты, последовательно исключи несоответствующие и сформулируй окончательный вывод.")
        appendLine("В конце перечисли соответствия «Имя — предмет» из задачи, по одному на строку.")
    }

    fun promptExecutionUser(prompt: String, usedFallback: Boolean): String =
        if (usedFallback) prompt
        else buildString {
            appendLine(prompt.trim())
            appendLine()
            append("Не забывай о требуемом формате финального ответа.")
        }

    fun expertUserPrompt(task: String): String = buildString {
        appendLine("Реши задачу:")
        appendLine(task)
        appendLine()
        appendLine("Ответь в формате JSON:")
        appendLine("{")
        appendLine("  \"answer\": \"краткий итоговый ответ\",")
        appendLine("  \"reasoning\": \"подробное пошаговое обоснование\"")
        appendLine("}")
        appendLine()
        appendLine("Финальный ответ в поле \"answer\" перечисли в формате «Имя — предмет», по одному соответствию на строку.")
    }

    fun expertSummaryUserPrompt(entries: List<String>): String = buildString {
        appendLine("Дано заключение трёх экспертов по задаче. Сформируй взвешенный общий итог.")
        appendLine("Опиши общий вывод и укажи, как объединились мнения.")
        appendLine()
        entries.forEach { appendLine(it) }
        appendLine("Подтверди финальный ответ, перечислив соответствия «Имя — предмет».")
    }

    data class ExpertDefinition(
        val name: String,
        val style: String,
        val systemInstruction: String
    )

    val experts = listOf(
        ExpertDefinition(
            name = "Логик",
            style = "Строго следует формальной логике, проверяет каждое утверждение и ищет единственно возможное распределение.",
            systemInstruction = "Ты — Логик. Анализируй задачу строго логическими шагами, избегай предположений без доказательств."
        ),
        ExpertDefinition(
            name = "Аналитик",
            style = "Структурирует данные в таблицу ограничений, использует исключения и перебор.",
            systemInstruction = "Ты — Аналитик. Структурируй факты, работай с ограничениями и исключениями, объясняй ход рассуждений."
        ),
        ExpertDefinition(
            name = "Скептик",
            style = "Проверяет потенциальные ошибки, ищет противоречия, перепроверяет выводы остальных.",
            systemInstruction = "Ты — Скептик. Сомневайся в очевидных решениях, ищи альтернативы и подтверждения, но сделай итоговый вывод."
        )
    )
}


