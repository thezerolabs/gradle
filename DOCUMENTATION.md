# Project Documentation

This document provides a comprehensive overview of TheZeroLabs Gradle Monorepo, including its plugins and configuration options.

## 1. Project Summary

TheZeroLabs Gradle Monorepo is a collection of Gradle modules designed to standardize and simplify the development of JVM-based projects. It consists of two primary components:

- **`bom/`**: A Bill of Materials (BOM) project that provides a centralized set of curated dependency versions. This ensures consistency and compatibility across all projects that use it.
- **`library/`**: A Gradle plugin module that offers two plugins:
    - `net.thezerolabs.gradle.library`: A foundational plugin for standardizing JVM projects.
    - `net.thezerolabs.gradle.service`: A specialized plugin for building and containerizing Spring Boot applications.

Together, these modules provide an opinionated yet flexible framework for setting up, building, and publishing JVM applications and libraries.

## 2. Detailed Plugin Documentation

The `library` module provides two Gradle plugins to streamline project setup. Both are configured via the `zero` extension in your `build.gradle.kts` or `build.gradle` file.

### 2.1. `net.thezerolabs.gradle.library`

The `library` plugin is the base plugin for all JVM projects. It automates common boilerplate configuration.

**Core Features:**

*   **Java Toolchain Configuration**: Automatically sets the Java toolchain to a modern version (default: 24). This can be configured via `zero.javaToolchainVersion`.
*   **Repository Setup**: Adds Maven Central, Spring Releases, and TheZeroLabs GitHub Packages repositories by default, so you don't have to configure them manually.
*   **BOM Integration**: Automatically imports TheZeroLabs BOM (`:bom` project or a published version). This manages dependency versions centrally. You can control this behavior with `zero.bomGroup`, `zero.bomArtifact`, and `zero.bomVersion`.
*   **Lombok Support**: If enabled (`zero.enableLombok`), it automatically adds Lombok to the annotation processor and compile-only configurations.
*   **Publishing Configuration**: Simplifies publishing to GitHub Packages by automatically creating a repository configuration in the `publishing` extension. This is controlled by `zero.enableGithubPublishing`.

**How to Apply:**

```kotlin
// build.gradle.kts
plugins {
    id("net.thezerolabs.gradle.library") version "<version>"
}
```

### 2.2. `net.thezerolabs.gradle.service`

The `service` plugin is designed for Spring Boot applications. It applies the `library` plugin and adds further integrations for building and containerizing services.

**Additional Features:**

*   **Spring Boot Integration**: Applies the official Spring Boot Gradle plugin and allows you to configure the main class via `zero.bootMainClass`.
*   **Containerization with Jib**: Applies the Jib plugin and provides a rich configuration block (`zero.container`) to build and publish container images to various registries.
    *   **GitHub Container Registry (ghcr.io)**: Publishing is enabled by default. The image name is derived from the GitHub repository owner and name.
    *   **Amazon ECR**: Supports publishing to ECR with dedicated configuration options (`zero.container.enableEcrPublishing`, `ecrRegistry`, etc.).

**How to Apply:**

```kotlin
// build.gradle.kts
plugins {
    id("net.thezerolabs.gradle.service") version "<version>"
}
```

## 3. Detailed Configuration Options

All plugins are configured through the `zero` extension in your build script.

```kotlin
// build.gradle.kts
zero {
    // ... configuration options ...
}
```

### 3.1. General Settings

| Property               | Type      | Default | Description                                                                                             |
| ---------------------- | --------- | ------- | ------------------------------------------------------------------------------------------------------- |
| `javaToolchainVersion` | `Int`     | `24`    | The Java toolchain version to apply to the project.                                                     |
| `enableLombok`         | `Boolean` | `true`  | If `true`, automatically adds `org.projectlombok:lombok` to the appropriate annotation processor configurations. |

### 3.2. BOM Configuration

These settings control how the plugin integrates the Bill of Materials (BOM).

