package net.thezerolabs.gradle.library

import org.gradle.api.Project

internal fun Project.env(name: String): String? = providers.environmentVariable(name).orNull
internal fun Project.prop(name: String): String? = providers.gradleProperty(name).orNull

internal fun resolveGithubOwnerRepo(project: Project, zero: ZeroExtension): Pair<String?, String?> {
    var owner = zero.githubOwner.orNull ?: project.env("GITHUB_REPOSITORY")?.substringBefore('/')
    var repo = zero.githubRepo.orNull ?: project.env("GITHUB_REPOSITORY")?.substringAfter('/')

    if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
        val gitConfig = project.rootProject.file(".git/config")
        if (gitConfig.isFile) {
            val url = gitConfig.readLines().firstOrNull { it.trim().startsWith("url =") }
                ?.substringAfter("=")?.trim()

            if (url != null) {
                val regex = Regex("""(?:https?|git)://github\.com/([^/]+)/([^/]+?)(?:\.git)?$""")
                val match = regex.find(url)
                if (match != null) {
                    if (owner.isNullOrBlank()) owner = match.groupValues[1]
                    if (repo.isNullOrBlank()) repo = match.groupValues[2]
                }
            }
        }
    }
    return owner to repo
}