package net.thezerolabs.gradle.library

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.mavenCentral
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import javax.inject.Inject

open class ContainerExtension @Inject constructor(objects: ObjectFactory) {
    /** Fully qualified image name to publish. If blank, derived from GitHub or ECR settings. */
    val image: Property<String> = objects.property(String::class.java)
    /** Additional tags to publish. Defaults to the project version when available. */
    val tags: ListProperty<String> = objects.listProperty(String::class.java)
    /** Whether to automatically configure publishing to GitHub Container Registry (ghcr.io). */
    val enableGithubPublishing: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    /** Whether to configure publishing to Amazon ECR. */
    val enableEcrPublishing: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    /** Optional explicit ECR registry endpoint (e.g. 123456789012.dkr.ecr.us-east-1.amazonaws.com). */
    val ecrRegistry: Property<String> = objects.property(String::class.java)
    /** Optional ECR repository name. */
    val ecrRepository: Property<String> = objects.property(String::class.java)
    /** Optional username for ECR authentication (defaults to AWS when not provided). */
    val ecrUsername: Property<String> = objects.property(String::class.java)
    /** Optional password/token for ECR authentication. */
    val ecrPassword: Property<String> = objects.property(String::class.java)
}

open class ZeroExtension @Inject constructor(objects: ObjectFactory) {
    /** Java toolchain version to enforce. */
    val javaToolchainVersion: Property<Int> = objects.property(Int::class.java).convention(24)
    /** Group of the published BOM to use when the :bom project is not present. */
    val bomGroup: Property<String> = objects.property(String::class.java).convention("net.thezerolabs.gradle")
    /** Artifact of the published BOM to use when the :bom project is not present. */
    val bomArtifact: Property<String> = objects.property(String::class.java).convention("bom")
    /** Version of the published BOM to use when the :bom project is not present. Default is taken from properties or skipped. */
    val bomVersion: Property<String> = objects.property(String::class.java)
    /** Whether to use enforcedPlatform instead of platform. */
    val enforcedPlatform: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    /** Configurations to which the BOM platform should be added by default. */
    val addToConfigurations: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(listOf("implementation", "testImplementation"))
    /** GitHub owner (user or org). If not set, will try to read from GITHUB_REPOSITORY env (owner/repo). */
    val githubOwner: Property<String> = objects.property(String::class.java)
    /** GitHub repository name. If not set, will try to read from GITHUB_REPOSITORY env (owner/repo). */
    val githubRepo: Property<String> = objects.property(String::class.java)
    /** Full GitHub Packages Maven URL. If set, overrides owner/repo derived URL. */
    val githubUrl: Property<String> = objects.property(String::class.java)
    /** Username for GitHub Packages. Defaults from env GITHUB_ACTOR if not provided. */
    val username: Property<String> = objects.property(String::class.java)
    /** Token/Password for GitHub Packages. Defaults from env GITHUB_TOKEN if not provided. */
    val token: Property<String> = objects.property(String::class.java)
    /** Whether to auto-configure GitHub Packages publishing repository. */
    val enableGithubPublishing: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    /** Optional Spring Boot main class (used by the service plugin). */
    val bootMainClass: Property<String> = objects.property(String::class.java)
    /** Container/image configuration (used by the service plugin). */
    val container: ContainerExtension = objects.newInstance(ContainerExtension::class.java)
}

class LibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register single configuration extension 'zero'
        val zero = project.extensions.create("zero", ZeroExtension::class.java)

        // Automatically apply required plugins to consuming projects
        project.pluginManager.apply("java")
        project.pluginManager.apply("maven-publish")

        // Enforce Java toolchain version when Java plugin is present
        project.pluginManager.withPlugin("java") {
            val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
            javaExt.toolchain.languageVersion.set(zero.javaToolchainVersion.map(JavaLanguageVersion::of))
            // Also publish sources and javadoc jars by default
            runCatching {
                javaExt.withSourcesJar()
                javaExt.withJavadocJar()
            }.onFailure {
                project.logger.info("[library] Unable to enable sources/javadoc jars: ${it.message}")
            }
        }

        // If Kotlin JVM plugin is used, set its toolchain to match the Java toolchain
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val kotlinExt = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java)
            kotlinExt.jvmToolchain {
                languageVersion.set(zero.javaToolchainVersion.map(JavaLanguageVersion::of))
            }
        }

        // Add default repositories for dependency resolution (Maven Central, Spring, and TheZeroLabs)
        fun ensureDefaultRepositories(target: Project) {
            fun env(name: String): String? = target.providers.environmentVariable(name).orNull
            fun prop(name: String): String? = target.providers.gradleProperty(name).orNull
                ?: target.findProperty(name)?.toString()

            // 1) Maven Central
            val mavenCentralUrl = "https://repo.maven.apache.org/maven2"
            val hasMavenCentral = target.repositories.any {
                it is MavenArtifactRepository && it.url.toString().trimEnd('/') == mavenCentralUrl
            }
            if (!hasMavenCentral) {
                target.repositories.mavenCentral()
            }

            // 2) Spring Releases
            val springReleaseUrl = "https://repo.spring.io/release"
            val hasSpringRelease = target.repositories.any {
                it is MavenArtifactRepository && it.url.toString().trimEnd('/') == springReleaseUrl
            }
            if (!hasSpringRelease) {
                target.repositories.maven {
                    name = "SpringReleases"
                    url = target.uri(springReleaseUrl)
                }
            }

            // 3) TheZeroLabs GitHub Packages
            val repoUrl = zero.githubUrl.orNull ?: "https://maven.pkg.github.com/thezerolabs/gradle"
            val normalizedRepoUrl = repoUrl.trimEnd('/')
            val user = zero.username.orNull
                ?: prop("gpr.user")
                ?: env("GPR_USER")
                ?: env("GITHUB_ACTOR")
            val token = zero.token.orNull
                ?: prop("gpr.token")
                ?: env("GPR_TOKEN")
                ?: env("GITHUB_TOKEN")

            val alreadyAdded = target.repositories.any {
                it is MavenArtifactRepository && it.url.toString().trimEnd('/') == normalizedRepoUrl
            }
            if (!alreadyAdded) {
                target.repositories.maven {
                    name = "TheZeroLabs"
                    url = target.uri(repoUrl)
                    if (!user.isNullOrBlank() && !token.isNullOrBlank()) {
                        credentials(PasswordCredentials::class) {
                            username = user
                            password = token
                        }
                    } else {
                        target.logger.info("[library] Adding TheZeroLabs Maven repository without credentials (url=$repoUrl)")
                    }
                }
            }
        }

        // Apply to the current project
        ensureDefaultRepositories(project)

        // Add BOM as a default dependency when common JVM configurations are present
        val bomNotation: Any? = project.rootProject.findProject(":bom")?.let {
            val local = project.dependencies.project(mapOf("path" to ":bom"))
            if (zero.enforcedPlatform.get()) project.dependencies.enforcedPlatform(local) else project.dependencies.platform(local)
        } ?: run {
            val bomVersion = zero.bomVersion.orNull
                ?: project.providers.gradleProperty("thezerolabs.bomVersion").orNull
                ?: project.findProperty("thezerolabs.bomVersion")?.toString()
                ?: project.providers.environmentVariable("THEZEROLABS_BOM_VERSION").orNull

            if (bomVersion.isNullOrBlank()) {
                project.logger.lifecycle("[library] No :bom project found and no BOM version provided (thezerolabs.bomVersion). Skipping BOM injection.")
                null
            } else {
                val gav = "${zero.bomGroup.get()}:${zero.bomArtifact.get()}:$bomVersion"
                if (zero.enforcedPlatform.get()) project.dependencies.enforcedPlatform(gav) else project.dependencies.platform(gav)
            }
        }

        if (bomNotation != null) {
            val targetConfigs = zero.addToConfigurations.get()
            project.configurations.matching { it.name in targetConfigs }.all {
                project.dependencies.add(this.name, bomNotation)
            }
        }

        // Configure publishing (order-safe)
        project.pluginManager.withPlugin("maven-publish") {
            // Ensure a default Maven publication exists for Java projects
            project.extensions.configure<PublishingExtension> {
                publications {
                    if (findByName("mavenJava") == null) {
                        val javaComponent = project.components.findByName("java")
                        if (javaComponent != null) {
                            create("mavenJava", MavenPublication::class.java).from(javaComponent)
                        } else {
                            project.logger.info("[library] No 'java' component found; skipping default maven publication creation.")
                        }
                    }
                }
            }

            if (zero.enableGithubPublishing.get()) {
                // Resolve configuration from extension, properties, or environment
                fun env(name: String): String? = project.providers.environmentVariable(name).orNull
                fun prop(name: String): String? = project.providers.gradleProperty(name).orNull ?: project.findProperty(name)?.toString()
                fun parseOwnerRepoFromGitConfig(): Pair<String, String>? {
                    val gitConfig = project.rootProject.file(".git/config")
                    if (!gitConfig.isFile) return null
                    val lines = gitConfig.readLines()
                    var inOrigin = false
                    var url: String? = null
                    for (raw in lines) {
                        val line = raw.trim()
                        if (line.startsWith("[") && line.endsWith("]")) {
                            inOrigin = line.equals("[remote \"origin\"]", ignoreCase = true)
                            continue
                        }
                        if (inOrigin && (line.startsWith("url =") || line.startsWith("url = ") || line.startsWith("url="))) {
                            url = line.substringAfter("=").trim()
                            break
                        }
                    }
                    if (url.isNullOrBlank()) return null
                    val candidates = listOf(
                        Regex("""(?i)git@([^:]+):([^/]+)/([^/]+?)(?:\\.git)?$"""),
                        Regex("""(?i)(?:https?|ssh|git)://([^/]+)/([^/]+)/([^/]+?)(?:\\.git)?$""")
                    )
                    for (regex in candidates) {
                        val m = regex.find(url!!)
                        if (m != null) {
                            val host = m.groupValues[1]
                            val owner = m.groupValues[2]
                            val repo = m.groupValues[3]
                            // Only trust owner/repo if host looks like GitHub; otherwise caller can override via zero.githubUrl
                            if (host.contains("github", ignoreCase = true)) return owner to repo
                        }
                    }
                    return null
                }

                val urlFromExt = zero.githubUrl.orNull
                var owner = zero.githubOwner.orNull
                    ?: env("GITHUB_OWNER")
                    ?: env("GITHUB_REPOSITORY")?.substringBefore('/')
                    ?: prop("gpr.owner")
                var repo = zero.githubRepo.orNull
                    ?: env("GITHUB_REPOSITORY")?.substringAfter('/')
                    ?: prop("gpr.repo")
                if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
                    parseOwnerRepoFromGitConfig()?.let { (o, r) ->
                        if (owner.isNullOrBlank()) owner = o
                        if (repo.isNullOrBlank()) repo = r
                        project.logger.info("[library] Derived GitHub owner/repo from .git/config: $owner/$repo")
                    }
                }
                val url = urlFromExt ?: if (!owner.isNullOrBlank() && !repo.isNullOrBlank()) {
                    "https://maven.pkg.github.com/$owner/$repo"
                } else null

                val user = zero.username.orNull
                    ?: prop("gpr.user")
                    ?: env("GITHUB_ACTOR")
                val token = zero.token.orNull
                    ?: prop("gpr.token")
                    ?: env("GITHUB_TOKEN")

                if (!url.isNullOrBlank()) {
                    project.extensions.configure<PublishingExtension> {
                        repositories {
                            maven {
                                name = "GitHubPackages"
                                this.url = project.uri(url)
                                if (!user.isNullOrBlank() && !token.isNullOrBlank()) {
                                    credentials(PasswordCredentials::class) {
                                        username = user
                                        password = token
                                    }
                                } else {
                                    project.logger.info("[library] Configured GitHub Packages repository without credentials (url=$url). Set gpr.user/gpr.token or GITHUB_ACTOR/GITHUB_TOKEN to publish.")
                                }
                            }
                        }
                    }
                } else {
                    project.logger.info("[library] Skipping GitHub Packages repository: missing owner/repo. Set zero.githubUrl or zero.githubOwner/zero.githubRepo.")
                }
            }
        }
    }
}
