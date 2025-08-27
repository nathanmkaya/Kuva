import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.kotlin.dsl.detektPlugins

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.publish) apply false
}

allprojects {
    tasks.withType<Detekt> {
        reports {
            xml.required.set(true)
            html.required.set(true)
            sarif.required.set(true)
            md.required.set(true)
            txt.required.set(true)
        }
    }

    tasks.withType<Detekt>().configureEach {
        // Keep generated stuff out of the analysis
        exclude("**/build/**", "**/generated/**", "**/build/generated/**", "**/.gradle/**")
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension>() {
    val ktfmtVersion = "0.57"
    kotlin {
        target("**/*.kt")
        ktfmt(ktfmtVersion).kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt(ktfmtVersion).kotlinlangStyle()
    }
}

detekt {
    toolVersion = "1.23.8"
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    basePath = projectDir.path
    autoCorrect = true
}

tasks.register("detektAll") {
    group = "verification"
    description = "Run Detekt across all modules and source sets"
    subprojects.forEach { sub -> dependsOn(sub.tasks.withType<Detekt>()) }
}

dependencies { detektPlugins(libs.detekt.compose) }
