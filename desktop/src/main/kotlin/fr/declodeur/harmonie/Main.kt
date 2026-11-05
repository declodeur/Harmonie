package fr.declodeur.harmonie

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.declodeur.harmonie.ui.App

fun main() = application {
    val scope = rememberCoroutineScope()
    val controller = remember { AppController(scope) }
    val windowState = rememberWindowState(width = 1320.dp, height = 860.dp)
    Window(
        onCloseRequest = {
            controller.dispose()
            exitApplication()
        },
        title = "Harmonie ${BuildInfo.VERSION}",
        state = windowState,
    ) {
        App(controller)
    }
}
