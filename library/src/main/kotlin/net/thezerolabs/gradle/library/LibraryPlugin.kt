package net.thezerolabs.gradle.library

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class LibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register single configuration extension 'zero'
        project.extensions.create("zero", ZeroExtension::class.java)

        // Apply convention plugins
        project.pluginManager.apply(ConventionPlugin::class.java)
        project.pluginManager.apply(GithubPlugin::class.java)
        project.pluginManager.apply(BomPlugin::class.java)

        // Configure Kotlin JVM toolchain if the plugin is present
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(24)
            }
        }

        // Ensure a default Maven publication exists for Java projects
        project.pluginManager.withPlugin("maven-publish") {
            project.extensions.configure<PublishingExtension> {
                publications {
                    project.components.findByName("java")?.let {
                        create("mavenJava", MavenPublication::class.java).from(it)
                    }
                }
            }
        }
    }
}