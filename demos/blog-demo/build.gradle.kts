plugins {
    java
    application
}

group = "com.redis.vl.demos"
version = "0.12.1"

repositories {
    mavenCentral()
}

dependencies {
    // Core RedisVL
    implementation(project(":core"))

    // LangChain4J
    implementation("dev.langchain4j:langchain4j:0.36.2")
    implementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:0.36.2")

    // Redis
    implementation("redis.clients:jedis:7.0.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.15")
}

application {
    mainClass.set("com.redis.vl.demo.blog.BlogDemo")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "Run the blog demo"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.redis.vl.demo.blog.BlogDemo")
}
