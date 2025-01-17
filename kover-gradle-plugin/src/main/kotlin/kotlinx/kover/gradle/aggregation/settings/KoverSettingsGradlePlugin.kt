/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.kover.gradle.aggregation.settings

import kotlinx.kover.gradle.aggregation.commons.artifacts.KoverContentAttr
import kotlinx.kover.gradle.aggregation.commons.artifacts.KoverUsageAttr
import kotlinx.kover.gradle.aggregation.commons.artifacts.asConsumer
import kotlinx.kover.gradle.aggregation.commons.artifacts.asDependency
import kotlinx.kover.gradle.aggregation.commons.names.KoverPaths
import kotlinx.kover.gradle.aggregation.settings.dsl.KoverNames
import kotlinx.kover.gradle.aggregation.settings.dsl.intern.KoverSettingsExtensionImpl
import kotlinx.kover.gradle.aggregation.commons.names.SettingsNames
import kotlinx.kover.gradle.aggregation.project.KoverProjectGradlePlugin
import kotlinx.kover.gradle.aggregation.settings.tasks.*
import kotlinx.kover.gradle.aggregation.settings.tasks.KoverHtmlReportTask
import kotlinx.kover.gradle.aggregation.settings.tasks.KoverVerifyTask
import kotlinx.kover.gradle.aggregation.settings.tasks.KoverXmlReportTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.language.base.plugins.LifecycleBasePlugin

public class KoverSettingsGradlePlugin: Plugin<Settings> {

    override fun apply(target: Settings) {
        val objects = target.serviceOf<ObjectFactory>()

        val settingsExtension = target.extensions.create<KoverSettingsExtensionImpl>(KoverNames.settingsExtensionName, objects)

        target.gradle.settingsEvaluated {
            KoverParametersProcessor.process(settingsExtension, providers)
        }

        target.gradle.beforeProject {
            if (!settingsExtension.coverageIsEnabled.get()) {
                return@beforeProject
            }

            val agentDependency = configurations.create(SettingsNames.DEPENDENCY_AGENT) {
                asDependency()
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(KoverUsageAttr.VALUE))
                }
            }
            dependencies.add(agentDependency.name, rootProject)

            if (path == Project.PATH_SEPARATOR) {
                configureRootProject(target, settingsExtension)
            }

            apply<KoverProjectGradlePlugin>()
        }
    }

    private fun Project.configureRootProject(settings: Settings, settingsExtension: KoverSettingsExtensionImpl) {
        val projectPath = path

        val dependencyConfig = configurations.create(KOVER_DEPENDENCY_NAME) {
            asDependency()
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(KoverUsageAttr.VALUE))
            }
        }
        val rootDependencies = dependencies
        settings.rootProject.walkSubprojects { descriptor ->
            rootDependencies.add(KOVER_DEPENDENCY_NAME, project(descriptor.path))
        }

        val artifacts = configurations.create("koverArtifactsCollector") {
            asConsumer()
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(KoverUsageAttr.VALUE))
                attribute(KoverContentAttr.ATTRIBUTE, KoverContentAttr.LOCAL_ARTIFACT)
            }
            extendsFrom(dependencyConfig)
        }

        val htmlTask = tasks.register<KoverHtmlReportTask>("koverHtmlReport")
        htmlTask.configure {
            dependsOn(artifacts)
            this.artifacts.from(artifacts)
            group = "verification"
            filters.convention(settingsExtension.reports.asInput())
            title.convention(projectPath)

            htmlDir.convention(layout.buildDirectory.dir(KoverPaths.htmlReportPath()))

            this.onlyIf {
                // `onlyIf` is used to ensure that the path is always printed, even when the task is not running and has the FROM-CACHE outcome
                printPath()
                true
            }
        }

        val xmlTask = tasks.register<KoverXmlReportTask>("koverXmlReport")
        xmlTask.configure {
            dependsOn(artifacts)
            this.artifacts.from(artifacts)
            group = "verification"
            filters.convention(settingsExtension.reports.asInput())
            title.convention(projectPath)

            reportFile.convention(layout.buildDirectory.file(KoverPaths.xmlReportPath()))
        }

        val verifyTask = tasks.register<KoverVerifyTask>("koverVerify")
        verifyTask.configure {
            dependsOn(artifacts)
            this.artifacts.from(artifacts)
            group = "verification"
            warningInsteadOfFailure.convention(settingsExtension.reports.verify.warningInsteadOfFailure)
            rules.convention(
                settingsExtension.reports.verify.rules.map { it.map { rule -> rule.asInput() } }
            )
        }
        // dependency on check
        tasks.configureEach {
            if (name == LifecycleBasePlugin.CHECK_TASK_NAME) {
                dependsOn(verifyTask)
            }
        }
    }

    private fun ProjectDescriptor.walkSubprojects(block: (ProjectDescriptor) -> Unit) {
        block(this)
        children.forEach { child ->
            child.walkSubprojects(block)
        }
    }

    private val KOVER_DEPENDENCY_NAME = "kover"
}

