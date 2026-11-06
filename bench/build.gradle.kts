// Banc d'essai : rejoue des morceaux annotés par des experts dans le moteur et compare les analyses.
// Lancement : ./gradlew :bench:run --args="bench/data <dossier du rapport> [historique.tsv]"
plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":engine"))
}

application {
    mainClass.set("fr.declodeur.harmonie.bench.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}
