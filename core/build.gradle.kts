plugins {
    java
    `java-library`
    `maven-publish`
}

description = "RedisVL4J - Vector Library for Java"

dependencies {
    // Redis client
    api("redis.clients:jedis:5.2.0")
    
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
    implementation("com.github.spotbugs:spotbugs-annotations:4.8.3")
}

// Configure all JAR tasks to use the desired artifact name
tasks.jar {
    archiveBaseName.set("redisvl4j")
}

tasks.named<Jar>("sourcesJar") {
    archiveBaseName.set("redisvl4j")
}

tasks.named<Jar>("javadocJar") {
    archiveBaseName.set("redisvl4j")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            // Override the artifactId
            artifactId = "redisvl4j"
            
            pom {
                name.set("RedisVL4J")
                description.set("Vector Library for Java")
                url.set("https://github.com/redis/redisvl4j")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("redis")
                        name.set("Redis Team")
                        email.set("oss@redis.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/redis/redisvl4j.git")
                    developerConnection.set("scm:git:ssh://github.com:redis/redisvl4j.git")
                    url.set("https://github.com/redis/redisvl4j")
                }
            }
        }
    }
}