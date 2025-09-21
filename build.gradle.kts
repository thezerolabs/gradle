plugins {
    `java-platform`
    `maven-publish`
}

allprojects {
    group = "net.thezerolabs.gradle"
    version = "1.0.0-SNAPSHOT"
    
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

