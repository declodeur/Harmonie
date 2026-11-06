import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val appVersion = "0.14.2"
val buildNumber: String = System.getenv("GITHUB_RUN_NUMBER") ?: "dev"

// Génère BuildInfo.kt (version et numéro de build affichés dans l'application)
val generateBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildinfo")
    val version = appVersion
    val build = buildNumber
    inputs.property("version", version)
    inputs.property("build", build)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("fr/declodeur/harmonie/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package fr.declodeur.harmonie
            |
            |object BuildInfo {
            |    const val VERSION = "$version"
            |    const val BUILD = "$build"
            |}
            |""".trimMargin()
        )
    }
}

kotlin.sourceSets.named("main") {
    this.kotlin.srcDir(generateBuildInfo)
}

dependencies {
    implementation(project(":engine"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
}

compose.desktop {
    application {
        mainClass = "fr.declodeur.harmonie.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Harmonie"
            packageVersion = "1.0.0"
            description = "Analyse harmonique en direct d'un instrument MIDI"
            vendor = "declodeur"
            modules("java.desktop", "java.prefs")
        }
    }
}
