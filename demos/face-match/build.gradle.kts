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
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    // RedisVL4J core
    implementation(project(":core"))

    // Face recognition - DJL (for now, ONNX later)
    implementation("ai.djl:api:0.29.0")
    implementation("ai.djl.pytorch:pytorch-engine:0.29.0")
    implementation("ai.djl.pytorch:pytorch-model-zoo:0.29.0")

    // Dimensionality reduction
    implementation("org.apache.commons:commons-math3:3.6.1")
    // TODO: Add t-SNE library once working version found

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
}

tasks.test {
    useJUnitPlatform()
}

// Task to generate embeddings from CSV
tasks.register<JavaExec>("generateEmbeddings") {
    group = "application"
    description = "Generate face embeddings from celebrity CSV and index in Redis"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.redis.vl.demos.facematch.service.FaceEmbeddingGenerator")

    // Default argument
    val csvFile = project.findProperty("csvFile") as String?
        ?: "demos/face-match/src/main/resources/data/celeb_faces.csv"

    args = listOf(csvFile)

    // Environment variables
    environment("REDIS_HOST", System.getenv("REDIS_HOST") ?: "localhost")
    environment("REDIS_PORT", System.getenv("REDIS_PORT") ?: "6379")
}
