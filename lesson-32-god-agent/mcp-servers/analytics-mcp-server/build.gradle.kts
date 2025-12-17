import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "com.prike.analyticsmcpserver"
version = "1.0.0"

application {
    mainClass.set("com.prike.analyticsmcpserver.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // MCP SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.7")
    
    // Ktor Server (требуется для MCP SDK)
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-sse:3.2.3")
    
    // Ktor Client (требуется для MCP SDK)
    implementation("io.ktor:ktor-client-core:3.2.3")
    implementation("io.ktor:ktor-client-cio:3.2.3")
    
    // Kotlinx IO (требуется для MCP SDK транспорта)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    
    // SQLite для работы с БД
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    // CSV парсинг
    implementation("com.opencsv:opencsv:5.9")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
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
    archiveBaseName.set("analytics-mcp-server")
    manifest {
        attributes["Main-Class"] = "com.prike.analyticsmcpserver.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

