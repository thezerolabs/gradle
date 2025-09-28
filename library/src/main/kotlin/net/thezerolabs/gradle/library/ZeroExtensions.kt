package net.thezerolabs.gradle.library

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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