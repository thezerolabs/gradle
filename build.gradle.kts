plugins {
    `java-platform`
    `maven-publish`
}

allprojects {
    group = "net.thezerolabs.gradle"
    version = "1.0.0"
    
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    if (name.startsWith("bom")) {
        plugins.apply("java-platform")
        plugins.apply("maven-publish")
        plugins.apply("signing")
    }
}

