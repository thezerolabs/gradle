plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.thezerolabs.gradle"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("library") {
            id = "net.thezerolabs.gradle.library"
            displayName = "TheZeroLabs Library Convention Plugin"
            description = "Enforces Java 24+ toolchain and adds TheZeroLabs BOM as a default platform dependency."
            implementationClass = "net.thezerolabs.gradle.library.LibraryPlugin"
        }
    }
}

publishing {
    publications {
        // java-gradle-plugin will create publications for plugin + marker automatically
    }
}
