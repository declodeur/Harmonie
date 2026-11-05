package fr.declodeur.harmonie.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Couleurs de l'application (thème sombre, lisible à distance d'écran normale). */
object Palette {
    val background = Color(0xFF14161A)
    val surface = Color(0xFF1D2026)
    val surfaceHigh = Color(0xFF262A32)
    val border = Color(0xFF343944)
    val text = Color(0xFFECEEF2)
    val muted = Color(0xFF9AA1AD)
    val accent = Color(0xFF5B9BFF)

    // Rôles des notes, utilisés partout de la même façon (clavier, listes, légende)
    val root = Color(0xFF3D7EFF)
    val chordTone = Color(0xFF9CC0FF)
    val tension = Color(0xFFFFB547)
    val extra = Color(0xFFFF6B6B)
    val melody = Color(0xFF4FD1A1)
    val bass = Color(0xFFB98CFF)
    val pending = Color(0xFFB8BEC8)

    val success = Color(0xFF4FD1A1)
    val warning = Color(0xFFFFB547)
    val danger = Color(0xFFFF6B6B)

    val whiteKey = Color(0xFFF4F4F2)
    val blackKey = Color(0xFF22252B)
    val keyBorder = Color(0xFF5A606B)

    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF0B1A33),
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = muted,
        outline = border,
        error = danger,
    )
}
