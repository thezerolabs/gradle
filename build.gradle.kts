plugins {
    `java-platform`
    `maven-publish`
}

val propertyVersion: String? = (findProperty("version") as String?)
val releaseVersion: String? = (findProperty("releaseVersion") as String?)
    ?: System.getenv("RELEASE_VERSION")
    ?: System.getenv("VERSION")
val resolvedVersion: String = when {
    // Prefer an explicit non-SNAPSHOT release version when provided (e.g., from Release workflow)
    !releaseVersion.isNullOrBlank() && !releaseVersion.endsWith("-SNAPSHOT") -> releaseVersion
    // For SNAPSHOT builds, stick to gradle.properties version
    propertyVersion?.contains("SNAPSHOT") == true -> propertyVersion
    // Fallbacks
    !releaseVersion.isNullOrBlank() -> releaseVersion
    else -> propertyVersion ?: "1.0.0-SNAPSHOT"
}

allprojects {
    group = "net.thezerolabs.gradle"
    version = resolvedVersion
    
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

