plugins {
    `java-platform`
    `maven-publish`
}

description = "Aggregated BOM of Spring Boot, Spring Cloud, Security, Data, Reactor, Jackson, Netty, AWS SDK, Google Cloud, Micrometer, Azure SDK, Kotlin (as of 2025-09)"

javaPlatform {
    // Import other BOMs into this platform
    allowDependencies()
}

dependencies {
    // Spring ecosystem
    api(platform(libs.boms.spring.boot))
    api(platform(libs.boms.spring.cloud))
    api(platform(libs.boms.spring.security))
    api(platform(libs.boms.spring.data))
    api(platform(libs.boms.reactor))

    // Core libs
    api(platform(libs.boms.jackson))
    api(platform(libs.boms.netty))

    // Cloud SDKs
    api(platform(libs.boms.aws))
    api(platform(libs.boms.google.cloud))
    api(platform(libs.boms.azure))

    // Observability
    api(platform(libs.boms.micrometer))

    // Language
    api(platform(libs.boms.kotlin))
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"]) // publish the platform BOM
            artifactId = "bom"
            pom {
                name.set("TheZeroLabs Aggregated BOM")
                description.set(project.description)
                url.set("https://github.com/thezerolabs/gradle-plugins")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("thezerolabs")
                        name.set("TheZeroLabs")
                    }
                }
            }
        }
    }
}
