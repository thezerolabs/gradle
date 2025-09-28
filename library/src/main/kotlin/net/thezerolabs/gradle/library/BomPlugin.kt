package net.thezerolabs.gradle.library

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class BomPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val zero = project.extensions.getByType<ZeroExtension>()

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
                project.configurations.findByName(cfgName)?.let {
                    project.dependencies.add(cfgName, bomNotation)
                }
            }
        }

        // Defer BOM addition until configurations are available
        project.pluginManager.withPlugin("java") { addBom() }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { addBom() }
        project.afterEvaluate { addBom() }
    }
}