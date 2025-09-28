package net.thezerolabs.gradle.library.internal

import org.gradle.api.Project

internal object GitUtils {
    fun parseOwnerRepoFromGitConfig(project: Project): Pair<String, String>? {
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
}