package net.thezerolabs.gradle.library

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.getByType
import java.util.LinkedHashSet

class ServicePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("net.thezerolabs.gradle.library")
        project.pluginManager.apply("org.springframework.boot")
        project.pluginManager.apply("com.google.cloud.tools.jib")

        val zero = project.extensions.getByType<ZeroExtension>()

        configureSpringBoot(project, zero)
        configureJib(project, zero)
    }

    private fun configureSpringBoot(project: Project, zero: ZeroExtension) {
        project.pluginManager.withPlugin("org.springframework.boot") {
            val springBootExt = project.extensions.findByName("springBoot") ?: return@withPlugin
            val mainClassProperty = findMainClassProperty(springBootExt)

            mainClassProperty?.convention(zero.bootMainClass)
            project.afterEvaluate {
                val mainClass = zero.bootMainClass.orNull
                if (!mainClass.isNullOrBlank()) {
                    mainClassProperty?.set(mainClass)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun findMainClassProperty(extension: Any): Property<String>? {
        val getter = extension.javaClass.methods.firstOrNull { method ->
            method.name == "getMainClass" && method.parameterCount == 0
        } ?: return null

        val result = runCatching { getter.invoke(extension) }.getOrNull()
        return result as? Property<String>
    }

    private fun configureJib(project: Project, zero: ZeroExtension) {
        project.pluginManager.withPlugin("com.google.cloud.tools.jib") {
            val jib = project.extensions.findByName("jib") ?: return@withPlugin
            val container = zero.container

            val env = { name: String -> project.providers.environmentVariable(name).orNull }
            val prop = { name: String -> project.providers.gradleProperty(name).orNull ?: project.findProperty(name)?.toString() }

            val (ghOwner, ghRepo) = resolveGithubOwnerRepo(project, zero)

            val useEcr = container.enableEcrPublishing.orNull == true ||
                !container.ecrRegistry.orNull.isNullOrBlank() ||
                !container.ecrRepository.orNull.isNullOrBlank()

            val image = container.image.orNull?.takeIf { it.isNotBlank() } ?: if (useEcr) {
                val registry = container.ecrRegistry.orNull ?: env("AWS_ECR_REGISTRY")
                val repository = container.ecrRepository.orNull ?: env("AWS_ECR_REPOSITORY") ?: project.name
                if (!registry.isNullOrBlank()) {
                    listOf(registry.trimEnd('/'), repository.trim('/')).joinToString("/") { it.lowercase() }
                } else null
            } else if (container.enableGithubPublishing.orNull != false) {
                if (!ghOwner.isNullOrBlank() && !ghRepo.isNullOrBlank()) {
                    "ghcr.io/${ghOwner.lowercase()}/${ghRepo.lowercase()}"
                } else null
            } else null

            if (!image.isNullOrBlank()) {
                setStringProperty(jib, "setImage", image, "to")
            }

            val defaultTags = if (container.tags.isPresent && container.tags.orNull?.isNotEmpty() == true) {
                container.tags.get()
            } else {
                val version = project.version.toString()
                if (version.equals("unspecified", ignoreCase = true)) emptyList() else listOf(version)
            }
            if (defaultTags.isNotEmpty()) {
                val tags = LinkedHashSet(defaultTags.filter { it.isNotBlank() }.map { it.trim() })
                setCollectionProperty(jib, "setTags", tags, "to")
            }

            if (useEcr) {
                val username = container.ecrUsername.orNull
                    ?: env("AWS_ECR_USERNAME")
                    ?: "AWS"
                val password = container.ecrPassword.orNull
                    ?: env("AWS_ECR_PASSWORD")
                    ?: env("AWS_ECR_TOKEN")

                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    setAuth(jib, username, password, "to")
                }
            } else if (container.enableGithubPublishing.orNull != false) {
                val user = zero.username.orNull ?: prop("gpr.user") ?: env("GPR_USER") ?: env("GITHUB_ACTOR")
                val token = zero.token.orNull ?: prop("gpr.token") ?: env("GPR_TOKEN") ?: env("GITHUB_TOKEN")
                if (!user.isNullOrBlank() && !token.isNullOrBlank()) {
                    setAuth(jib, user, token, "to")
                }
            }

            val extraCredHelper = env("JIB_CRED_HELPER") ?: prop("jib.credHelper")
            if (!extraCredHelper.isNullOrBlank()) {
                setStringProperty(jib, "setCredHelper", extraCredHelper, "to")
            }
        }
    }

    private fun setStringProperty(target: Any, setterName: String, value: String, nested: String? = null) {
        val receiver = nested?.let { getNested(target, it) } ?: target
        val method = receiver?.javaClass?.methods?.firstOrNull { it.name == setterName && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        method?.invoke(receiver, value)
    }

    private fun setCollectionProperty(target: Any, setterName: String, value: Set<String>, nested: String? = null) {
        val receiver = nested?.let { getNested(target, it) } ?: target
        val method = receiver?.javaClass?.methods?.firstOrNull { it.name == setterName && it.parameterCount == 1 }
        method?.invoke(receiver, value)
    }

    private fun setAuth(target: Any, username: String, password: String, nested: String? = null) {
        val receiver = nested?.let { getNested(target, it) } ?: target
        val auth = receiver?.javaClass?.methods?.firstOrNull { it.name == "getAuth" && it.parameterCount == 0 }?.invoke(receiver)
        if (auth != null) {
            val userSetter = auth.javaClass.methods.firstOrNull { it.name == "setUsername" && it.parameterCount == 1 }
            val passSetter = auth.javaClass.methods.firstOrNull { it.name == "setPassword" && it.parameterCount == 1 }
            userSetter?.invoke(auth, username)
            passSetter?.invoke(auth, password)
        }
    }

    private fun getNested(target: Any, nested: String): Any? {
        val getter = target.javaClass.methods.firstOrNull { it.name.equals("get${nested.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}", ignoreCase = true) && it.parameterCount == 0 }
        return getter?.invoke(target)
    }

    private fun resolveGithubOwnerRepo(project: Project, zero: ZeroExtension): Pair<String?, String?> {
        val env = { name: String -> project.providers.environmentVariable(name).orNull }
        val prop = { name: String -> project.providers.gradleProperty(name).orNull ?: project.findProperty(name)?.toString() }

        var owner = zero.githubOwner.orNull ?: env("GITHUB_OWNER") ?: env("GITHUB_REPOSITORY")?.substringBefore('/')
            ?: prop("gpr.owner")
        var repo = zero.githubRepo.orNull ?: env("GITHUB_REPOSITORY")?.substringAfter('/') ?: prop("gpr.repo")

        if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
            val gitConfig = project.rootProject.file(".git/config")
            if (gitConfig.isFile) {
                val lines = gitConfig.readLines()
                var inOrigin = false
                var url: String? = null
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.startsWith("[") && line.endsWith("]")) {
                        inOrigin = line.equals("[remote \"origin\"]", ignoreCase = true)
                        continue
                    }
                    if (inOrigin && line.startsWith("url")) {
                        url = line.substringAfter('=').trim()
                        break
                    }
                }
                if (!url.isNullOrBlank()) {
                    val regexes = listOf(
                        Regex("""(?i)git@([^:]+):([^/]+)/([^/]+?)(?:\\.git)?$"""),
                        Regex("""(?i)(?:https?|ssh|git)://([^/]+)/([^/]+)/([^/]+?)(?:\\.git)?$""")
                    )
                    for (regex in regexes) {
                        val match = regex.find(url)
                        if (match != null && match.groupValues.size >= 4) {
                            val host = match.groupValues[1]
                            if (!host.contains("github", ignoreCase = true)) break
                            owner = owner ?: match.groupValues[2]
                            repo = repo ?: match.groupValues[3]
                            break
                        }
                    }
                }
            }
        }

        return owner?.takeIf { it.isNotBlank() } to repo?.takeIf { it.isNotBlank() }
    }

    private fun String.trim(char: Char): String = trimStart(char).trimEnd(char)
}
