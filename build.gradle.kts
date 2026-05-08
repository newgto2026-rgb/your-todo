import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.google.services) apply false
}

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            lint {
                abortOnError = true
                warningsAsErrors = true
                checkAllWarnings = true
                checkDependencies = true
                // Keep strict lint deterministic by excluding time-variant upgrade suggestions.
                disable += setOf(
                    "GradleDependency",
                    "AndroidGradlePluginVersion",
                    "NewerVersionAvailable"
                )
                explainIssues = true
                textReport = true
                xmlReport = true
                htmlReport = true
                sarifReport = true
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            lint {
                abortOnError = true
                warningsAsErrors = true
                checkAllWarnings = true
                checkDependencies = true
                // Keep strict lint deterministic by excluding time-variant upgrade suggestions.
                disable += setOf(
                    "GradleDependency",
                    "AndroidGradlePluginVersion",
                    "NewerVersionAvailable"
                )
                explainIssues = true
                textReport = true
                xmlReport = true
                htmlReport = true
                sarifReport = true
            }
        }
    }
}

tasks.register("coverageReportAll") {
    group = "verification"
    description = "Generate JaCoCo coverage reports for key modules."
    dependsOn(
        ":core:domain:jacocoTestReport",
        ":core:data:jacocoTestReport",
        ":core:database:jacocoTestReport",
        ":feature:todo:impl:jacocoTestReport"
    )
}

tasks.register("coverageVerifyAll") {
    group = "verification"
    description = "Verify JaCoCo coverage thresholds for key modules."
    dependsOn(
        ":core:domain:jacocoCoverageVerification",
        ":core:data:jacocoCoverageVerification",
        ":core:database:jacocoCoverageVerification",
        ":feature:todo:impl:jacocoCoverageVerification"
    )
}
