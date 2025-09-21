plugins {
    `java-platform`
    `maven-publish`
}

val releaseVersion: String? = (findProperty("releaseVersion") as String?)
    ?: System.getenv("RELEASE_VERSION")
    ?: System.getenv("VERSION")

allprojects {
    group = "net.thezerolabs.gradle"
    version = releaseVersion ?: (findProperty("version") as String? ?: "1.0.0-SNAPSHOT")
    
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    if (name.startsWith("bom")) {
        plugins.apply("java-platform")
        plugins.apply("maven-publish")
    }
}

