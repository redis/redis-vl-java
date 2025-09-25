rootProject.name = "redisvl"

// Core modules
include("core")

// Configure module locations
project(":core").projectDir = file("core")

// Enable build cache for faster builds
buildCache {
    local {
        isEnabled = true
    }
}

// Enable configuration cache for faster builds
// Note: Configuration cache is stable in Gradle 8.x