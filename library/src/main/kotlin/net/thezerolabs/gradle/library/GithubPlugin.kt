package net.thezerolabs.gradle.library

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven

class GithubPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val zero = project.extensions.getByType<ZeroExtension>()

        // Add TheZeroLabs GitHub Packages repository for dependency resolution
        project.rootProject.allprojects {
            val repoUrl = zero.githubUrl.orNull ?: "https://maven.pkg.github.com/thezerolabs/gradle"
            val user = zero.username.orNull ?: prop("gpr.user") ?: env("GITHUB_ACTOR")
            val token = zero.token.orNull ?: prop("gpr.token") ?: env("GITHUB_TOKEN")

            repositories.maven {
                name = "TheZeroLabs"
                url = uri(repoUrl)
                if (!user.isNullOrBlank() && !token.isNullOrBlank()) {
                    credentials(PasswordCredentials::class) {
                        username = user
                        password = token
                    }
                } else {
                    project.logger.info("[library] Adding TheZeroLabs Maven repository without credentials (url=$repoUrl)")
                }
            }
        }

        // Configure publishing to GitHub Packages
        if (zero.enableGithubPublishing.get()) {
            project.extensions.configure<PublishingExtension> {
                val (owner, repo) = resolveGithubOwnerRepo(project, zero)
                val url = zero.githubUrl.orNull ?: if (!owner.isNullOrBlank() && !repo.isNullOrBlank()) {
                    "https://maven.pkg.github.com/$owner/$repo"
                } else null

                if (!url.isNullOrBlank()) {
                    repositories {
                        maven {
                            name = "GitHubPackages"
                            this.url = project.uri(url)
                            val user = zero.username.orNull ?: project.prop("gpr.user") ?: project.env("GITHUB_ACTOR")
                            val token = zero.token.orNull ?: project.prop("gpr.token") ?: project.env("GITHUB_TOKEN")
                            if (!user.isNullOrBlank() && !token.isNullOrBlank()) {
                                credentials(PasswordCredentials::class) {
                                    username = user
                                    password = token
                                }
                            } else {
                                project.logger.info("[library] Configured GitHub Packages repository without credentials (url=$url).")
                            }
                        }
                    }
                } else {
                    project.logger.info("[library] Skipping GitHub Packages repository: missing owner/repo.")
                }
            }
        }
    }
}