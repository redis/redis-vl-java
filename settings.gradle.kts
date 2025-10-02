rootProject.name = "redisvl"

// Core modules
include("core")
include("docs")

// Configure module locations
project(":core").projectDir = file("core")
project(":docs").projectDir = file("docs")

// Enable build cache for faster builds
buildCache {
    local {
        isEnabled = true
    }
}

// Enable configuration cache for faster builds
// Note: Configuration cache is stable in Gradle 8.x