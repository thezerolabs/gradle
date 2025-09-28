package net.thezerolabs.gradle.library

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven

class ConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Automatically apply required plugins to consuming projects
        project.pluginManager.apply("java")
        project.pluginManager.apply("maven-publish")

        // Enforce Java 24+ via toolchains
        val javaExt = project.extensions.getByType<JavaPluginExtension>()
        javaExt.toolchain.languageVersion.set(JavaLanguageVersion.of(24))

        // Also publish sources and javadoc jars by default
        runCatching {
            javaExt.withSourcesJar()
            javaExt.withJavadocJar()
        }.onFailure {
            project.logger.info("[library] Unable to enable sources/javadoc jars: ${it.message}")
        }

        // Add default repositories for dependency resolution (Maven Central and Spring)
        project.repositories.mavenCentral()
        project.repositories.maven {
            name = "SpringReleases"
            url = project.uri("https://repo.spring.io/release")
        }
    }
}