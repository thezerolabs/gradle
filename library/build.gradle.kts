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
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    compileOnly("org.springframework.boot:spring-boot-gradle-plugin:3.3.0")
    compileOnly("com.google.cloud.tools:jib-gradle-plugin:3.4.2")
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
    }
}

publishing {
    publications {
        // java-gradle-plugin will create publications for plugin + marker automatically
    }
}

// Configure GitHub Packages repository (no init script)
val gprOwnerRepo = System.getenv("GITHUB_REPOSITORY")
val gprOwner = gprOwnerRepo?.substringBefore('/') ?: (findProperty("gpr.owner") as String?)
val gprRepo = gprOwnerRepo?.substringAfter('/') ?: (findProperty("gpr.repo") as String?)
val gprUrl = System.getenv("GPR_URL") ?: if (!gprOwner.isNullOrBlank() && !gprRepo.isNullOrBlank()) {
    "https://maven.pkg.github.com/$gprOwner/$gprRepo"
} else null
val gprUser = System.getenv("GPR_USER") ?: System.getenv("GITHUB_ACTOR")
val gprToken = System.getenv("GPR_TOKEN") ?: System.getenv("GITHUB_TOKEN")

if (!gprUrl.isNullOrBlank() && !gprUser.isNullOrBlank() && !gprToken.isNullOrBlank()) {
    publishing {
        repositories {
            maven {
                url = uri(gprUrl)
                credentials {
                    username = gprUser
                    password = gprToken
                }
            }
        }
    }
}
