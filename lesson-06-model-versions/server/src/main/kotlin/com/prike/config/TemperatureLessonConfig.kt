package com.prike.config

data class ModelDefinitionConfig(
    val id: String,
    val displayName: String,
    val endpoint: String,
    val huggingFaceUrl: String,
    val pricePer1kTokensUsd: Double? = null,
    val defaultParams: Map<String, Any?> = emptyMap()
)

data class ModelComparisonLessonConfig(
    val defaultQuestion: String,
    val defaultModelIds: List<String>,
    val models: List<ModelDefinitionConfig>
)

