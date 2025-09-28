package net.thezerolabs.gradle.library

import com.google.cloud.tools.jib.gradle.JibExtension
import net.thezerolabs.gradle.library.internal.GitUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.springframework.boot.gradle.dsl.SpringBootExtension
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
            val springBootExt = project.extensions.getByType<SpringBootExtension>()
            springBootExt.mainClass.convention(zero.bootMainClass)
            project.afterEvaluate {
                val mainClass = zero.bootMainClass.orNull
                if (!mainClass.isNullOrBlank()) {
                    springBootExt.mainClass.set(mainClass)
                }
            }
        }
    }

    private fun configureJib(project: Project, zero: ZeroExtension) {
        project.pluginManager.withPlugin("com.google.cloud.tools.jib") {
            val jib = project.extensions.getByType<JibExtension>()
            val container = zero.container

            val env = { name: String -> project.providers.environmentVariable(name).orNull }
            val prop = { name: String -> project.providers.gradleProperty(name).orNull ?: project.findProperty(name)?.toString() }

            val (ghOwner, ghRepo) = resolveGithubOwnerRepo(project, zero)

            val useEcr = container.enableEcrPublishing.orNull == true ||
                !container.ecrRegistry.orNull.isNullOrBlank() ||
                !container.ecrRepository.orNull.isNullOrBlank()

            val imageNameValue = container.image.orNull?.takeIf { it.isNotBlank() } ?: if (useEcr) {
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

            val imageTagsValue = if (container.tags.isPresent && container.tags.orNull?.isNotEmpty() == true) {
                container.tags.get()
            } else {
                val version = project.version.toString()
                if (version.equals("unspecified", ignoreCase = true)) emptyList() else listOf(version)
            }

            jib.to {
                if (!imageNameValue.isNullOrBlank()) {
                    image = imageNameValue
                }
                if (imageTagsValue.isNotEmpty()) {
                    tags = LinkedHashSet(imageTagsValue.filter { it.isNotBlank() }.map { it.trim() })
                }

                auth {
                    if (useEcr) {
                        val authUsername = container.ecrUsername.orNull
                            ?: env("AWS_ECR_USERNAME")
                            ?: "AWS"
                        val authPassword = container.ecrPassword.orNull
                            ?: env("AWS_ECR_PASSWORD")
                            ?: env("AWS_ECR_TOKEN")

                        if (!authUsername.isNullOrBlank() && !authPassword.isNullOrBlank()) {
                            username = authUsername
                            password = authPassword
                        }
                    } else if (container.enableGithubPublishing.orNull != false) {
                        val authUser = zero.username.orNull ?: prop("gpr.user") ?: env("GPR_USER") ?: env("GITHUB_ACTOR")
                        val authToken = zero.token.orNull ?: prop("gpr.token") ?: env("GPR_TOKEN") ?: env("GITHUB_TOKEN")
                        if (!authUser.isNullOrBlank() && !authToken.isNullOrBlank()) {
                            username = authUser
                            password = authToken
                        }
                    }
                }

                val extraCredHelperValue = env("JIB_CRED_HELPER") ?: prop("jib.credHelper")
                if (!extraCredHelperValue.isNullOrBlank()) {
                    credHelper {
                        helper = extraCredHelperValue
                    }
                }
            }
        }
    }

    private fun resolveGithubOwnerRepo(project: Project, zero: ZeroExtension): Pair<String?, String?> {
        val env = { name: String -> project.providers.environmentVariable(name).orNull }
        val prop = { name: String -> project.providers.gradleProperty(name).orNull ?: project.findProperty(name)?.toString() }

        var owner = zero.githubOwner.orNull ?: env("GITHUB_OWNER") ?: env("GITHUB_REPOSITORY")?.substringBefore('/')
            ?: prop("gpr.owner")
        var repo = zero.githubRepo.orNull ?: env("GITHUB_REPOSITORY")?.substringAfter('/') ?: prop("gpr.repo")

        if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
            GitUtils.parseOwnerRepoFromGitConfig(project)?.let { (o, r) ->
                if (owner.isNullOrBlank()) owner = o
                if (repo.isNullOrBlank()) repo = r
            }
        }

        return owner?.takeIf { it.isNotBlank() } to repo?.takeIf { it.isNotBlank() }
    }

    private fun String.trim(char: Char): String = trimStart(char).trimEnd(char)
}