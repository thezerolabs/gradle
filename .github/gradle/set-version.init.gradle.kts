// Init script to set the version for all projects after evaluation
// Determines the release version from (in order):
//  - Gradle property: -PreleaseVersion=<ver>
//  - Env: RELEASE_VERSION or VERSION
//  - GitHub Actions tag: GITHUB_REF_NAME (or last part of GITHUB_REF), strips leading 'v'

fun computeReleaseVersion(): String? {
    val prop = gradle.startParameter.projectProperties["releaseVersion"]
    val env = System.getenv("RELEASE_VERSION") ?: System.getenv("VERSION")
    val ghaTag = System.getenv("GITHUB_REF_NAME")
        ?: System.getenv("GITHUB_REF")?.substringAfterLast('/')
    val tagStripped = ghaTag?.removePrefix("v")
    return prop ?: env ?: tagStripped
}

val releaseVersion = computeReleaseVersion()

if (releaseVersion.isNullOrBlank()) {
    println("[init] No release version supplied; skipping version override")
} else {
    println("[init] Setting version to $releaseVersion for all projects")
    gradle.projectsEvaluated {
        allprojects {
            version = releaseVersion
        }
    }
}
