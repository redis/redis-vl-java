rootProject.name = "redisvl"

// Core modules
include("core")
include("docs")

// Demos
include("demos:rag-multimodal")
include("demos:langchain4j-vcr")
include("demos:spring-ai-vcr")

// Configure module locations
project(":core").projectDir = file("core")
project(":docs").projectDir = file("docs")
project(":demos:rag-multimodal").projectDir = file("demos/rag-multimodal")
project(":demos:langchain4j-vcr").projectDir = file("demos/langchain4j-vcr")
project(":demos:spring-ai-vcr").projectDir = file("demos/spring-ai-vcr")

// Enable build cache for faster builds
buildCache {
    local {
        isEnabled = true
    }
}

// Enable configuration cache for faster builds
// Note: Configuration cache is stable in Gradle 8.x
