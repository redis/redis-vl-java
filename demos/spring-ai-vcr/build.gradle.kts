plugins {
    java
}

group = "com.redis.vl.demo"
version = "0.12.0"

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

dependencies {
    // RedisVL Core (includes VCR support)
    implementation(project(":core"))

    // SpotBugs annotations
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.3")

    // Spring AI 1.1.0
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.0"))
    implementation("org.springframework.ai:spring-ai-openai")

    // Redis
    implementation("redis.clients:jedis:5.2.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.15")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
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
    // Pass environment variables to tests
    environment("OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") ?: "")
}
