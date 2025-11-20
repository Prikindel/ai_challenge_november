import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "com.prike.mcpserver"
version = "1.0.0"

application {
    mainClass.set("com.prike.mcpserver.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server (требуется для MCP SDK)
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-sse:3.2.3")
    
    // Ktor Client для работы с Telegram Bot API
    implementation("io.ktor:ktor-client-core:3.2.3")
    implementation("io.ktor:ktor-client-cio:3.2.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    
    // Kotlinx IO (требуется для MCP SDK транспорта)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")
    
    // YAML parsing для конфигурации
    implementation("org.yaml:snakeyaml:2.2")
    
    // Dotenv для загрузки переменных окружения из .env файла
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    
    // MCP Kotlin SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.7")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    workingDir = file(".")
}

// Задача для создания JAR файла
tasks.jar {
    archiveBaseName.set("telegram-sender-mcp-server")
    manifest {
        attributes["Main-Class"] = "com.prike.mcpserver.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

