package com.prike.config

data class TemperatureLessonConfig(
    val defaultQuestion: String,
    val defaultTemperatures: List<Double>,
    val comparisonTemperature: Double
)

