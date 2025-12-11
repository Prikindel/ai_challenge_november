import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "com.prike.mcpcommon"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Client
    implementation("io.ktor:ktor-client-core:3.2.3")
    implementation("io.ktor:ktor-client-cio:3.2.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.2.3")
    
    // Ktor Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    
    // Ktor Server (для MCP серверов)
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-sse:3.2.3")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    
    // Kotlinx IO (для MCP SDK транспорта)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")
    
    // MCP Kotlin SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.7")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

