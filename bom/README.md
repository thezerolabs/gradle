# TheZeroLabs Gradle BOM

The `bom` module is a Gradle Java Platform (BOM) that centralizes dependency versions for TheZeroLabs projects. Consumers import this BOM to align versions across their modules without specifying each version individually.

## Coordinates

- Maven GAV: `net.thezerolabs.gradle:bom:<version>`
- Repository: GitHub Packages (this repository)

If you are building inside this monorepo, you can also reference the local project:
- Local project notation: `project(":bom")`

## Repository configuration (GitHub Packages)

You must add your GitHub Packages repository and credentials to resolve the BOM.

Kotlin DSL (settings.gradle.kts or build.gradle.kts):
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

Groovy DSL (settings.gradle or build.gradle):
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

## How to consume the BOM

- Kotlin DSL:
```kotlin
dependencies {
    // Align versions using the platform
    implementation(platform("net.thezerolabs.gradle:bom:<version>"))

    // Or enforce constraints strictly
    // implementation(enforcedPlatform("net.thezerolabs.gradle:bom:<version>"))

    // Your dependencies below (no versions needed when covered by the BOM)
    // implementation("group:artifact")
}
```

- Groovy DSL:
```groovy
dependencies {
    implementation platform('net.thezerolabs.gradle:bom:<version>')
    // implementation enforcedPlatform('net.thezerolabs.gradle:bom:<version>')

    // implementation 'group:artifact'
}
```

## Consuming from this monorepo

If you depend on this BOM from other modules in the same multi-project build, you can import the local platform:

Kotlin DSL:
```kotlin
dependencies {
    implementation(platform(project(":bom")))
    // or enforcedPlatform(project(":bom"))
}
```

## Choosing the BOM version

- Use the released version published to GitHub Packages, e.g. `1.0.0`.
- For snapshot builds, you may use the `-SNAPSHOT` version that matches this repository's `gradle.properties`.

Inside this repository, the library plugin `net.thezerolabs.gradle.library` can inject the BOM automatically; see `../library/README.md` for details.
