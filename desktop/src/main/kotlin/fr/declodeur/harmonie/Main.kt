package fr.declodeur.harmonie

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.declodeur.harmonie.ui.App

private val ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

private fun iconBytes(size: Int): ByteArray? =
    Thread.currentThread().contextClassLoader?.getResourceAsStream("icons/harmonie-$size.png")?.use { it.readBytes() }

private fun iconImage(size: Int): java.awt.Image? = iconBytes(size)?.let { javax.imageio.ImageIO.read(it.inputStream()) }

private fun iconPainter(size: Int): Painter? =
    iconBytes(size)?.let { BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()) }

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
        icon = remember { iconPainter(256) },
    ) {
        // Plusieurs tailles : Windows choisit la version simplifiée pour la barre de titre et la barre des tâches
        LaunchedEffect(Unit) {
            window.iconImages = ICON_SIZES.mapNotNull { size -> iconImage(size) }
        }
        App(controller)
    }
}
