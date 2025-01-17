/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.kover.gradle.aggregation.settings.dsl.intern

import kotlinx.kover.gradle.aggregation.settings.dsl.ReportsSettings
import kotlinx.kover.gradle.aggregation.settings.dsl.VerifySettings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

internal abstract class ReportsSettingsImpl: ReportsSettings {
    @get:Inject
    abstract val objects: ObjectFactory

    @Suppress("LeakingThis")
    override val verify: VerifySettings = objects.newInstance<VerifySettingsImpl>(this)
}