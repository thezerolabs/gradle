# TheZeroLabs Library Gradle Plugin

The `library` module provides a Gradle plugin (`net.thezerolabs.gradle.library`) that standardizes JVM project setup and optionally wires in TheZeroLabs BOM and GitHub Packages publishing.

## Plugin ID

- `net.thezerolabs.gradle.library`

## What the plugin does

- Sets Java toolchain to Java 24 when not explicitly configured.
- Adds TheZeroLabs GitHub Packages Maven repository for dependency resolution by default.
- Optionally adds TheZeroLabs BOM (`:bom` project if present, or published BOM by GAV) to common configurations.
- Optionally configures a Maven repository pointing to GitHub Packages for publishing (no init scripts required).

## Applying the plugin

Kotlin DSL (project `build.gradle.kts`):
```kotlin
plugins {
    id("net.thezerolabs.gradle.library") version "<version>"
}
```

Groovy DSL (project `build.gradle`):
```groovy
plugins {
    id 'net.thezerolabs.gradle.library' version '<version>'
}
```

If you are consuming the plugin from GitHub Packages, add the repository to `settings.gradle(.kts)` or `build.gradle(.kts)`:

Kotlin DSL:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.github.com/thezerolabs/gradle")
            credentials {
                username = System.getenv("GPR_USER") ?: System.getenv("GITHUB_ACTOR")
                password = System.getenv("GPR_TOKEN") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Groovy DSL:
```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri('https://maven.pkg.github.com/thezerolabs/gradle')
            credentials {
                username = System.getenv('GPR_USER') ?: System.getenv('GITHUB_ACTOR')
                password = System.getenv('GPR_TOKEN') ?: System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

## Configuration (extension)

The plugin exposes an extension named `zero`:

Kotlin DSL:
```kotlin
zero {
    // When :bom project is not present, these control the published BOM coordinates
    bomGroup.set("net.thezerolabs.gradle")
    bomArtifact.set("bom")
    bomVersion.set("<version>") // If omitted and :bom not present, no BOM is injected

    // Whether to use enforcedPlatform instead of platform
    enforcedPlatform.set(false)

    // Configurations to add the BOM platform to (default: implementation, testImplementation)
    addToConfigurations.set(listOf("implementation", "testImplementation"))

    // GitHub packages publishing auto-config (optional)
    enableGithubPublishing.set(true)
    githubOwner.set("thezerolabs") // Optional; auto-derived from env or .git/config
    githubRepo.set("gradle")        // Optional; auto-derived from env or .git/config
    githubUrl.set("https://maven.pkg.github.com/thezerolabs/gradle") // Optional explicit URL for dependency resolution and publishing

    // Credentials (used for dependency resolution and publishing; if not set, falls back to env GITHUB_ACTOR / GITHUB_TOKEN)
    username.set("${System.getenv("GPR_USER")}")
    token.set("${System.getenv("GPR_TOKEN")}")
}
```

Groovy DSL:
```groovy
zero {
    bomGroup = providers.gradleProperty('zero.bomGroup').orNull ?: 'net.thezerolabs.gradle'
    bomArtifact = 'bom'
    bomVersion = '<version>'
    enforcedPlatform = false
    addToConfigurations = ['implementation', 'testImplementation']

    enableGithubPublishing = true
    githubOwner = 'thezerolabs'
    githubRepo = 'gradle'
    githubUrl = 'https://maven.pkg.github.com/thezerolabs/gradle' // Used for dependency resolution and publishing

    // Credentials (used for dependency resolution and publishing)
    username = System.getenv('GPR_USER')
    token = System.getenv('GPR_TOKEN')
}
```

## Using the BOM via the plugin

When `:bom` is present in the same build, the plugin adds a platform dependency to the configurations listed in `addToConfigurations`. When `:bom` isn’t present, you can set `bomVersion` so the plugin pulls the published BOM by GAV.

Example:
```kotlin
dependencies {
    // You do not need to specify versions for libraries that are covered by the BOM
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}
```

## Publishing to GitHub Packages (optional)

If `enableGithubPublishing` is true and a URL and credentials are available, the plugin configures a `maven` repository for publishing:
- URL resolution order: `zero.githubUrl` -> derived from `zero.githubOwner`/`zero.githubRepo` -> derived from env or .git/config
- Credentials: `zero.username`/`zero.token` -> env `GPR_USER`/`GPR_TOKEN` -> env `GITHUB_ACTOR`/`GITHUB_TOKEN`

Then you can publish with:
```bash
./gradlew publish
```

## FAQ

- Why Java 24?
  - The plugin defaults to Java 24 toolchains to leverage the latest JDK features; you can override the toolchain in your project’s `java` or `kotlin` extensions if needed.
- Overwriting snapshots on GitHub Packages?
  - GitHub Packages does not allow overwriting the same version. Bump your `-SNAPSHOT` version between publishes, or use the release workflow for immutable releases.
