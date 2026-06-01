// Root build script for the BookServerFull monorepo.
// Per-module configuration (Java toolchain, Spring Boot, etc.) lives in each
// subproject's build.gradle.kts; this root script only fixes the common
// coordinates used across the project.

allprojects {
    group = "com.example"
    version = "0.0.1-SNAPSHOT"
}
