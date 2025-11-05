plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.redis.vl.demo"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/milestone")
    }
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

application {
    mainClass.set("com.redis.vl.demo.rag.MultimodalRAGApp")
    // mainModule.set("com.redis.vl.demo.rag")  // Disabled - dependencies not modular
}

dependencies {
    // RedisVL Core
    implementation(project(":core"))

    // SpotBugs annotations (compileOnly - needed to avoid warnings from core module)
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.3")

    // LangChain4J
    implementation("dev.langchain4j:langchain4j:0.36.2")
    implementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:0.36.2")
    implementation("dev.langchain4j:langchain4j-open-ai:0.36.2")
    implementation("dev.langchain4j:langchain4j-azure-open-ai:0.36.2")
    implementation("dev.langchain4j:langchain4j-anthropic:0.36.2")
    implementation("dev.langchain4j:langchain4j-ollama:0.36.2")

    // Redis
    implementation("redis.clients:jedis:5.2.0")

    // PDF Processing
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.pdfbox:pdfbox-tools:3.0.3")

    // Token Counting
    implementation("com.knuddels:jtokkit:1.1.0")

    // JSON Processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    // MaterialFX - Modern Material Design components
    implementation("io.github.palexdev:materialfx:11.17.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.15")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.8.3")

    // TestContainers for integration tests
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Xlint:all",
        "-Xlint:-processing"
    ))
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Docker Compose tasks for Redis Stack
// Find docker command (try common locations)
val dockerCmd = listOf("/usr/local/bin/docker", "/usr/bin/docker", "docker")
    .firstOrNull { file(it).exists() || it == "docker" } ?: "docker"

val dockerComposeUp = tasks.register<Exec>("dockerComposeUp") {
    group = "docker"
    description = "Start Redis Stack using Docker Compose"

    workingDir = projectDir
    commandLine(dockerCmd, "compose", "up", "-d", "--wait")

    doFirst {
        println("Starting Redis Stack container (if not already running)...")
    }

    doLast {
        println("Redis Stack container is ready")
    }
}

val dockerComposeDown = tasks.register<Exec>("dockerComposeDown") {
    group = "docker"
    description = "Stop Redis Stack Docker Compose services"

    workingDir = projectDir
    commandLine(dockerCmd, "compose", "down")

    doLast {
        println("Redis Stack container stopped.")
    }
}

val dockerComposeLogs = tasks.register<Exec>("dockerComposeLogs") {
    group = "docker"
    description = "Show logs from Redis Stack container"

    workingDir = projectDir
    commandLine(dockerCmd, "compose", "logs", "-f")
}

val waitForRedis = tasks.register("waitForRedis") {
    group = "docker"
    description = "Ensure Redis is ready (starts container if needed)"

    dependsOn(dockerComposeUp)

    doLast {
        println("✓ Redis Stack is ready at localhost:6399")
        println("✓ RedisInsight is available at http://localhost:8002")
    }
}

// Run task configuration
tasks.named<JavaExec>("run") {
    // Ensure Redis is running before starting the app
    dependsOn(waitForRedis)

    // Enable preview features if needed
    jvmArgs = listOf(
        "--add-exports", "javafx.graphics/com.sun.javafx.util=ALL-UNNAMED"
    )

    doFirst {
        println("Starting Multimodal RAG Demo...")
        println("Redis Stack available at: localhost:6399")
        println("RedisInsight available at: http://localhost:8002")
    }
}

// Task to run standalone script
tasks.register<JavaExec>("runStandalone") {
    group = "application"
    description = "Run standalone multimodal RAG demonstration"

    // Ensure Redis is running
    dependsOn(waitForRedis)

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.redis.vl.demo.rag.MultimodalRAGStandalone")

    doFirst {
        println("Starting Standalone Multimodal RAG Demo...")
        println("Redis Stack available at: localhost:6399")
    }
}
