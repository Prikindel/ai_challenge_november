import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "com.prike"
version = "1.0.0"

application {
    mainClass.set("com.prike.MainKt")
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
    
    // Ktor Client
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
    
    // SQLite для базы знаний
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.22")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    workingDir = file(".")
}

