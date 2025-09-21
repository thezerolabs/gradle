// GitHub Packages publishing init script (Kotlin DSL)
// Injects a Maven repository into all projects for 'maven-publish'
// Resolves owner/repo from env (GITHUB_REPOSITORY) or from .git/config remote "origin"

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.credentials.PasswordCredentials

fun parseOwnerRepoFromGitConfig(rootDir: java.io.File): Pair<String, String>? {
    val gitConfig = java.io.File(rootDir, ".git/config")
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
    val patterns = listOf(
        Regex("""(?i)git@([^:]+):([^/]+)/([^/]+?)(?:\\.git)?$"""),
        Regex("""(?i)(?:https?|ssh|git)://([^/]+)/([^/]+)/([^/]+?)(?:\\.git)?$""")
    )
    for (regex in patterns) {
        val m = regex.find(url!!)
        if (m != null) {
            val host = m.groupValues[1]
            val owner = m.groupValues[2]
            val repo = m.groupValues[3]
            if (host.contains("github", ignoreCase = true)) return owner to repo
        }
    }
    return null
}

gradle.settingsEvaluated {
    val root = gradle.rootProject

    val ownerFromEnv = System.getenv("GITHUB_OWNER")
    val ownerRepoFromEnv = System.getenv("GITHUB_REPOSITORY")
    val ownerFromProp = root.findProperty("gpr.owner")?.toString()
    val repoFromProp = root.findProperty("gpr.repo")?.toString()

    var owner = ownerFromEnv ?: ownerRepoFromEnv?.substringBefore('/') ?: ownerFromProp
    var repo = ownerRepoFromEnv?.substringAfter('/') ?: repoFromProp

    if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
        parseOwnerRepoFromGitConfig(root.rootDir)?.let { (o, r) ->
            if (owner.isNullOrBlank()) owner = o
            if (repo.isNullOrBlank()) repo = r
            println("[init] Derived GitHub owner/repo from .git/config: $owner/$repo")
        }
    }

    val repoUrl = System.getenv("GPR_URL") ?: if (!owner.isNullOrBlank() && !repo.isNullOrBlank()) {
        "https://maven.pkg.github.com/$owner/$repo"
    } else null

    val user = System.getenv("GPR_USER") ?: System.getenv("GITHUB_ACTOR")
    val token = System.getenv("GPR_TOKEN") ?: System.getenv("GITHUB_TOKEN")

    if (!repoUrl.isNullOrBlank() && !user.isNullOrBlank() && !token.isNullOrBlank()) {
        root.allprojects {
            plugins.withId("maven-publish") {
                val pub = extensions.getByType(PublishingExtension::class.java)
                pub.repositories { repos ->
                    repos.maven {
                        name = "GitHubPackages"
                        url = uri(repoUrl)
                        credentials(PasswordCredentials::class) {
                            username = user
                            password = token
                        }
                    }
                }
            }
        }
    } else {
        println("[init] Skipping GitHub Packages publishing repository: missing url/user/token")
    }
}
