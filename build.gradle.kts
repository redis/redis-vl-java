plugins {
    java
    `java-library`
    `maven-publish`
    id("com.github.spotbugs") version "6.0.26" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
    id("io.freefair.lombok") version "8.11" apply false
    jacoco
}

allprojects {
    group = "com.redis"
    version = "0.0.1"

    repositories {
        mavenCentral()
        maven {
            url = uri("https://repo.spring.io/milestone")
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "jacoco")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "-parameters",
            "-Xlint:all",
            "-Xlint:-processing",
            "-Werror"
        ))
        options.release = 17
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }

    // Explicitly declare test runtime dependencies to avoid Gradle 9.0 deprecation
    configurations {
        testRuntimeOnly {
            extendsFrom(configurations.testImplementation.get())
        }
    }

    tasks.withType<Javadoc> {
        options {
            (this as StandardJavadocDocletOptions).apply {
                addBooleanOption("Xdoclint:all,-missing", true)
                addBooleanOption("html5", true)
            }
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat("1.25.2")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    // Configure SpotBugs
    tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
        excludeFilter.set(file("${rootProject.projectDir}/spotbugs-exclude.xml"))
    }

    // Disable SpotBugs for test code - test code has different requirements
    tasks.named("spotbugsTest") {
        enabled = false
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required = true
            html.required = true
        }
    }

    tasks.jacocoTestCoverageVerification {
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    dependencies {
        // Logging
        implementation("org.slf4j:slf4j-api:2.0.16")

        // Testing dependencies
        testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
        testImplementation("org.assertj:assertj-core:3.27.2")
        testImplementation("org.mockito:mockito-core:5.15.2")
        testImplementation("org.mockito:mockito-junit-jupiter:5.15.2")
        testImplementation("org.testcontainers:testcontainers:1.20.4")
        testImplementation("org.testcontainers:junit-jupiter:1.20.4")
        testImplementation("com.redis:testcontainers-redis:2.2.2")
        testImplementation("net.razorvine:pickle:1.4")

        // Explicitly declare test runtime dependencies
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testRuntimeOnly("ch.qos.logback:logback-classic:1.5.15")
    }
}

tasks.wrapper {
    gradleVersion = "8.11.1"
    distributionType = Wrapper.DistributionType.ALL
}

// Task to copy jar to notebooks directory for Jupyter
tasks.register<Copy>("copyJarToNotebooks") {
    dependsOn(":core:jar")
    from("core/build/libs/redisvl-0.0.1.jar")
    into("notebooks")
}

// Make build depend on copying jar
tasks.named("build") {
    dependsOn("copyJarToNotebooks")
}