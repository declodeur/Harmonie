// Moteur d'analyse : Kotlin pur, sans dépendance à Windows ni à Android,
// pour être réutilisé tel quel dans la future version mobile.
plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
