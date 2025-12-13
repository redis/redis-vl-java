plugins {
    java
    `java-library`
    `maven-publish`
}

description = "RedisVL - Vector Library for Java"

dependencies {
    // Redis client
    api("redis.clients:jedis:7.0.0")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.1.0")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // Utilities
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.github.f4b6a3:ulid-creator:5.2.3")

    // Lombok for reducing boilerplate
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    testCompileOnly("org.projectlombok:lombok:1.18.36")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.36")

    // SpotBugs annotations for suppressing false positives
    // Use compileOnly since these are just markers for static analysis
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.3")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.8.3")

    // LangChain4J - Core API (required for vectorizers)
    compileOnly("dev.langchain4j:langchain4j:0.36.2")

    // LangChain4J Embedding Providers (optional - users include what they need)
    compileOnly("dev.langchain4j:langchain4j-open-ai:0.36.2")
    compileOnly("dev.langchain4j:langchain4j-azure-open-ai:0.36.2")
    compileOnly("dev.langchain4j:langchain4j-hugging-face:0.36.2")
    compileOnly("dev.langchain4j:langchain4j-ollama:0.36.2")
    compileOnly("dev.langchain4j:langchain4j-vertex-ai-gemini:0.36.2")

    // LangChain4J Local Embedding Models (optional)
    compileOnly("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:0.36.2")
    compileOnly("dev.langchain4j:langchain4j-embeddings-bge-small-en-v15:0.36.2")
    compileOnly("dev.langchain4j:langchain4j-embeddings-e5-small-v2:0.36.2")

    // ONNX Runtime for running models locally
    implementation("com.microsoft.onnxruntime:onnxruntime:1.16.3")

    // HTTP client for downloading from HuggingFace
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing for config.json
    implementation("com.google.code.gson:gson:2.10.1")

    // HuggingFace tokenizers for all transformer models (BERT, XLMRoberta, etc)
    implementation("ai.djl.huggingface:tokenizers:0.30.0")

    // Cohere Java SDK for reranking
    compileOnly("com.cohere:cohere-java:1.8.1")

    // VCR Test Utilities (optional - users include what they need for testing)
    // JUnit 5 for extension development
    compileOnly("org.junit.jupiter:junit-jupiter-api:5.10.2")
    // Testcontainers for Redis persistence
    compileOnly("org.testcontainers:testcontainers:1.19.7")
    compileOnly("org.testcontainers:junit-jupiter:1.19.7")
    // ByteBuddy for method interception (future LLM interceptor)
    compileOnly("net.bytebuddy:byte-buddy:1.14.12")
    compileOnly("net.bytebuddy:byte-buddy-agent:1.14.12")

    // Test dependencies for LangChain4J (include in tests to verify integration)
    testImplementation("dev.langchain4j:langchain4j:0.36.2")
    testImplementation("dev.langchain4j:langchain4j-open-ai:0.36.2")
    testImplementation("dev.langchain4j:langchain4j-cohere:0.36.2")
    testImplementation("dev.langchain4j:langchain4j-voyage-ai:0.36.2")
    testImplementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:0.36.2")
    testImplementation("dev.langchain4j:langchain4j-hugging-face:0.36.2")

    // Cohere for integration tests
    testImplementation("com.cohere:cohere-java:1.8.1")

    // Additional test dependencies
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.mockito:mockito-core:5.11.0")

    // VCR test dependencies (to test VCR functionality)
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
    testImplementation("net.bytebuddy:byte-buddy:1.14.12")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.12")
}

// Configure test execution
tasks.test {
    // Exclude slow and integration tests by default
    useJUnitPlatform {
        excludeTags("slow", "integration")
    }

    // Pass environment variables to tests
    environment("LANGCACHE_API_KEY", System.getenv("LANGCACHE_API_KEY") ?: "")
}

// Create a task for running integration tests
tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

// Create a task for running slow tests
tasks.register<Test>("slowTest") {
    useJUnitPlatform {
        includeTags("slow")
    }
    shouldRunAfter(tasks.test)
}

// Configure all JAR tasks to use the desired artifact name
tasks.jar {
    archiveBaseName.set("redisvl")
}

tasks.named<Jar>("sourcesJar") {
    archiveBaseName.set("redisvl")
}

tasks.named<Jar>("javadocJar") {
    archiveBaseName.set("redisvl")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            // Override the artifactId
            artifactId = "redisvl"

            pom {
                name.set("RedisVL")
                description.set("Vector Library for Java")
                url.set("https://github.com/redis/redisvl")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id = "bsbodden"
                        name = "Brian Sam-Bodden"
                        email = "bsb at redis.com"
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/redis/redis-vl-java.git")
                    developerConnection.set("scm:git:ssh://github.com:redis/redis-vl-java.git")
                    url.set("https://github.com/redis/redis-vl-java")
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}