rootProject.name = "redisvl"

// Core modules
include("core")
include("docs")

// Demos
include("demos:rag-multimodal")

// Configure module locations
project(":core").projectDir = file("core")
project(":docs").projectDir = file("docs")
project(":demos:rag-multimodal").projectDir = file("demos/rag-multimodal")

// Enable build cache for faster builds
buildCache {
    local {
        isEnabled = true
    }
}

// Enable configuration cache for faster builds
// Note: Configuration cache is stable in Gradle 8.x
