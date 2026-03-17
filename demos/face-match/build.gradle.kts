plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.redis.vl.demos"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics", "javafx.swing")
}

dependencies {
    // RedisVL4J core
    implementation(project(":core"))

    // ONNX Runtime for face recognition
    implementation("com.microsoft.onnxruntime:onnxruntime:1.16.3")

    // Face recognition - DJL (for now, ONNX later)
    implementation("ai.djl:api:0.29.0")
    implementation("ai.djl.pytorch:pytorch-engine:0.29.0")
    implementation("ai.djl.pytorch:pytorch-model-zoo:0.29.0")

    // Dimensionality reduction (t-SNE)
    implementation("com.github.haifengl:smile-core:3.1.1")

    // JavaFX 3D visualization
    implementation("org.fxyz3d:fxyz3d:0.6.0")

    // Utilities
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("com.github.spotbugs:spotbugs-annotations:4.8.2")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.redis.vl.demos.facematch.FaceMatchApplication")

    // Configure Redis connection and celebrity count from environment or defaults
    applicationDefaultJvmArgs = listOf(
        "-Dredis.host=${System.getenv("REDIS_HOST") ?: "localhost"}",
        "-Dredis.port=${System.getenv("REDIS_PORT") ?: "6380"}",
        "-Dceleb.count=${System.getenv("CELEB_COUNT") ?: "0"}"
    )
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
}

// Task to inspect ONNX model
tasks.register<JavaExec>("inspectModel") {
    group = "application"
    description = "Inspect ArcFace ONNX model input/output specifications"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.redis.vl.demos.facematch.util.ModelInspector")
}

// Task to test tensor creation
tasks.register<JavaExec>("testTensor") {
    group = "application"
    description = "Test ONNX tensor creation and inference"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.redis.vl.demos.facematch.util.TensorTest")
}

// Docker Compose tasks for Redis
// Find docker command (try common locations)
val dockerCmd = listOf("/usr/local/bin/docker", "/usr/bin/docker", "docker")
    .firstOrNull { file(it).exists() || it == "docker" } ?: "docker"

tasks.register<Exec>("redisUp") {
    group = "docker"
    description = "Start Redis 8.2 container with persistence for face-match demo"
    workingDir = projectDir
    commandLine(dockerCmd, "compose", "up", "-d", "--wait")

    doLast {
        println("Redis started on port 6380")
    }
}

tasks.register<Exec>("redisDown") {
    group = "docker"
    description = "Stop Redis container"
    workingDir = projectDir
    commandLine(dockerCmd, "compose", "down")
}

tasks.register<Exec>("redisLogs") {
    group = "docker"
    description = "View Redis container logs"
    workingDir = projectDir
    commandLine(dockerCmd, "compose", "logs", "-f", "redis")
}

tasks.register<Exec>("redisReset") {
    group = "docker"
    description = "Stop Redis and remove persisted data"
    workingDir = projectDir
    commandLine(dockerCmd, "compose", "down", "-v")

    doLast {
        println("Redis data volumes removed. Run 'redisUp' to start fresh.")
    }
}

// Make run task depend on redisUp
tasks.named("run") {
    dependsOn("redisUp")

    doFirst {
        println("Starting Face Match demo...")
        println("Redis connection: localhost:6380")
        println("Note: Embeddings will be cached in Redis for faster subsequent launches")
    }
}

// Task to generate embeddings from CSV
tasks.register<JavaExec>("generateEmbeddings") {
    group = "application"
    description = "Generate face embeddings from celebrity CSV and index in Redis"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.redis.vl.demos.facematch.service.FaceEmbeddingGenerator")
    dependsOn("redisUp")

    // Default argument
    val csvFile = project.findProperty("csvFile") as String?
        ?: "demos/face-match/src/main/resources/data/celeb_faces.csv"

    args = listOf(csvFile)

    // Environment variables - use port 6380 for demo Redis
    environment("REDIS_HOST", System.getenv("REDIS_HOST") ?: "localhost")
    environment("REDIS_PORT", System.getenv("REDIS_PORT") ?: "6380")

    doFirst {
        println("Generating embeddings and indexing in Redis on port 6380...")
    }
}
