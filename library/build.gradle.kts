plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.thezerolabs.gradle"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Add plugin dependencies to allow compiling against their APIs
    compileOnly(libs.gradlePlugins.kotlin)
    compileOnly(libs.gradlePlugins.springboot)
    compileOnly(libs.gradlePlugins.jib)
}

gradlePlugin {
    plugins {
        create("library") {
            id = "net.thezerolabs.gradle.library"
            displayName = "TheZeroLabs Library Convention Plugin"
            description = "Enforces Java 24+ toolchain and adds TheZeroLabs BOM as a default platform dependency."
            implementationClass = "net.thezerolabs.gradle.library.LibraryPlugin"
        }
        create("service") {
            id = "net.thezerolabs.gradle.service"
            displayName = "TheZeroLabs Service Convention Plugin"
            description = "Extends the library plugin with Spring Boot and container image publishing defaults."
            implementationClass = "net.thezerolabs.gradle.library.ServicePlugin"
        }
        create("convention") {
            id = "net.thezerolabs.gradle.convention"
            displayName = "TheZeroLabs Convention Plugin"
            description = "Internal convention plugin for Java, toolchains, and default repositories."
            implementationClass = "net.thezerolabs.gradle.library.ConventionPlugin"
        }
        create("github") {
            id = "net.thezerolabs.gradle.github"
            displayName = "TheZeroLabs GitHub Integration Plugin"
            description = "Internal convention plugin for GitHub Packages publishing and repository setup."
            implementationClass = "net.thezerolabs.gradle.library.GithubPlugin"
        }
        create("bom") {
            id = "net.thezerolabs.gradle.bom"
            displayName = "TheZeroLabs BOM Plugin"
            description = "Internal convention plugin for managing the TheZeroLabs BOM."
            implementationClass = "net.thezerolabs.gradle.library.BomPlugin"
        }
    }
}

publishing {
    // Publications are configured by the applied plugins (java-gradle-plugin and GithubPlugin)
}