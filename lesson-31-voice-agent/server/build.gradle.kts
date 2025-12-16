import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "com.voiceagent"
version = "1.0.0"

application {
    mainClass.set("com.voiceagent.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("io.ktor:ktor-server-call-logging:2.3.7")
    implementation("io.ktor:ktor-server-partial-content:2.3.7")
    
    // Ktor Client (для OpenAI API)
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // YAML parsing для конфигурации
    implementation("org.yaml:snakeyaml:2.2")
    
    // Dotenv для загрузки переменных окружения из .env файла
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // Vosk + JNA
    implementation("com.alphacephei:vosk:0.3.45")
    implementation("net.java.dev.jna:jna:5.13.0")

    // Утилиты для работы с аудио/архивами (для Vosk моделей)
    implementation("org.apache.commons:commons-compress:1.21")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