| Property                | Type                | Default                                  | Description                                                                                                                              |
| ----------------------- | ------------------- | ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `bomGroup`              | `String`            | `net.thezerolabs.gradle`                 | The group ID for the published BOM. Used when the `:bom` project is not present.                                                           |
| `bomArtifact`           | `String`            | `bom`                                    | The artifact ID for the published BOM.                                                                                                   |
| `bomVersion`            | `String`            | *(none)*                                 | The version of the published BOM to use. If not set, BOM injection is skipped unless the `:bom` project is present.                      |
| `enforcedPlatform`      | `Boolean`           | `false`                                  | If `true`, uses `enforcedPlatform()` instead of `platform()` to import the BOM, forcing dependency versions.                             |
| `addToConfigurations`   | `List<String>`      | `["api", "implementation", ...]`         | A list of configurations to which the BOM platform will be added.                                                                        |

### 3.3. GitHub Packages Configuration

These settings configure dependency resolution and publishing to GitHub Packages.

| Property                 | Type      | Default                                       | Description                                                                                                                                                               |
| ------------------------ | --------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `enableGithubPublishing` | `Boolean` | `true`                                        | If `true`, automatically configures a Maven repository in the `publishing` extension for GitHub Packages.                                                                |
| `githubOwner`            | `String`  | *(derived)*                                   | The GitHub owner (user or organization). Derived from the `GITHUB_REPOSITORY` environment variable or `.git/config` if not set.                                             |
| `githubRepo`             | `String`  | *(derived)*                                   | The GitHub repository name. Derived from `GITHUB_REPOSITORY` or `.git/config`.                                                                                           |
| `githubUrl`              | `String`  | *(derived)*                                   | The full URL to the GitHub Packages repository. Overrides the URL derived from `githubOwner` and `githubRepo`.                                                            |
| `username`               | `String`  | `System.getenv("GITHUB_ACTOR")`               | The username for authenticating with GitHub Packages.                                                                                                                     |
| `token`                  | `String`  | `System.getenv("GITHUB_TOKEN")`               | The password or token for authenticating with GitHub Packages.                                                                                                            |

### 3.4. Service & Container Configuration

These settings are primarily used by the `service` plugin.

#### Spring Boot

| Property          | Type     | Default  | Description                                        |
| ----------------- | -------- | -------- | -------------------------------------------------- |
| `bootMainClass`   | `String` | *(none)* | The fully qualified name of the Spring Boot main class. |

#### Container (`zero.container`)

The `container` block configures Jib for building container images.

**General Container Settings**

| Property                 | Type           | Default                     | Description                                                                                                                                      |
| ------------------------ | -------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `image`                  | `String`       | *(derived)*                 | The full name of the container image to build. If not set, it's derived from ECR or GitHub settings (e.g., `ghcr.io/owner/repo`).                  |
| `tags`                   | `List<String>` | `[project.version]`         | A list of tags to apply to the published image.                                                                                                  |
| `enableGithubPublishing` | `Boolean`      | `true`                      | If `true`, configures Jib to publish to GitHub Container Registry (`ghcr.io`).                                                                   |

**ECR Settings**

These settings are used when `enableEcrPublishing` is `true`.

| Property                | Type      | Default                                | Description                                                                                             |
| ----------------------- | --------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `enableEcrPublishing`   | `Boolean` | `false`                                | If `true`, configures Jib to publish to Amazon ECR instead of GitHub Container Registry.                |
| `ecrRegistry`           | `String`  | *(none)*                               | The ECR registry endpoint (e.g., `123456789012.dkr.ecr.us-east-1.amazonaws.com`).                         |
| `ecrRepository`         | `String`  | *(none)*                               | The name of the repository within the ECR registry.                                                     |
| `ecrUsername`           | `String`  | `AWS`                                  | The username for ECR authentication.                                                                    |
| `ecrPassword`           | `String`  | `System.getenv("AWS_ECR_PASSWORD")`    | The password or token for ECR authentication.                                                           |