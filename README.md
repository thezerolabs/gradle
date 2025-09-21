# TheZeroLabs Gradle Monorepo

This repository hosts two Gradle modules:

- [`bom/`](bom/README.md): A Java Platform (BOM) that centralizes dependency versions.
- [`library/`](library/README.md): A Gradle plugin (`net.thezerolabs.gradle.library`) that standardizes JVM project setup and optionally wires the BOM and GitHub Packages publishing.

## Quick Start

- To use the BOM in your project, see [`bom/README.md`](bom/README.md).
- To apply and configure the library plugin, see [`library/README.md`](library/README.md).

## Repositories (GitHub Packages)

To consume artifacts (BOM or plugin) from GitHub Packages, add this repository and credentials:

Kotlin DSL:
```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/thezerolabs/gradle")
        credentials {
            username = System.getenv("GPR_USER") ?: System.getenv("GITHUB_ACTOR")
            password = System.getenv("GPR_TOKEN") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Groovy DSL:
```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/thezerolabs/gradle')
        credentials {
            username = System.getenv('GPR_USER') ?: System.getenv('GITHUB_ACTOR')
            password = System.getenv('GPR_TOKEN') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}
```

## CI/CD Overview

- `publish.yml` (develop): Publishes snapshots using the version from root `gradle.properties`.
- `release.yml` (release): Publishes a non-SNAPSHOT version from the tag/input and bumps the repo to the next `-SNAPSHOT`.

For more details, check the READMEs in each module.
