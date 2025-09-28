package net.thezerolabs.gradle.library

import com.google.cloud.tools.jib.gradle.JibExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.util.LinkedHashSet

class ServicePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply base plugins and the new convention plugins
        project.pluginManager.apply(LibraryPlugin::class.java)
        project.pluginManager.apply(SpringBootPlugin::class.java)
        project.pluginManager.apply("com.google.cloud.tools.jib")

        val zero = project.extensions.getByType<ZeroExtension>()

        // Configure Spring Boot with type-safe access
        project.tasks.withType(BootJar::class.java).configureEach {
            mainClass.set(zero.bootMainClass)
        }

        // Configure Jib with type-safe access
        project.extensions.configure<JibExtension> {
            val container = zero.container

            val useEcr = container.enableEcrPublishing.get() ||
                container.ecrRegistry.isPresent ||
                container.ecrRepository.isPresent

            val image = container.image.orNull?.takeIf { it.isNotBlank() } ?: if (useEcr) {
                val registry = container.ecrRegistry.orNull ?: project.env("AWS_ECR_REGISTRY")
                val repository = container.ecrRepository.orNull ?: project.name
                if (registry != null) "$registry/$repository".lowercase() else null
            } else if (container.enableGithubPublishing.get()) {
                val (owner, repo) = resolveGithubOwnerRepo(project, zero)
                if (owner != null && repo != null) "ghcr.io/${owner.lowercase()}/${repo.lowercase()}" else null
            } else null

            if (image != null) {
                to.image = image
            }

            val defaultTags = if (container.tags.isPresent && container.tags.get().isNotEmpty()) {
                container.tags.get()
            } else {
                listOf(project.version.toString()).filter { it != "unspecified" }
            }
            if (defaultTags.isNotEmpty()) {
                to.tags = LinkedHashSet(defaultTags)
            }

            if (useEcr) {
                val username = container.ecrUsername.orNull ?: "AWS"
                val password = container.ecrPassword.orNull ?: project.env("AWS_ECR_TOKEN")
                if (password != null) {
                    to.auth.username = username
                    to.auth.password = password
                }
            } else if (container.enableGithubPublishing.get()) {
                val user = zero.username.orNull ?: project.prop("gpr.user") ?: project.env("GITHUB_ACTOR")
                val token = zero.token.orNull ?: project.prop("gpr.token") ?: project.env("GITHUB_TOKEN")
                if (user != null && token != null) {
                    to.auth.username = user
                    to.auth.password = token
                }
            }

            // This property is on the JibExtension itself, not nested under 'to'
            val credHelperValue = project.env("JIB_CRED_HELPER") ?: project.prop("jib.credHelper")
            if (credHelperValue != null) {
                // Fallback to reflection due to persistent Kotlin interop issues
                try {
                    val method = this::class.java.getMethod("setCredHelper", String::class.java)
                    method.invoke(this, credHelperValue)
                } catch (e: Exception) {
                    project.logger.warn("[service] Failed to set Jib credHelper via reflection: ${e.message}")
                }
            }
        }
    }
}