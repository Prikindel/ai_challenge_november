import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "com.prike"
version = "1.0.0"

application {
    mainClass.set("com.prike.MainKt")
}

repositories {
    mavenCentral()
    // JetBrains Space для Koog
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/koog/maven")
    }
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-netty:3.2.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    implementation("io.ktor:ktor-server-cors:3.2.3")
    implementation("io.ktor:ktor-server-call-logging:3.2.3")
    
    // Ktor Client
    implementation("io.ktor:ktor-client-core:3.2.3")
    implementation("io.ktor:ktor-client-cio:3.2.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.2.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    
    // YAML parsing для конфигурации
    implementation("org.yaml:snakeyaml:2.2")
    
    // Dotenv для загрузки переменных окружения
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    
    // SQLite для БД
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    // Exposed для работы с БД
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    
    // Koog framework для создания AI-агентов
    implementation("ai.koog:koog-agents:0.5.4")
    
    // Дополнительные зависимости Koog могут потребоваться для LLM провайдеров
    // Пока закомментировано, так как зависимость не найдена в репозиториях
    // Возможно, executor уже входит в koog-agents
    // implementation("ai.koog:koog-llm-openai:0.5.4") // для OpenAI
    // implementation("ai.koog:koog-llm-anthropic:0.5.4") // для Anthropic
    
    // MCP Kotlin SDK для Telegram интеграции
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.7")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.22")
}

tasks.test {
    useJUnitPlatform()
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

