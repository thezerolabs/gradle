package net.thezerolabs.gradle.library

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.kotlin.dsl.configure
import org.gradle.jvm.toolchain.JavaLanguageVersion
import javax.inject.Inject

open class ZeroExtension @Inject constructor(objects: ObjectFactory) {
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
}

class LibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register single configuration extension 'zero'
        val zero = project.extensions.create("zero", ZeroExtension::class.java)

        // Automatically apply required plugins to consuming projects
        project.pluginManager.apply("java")
        project.pluginManager.apply("maven-publish")

        // Enforce Java 24+ via toolchains when Java plugin is present
        project.pluginManager.withPlugin("java") {
            val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
            val current: JavaLanguageVersion? = javaExt.toolchain.languageVersion.orNull
            if (current == null || current.asInt() < 24) {
                javaExt.toolchain.languageVersion.set(JavaLanguageVersion.of(24))
            }
        }

        // If Kotlin JVM plugin is used, set its toolchain to 24 as well (reflection to avoid hard dependency)
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val kotlinExt = project.extensions.findByName("kotlin")
            runCatching {
                val m = kotlinExt?.javaClass?.methods?.firstOrNull { it.name == "jvmToolchain" && it.parameterTypes.size == 1 }
                if (m != null) {
                    // Prefer the overload taking an Int (Gradle Kotlin DSL: jvmToolchain(24))
                    m.invoke(kotlinExt, 24)
                }
            }.onFailure {
                project.logger.info("[library] Unable to set Kotlin JVM toolchain to 24: ${it.message}")
            }
        }

        // Add BOM as a default dependency when common JVM configurations are present
        fun addBom() {
            val bomNotation: Any = project.rootProject.findProject(":bom")?.let {
                val local = project.dependencies.project(mapOf("path" to ":bom"))
                if (zero.enforcedPlatform.get()) project.dependencies.enforcedPlatform(local) else project.dependencies.platform(local)
            } ?: run {
                val bomVersion = zero.bomVersion.orNull
                    ?: project.providers.gradleProperty("thezerolabs.bomVersion").orNull
                    ?: project.findProperty("thezerolabs.bomVersion")?.toString()
                    ?: project.providers.environmentVariable("THEZEROLABS_BOM_VERSION").orNull

                if (bomVersion.isNullOrBlank()) {
                    project.logger.lifecycle("[library] No :bom project found and no BOM version provided (thezerolabs.bomVersion). Skipping BOM injection.")
                    return
                }
                val gav = "${zero.bomGroup.get()}:${zero.bomArtifact.get()}:$bomVersion"
                if (zero.enforcedPlatform.get()) project.dependencies.enforcedPlatform(gav) else project.dependencies.platform(gav)
            }

            val configurations = zero.addToConfigurations.get()
            configurations.forEach { cfgName ->
                val cfg = project.configurations.findByName(cfgName)
                if (cfg != null) {
                    // Add as a normal dependency to the configuration
                    project.dependencies.add(cfgName, bomNotation)
                }
            }
        }

        // Wire BOM after Java or Kotlin plugin creates standard configurations
        project.pluginManager.withPlugin("java") { addBom() }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { addBom() }

        // As a fallback, try adding late in configuration if neither plugin triggers
        project.afterEvaluate { addBom() }

        // Configure publishing to GitHub Packages (order-safe)
        project.pluginManager.withPlugin("maven-publish") {
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

                if (!url.isNullOrBlank() && !user.isNullOrBlank() && !token.isNullOrBlank()) {
                    project.extensions.configure<PublishingExtension> {
                        repositories {
                            maven {
                                this.url = project.uri(url)
                                credentials(PasswordCredentials::class) {
                                    this.username = user
                                    this.password = token
                                }
                            }
                        }
                    }
                } else {
                    project.logger.info("[library] Skipping GitHub Packages publishing repository: missing url/user/token. url=$url user=${user?.let { "***" }} token=${token?.let { "***" }}")
                }
            }
        }
    }
}
