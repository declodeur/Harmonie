package fr.declodeur.harmonie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import fr.declodeur.harmonie.AppController
import fr.declodeur.harmonie.LayoutPrefs
import fr.declodeur.harmonie.BuildInfo
import fr.declodeur.harmonie.engine.AnalysisMode
import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.ChordCandidate
import fr.declodeur.harmonie.engine.ChordResult
import fr.declodeur.harmonie.engine.EngineSnapshot
import fr.declodeur.harmonie.engine.ChordScaleResult
import fr.declodeur.harmonie.engine.ChordScales
import fr.declodeur.harmonie.engine.MelodyRole
import fr.declodeur.harmonie.engine.HarmonicAnalysis
import fr.declodeur.harmonie.engine.HarmonicFunction
import fr.declodeur.harmonie.engine.HistoryEntry
import fr.declodeur.harmonie.engine.Key
import fr.declodeur.harmonie.engine.DegreeNotation
import fr.declodeur.harmonie.engine.KeyEstimate
import fr.declodeur.harmonie.engine.Spelling
import fr.declodeur.harmonie.engine.spelling
import fr.declodeur.harmonie.engine.MusicStyle
import fr.declodeur.harmonie.engine.Notes
import fr.declodeur.harmonie.engine.ToneRole
import fr.declodeur.harmonie.engine.KeyParams
import fr.declodeur.harmonie.engine.PredictionSource
import fr.declodeur.harmonie.engine.ProgressionModel
import fr.declodeur.harmonie.engine.currentKeyParams
import fr.declodeur.harmonie.engine.withKeyParams
import kotlin.math.roundToInt

@Composable
fun App(controller: AppController) {
    val snap by controller.snapshot.collectAsState()
    val settings by controller.settings.collectAsState()
    val devices by controller.devices.collectAsState()
    val connected by controller.connected.collectAsState()
    val error by controller.error.collectAsState()
    val layoutPrefs by controller.layout.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = Palette.scheme) {
        Surface(Modifier.fillMaxSize(), color = Palette.background) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
            ) {
                TopBar(
                    settings = settings,
                    devices = devices.map { it.name },
                    connected = connected,
                    onSettings = controller::updateSettings,
                    onDevice = { name -> devices.firstOrNull { it.name == name }?.let(controller::connect) },
                    onRefresh = { controller.refreshDevices() },
                    onOpenSettings = { showSettings = true },
                    onReleaseAll = controller::releaseAll,
                )
                Spacer(Modifier.height(12.dp))
                val spelling = snap.spelling
                MainArea(
                    snap = snap,
                    spelling = spelling,
                    style = settings.style,
                    showMiniKeyboards = settings.prediction.showMiniKeyboards,
                    predictionSource = if (
                        settings.prediction.source == PredictionSource.CORPUS &&
                        ProgressionModel.bundled?.styles?.contains(settings.style) == true
                    ) "statistiques des corpus" else "modèle théorique",
                    showScaleOnKeyboard = settings.scaleOnKeyboard,
                    savedLayout = layoutPrefs,
                    onLayoutChanged = controller::saveLayout,
                    onClearHistory = controller::clearHistory,
                    onKeyClick = controller::toggleKey,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Spacer(Modifier.height(10.dp))
                StatusBar(connected, error)
            }
        }
        if (showSettings) {
            SettingsDialog(
                settings,
                controller::updateSettings,
                onResetLayout = controller::resetLayout,
                onClose = { showSettings = false },
            )
        }
    }
}

@Composable
private fun TopBar(
    settings: AnalysisSettings,
    devices: List<String>,
    connected: String?,
    onSettings: (AnalysisSettings) -> Unit,
    onDevice: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onReleaseAll: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Selector(
            label = "Style",
            options = MusicStyle.entries,
            selected = settings.style,
            display = { it.label },
            onSelect = { onSettings(settings.copy(style = it)) },
            help = Help.STYLE,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Selector(
                label = "Entrée MIDI",
                options = devices,
                selected = connected,
                display = { it },
                onSelect = onDevice,
                help = Help.INPUT,
                placeholder = if (devices.isEmpty()) "Aucune entrée MIDI" else "Choisir…",
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onRefresh) { Text("↻ Actualiser", fontSize = 13.sp) }
        }
        Selector(
            label = "Analyse",
            options = AnalysisMode.entries,
            selected = settings.mode,
            display = { it.label },
            onSelect = { onSettings(settings.copy(mode = it)) },
            help = Help.MODE,
        )
        Selector(
            label = "Canal des accords",
            options = (0 until 16).toList(),
            selected = settings.chordChannel,
            display = { "Canal ${it + 1}" },
            onSelect = { onSettings(settings.copy(chordChannel = it)) },
            help = Help.CHANNEL,
            enabled = settings.mode == AnalysisMode.CHORDS_AND_MELODY,
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onReleaseAll) { Text("Relâcher tout", fontSize = 13.sp) }
        OutlinedButton(onClick = onOpenSettings) { Text("Réglages", fontSize = 13.sp) }
    }
}

@Composable
private fun KeyboardPanel(snap: EngineSnapshot, showScale: Boolean, onKeyClick: (Int) -> Unit, modifier: Modifier) {
    Panel(
        "Clavier",
        modifier,
        help = Help.KEYBOARD,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendItem(Palette.root, "Fondamentale")
                LegendItem(Palette.chordTone, "Note de l'accord")
                LegendItem(Palette.tension, "Tension")
                LegendItem(Palette.extra, "Note étrangère")
                LegendItem(Palette.bass, "Basse hors accord")
                LegendItem(Palette.pending, "Non analysée")
                LegendItem(Palette.melody, "Mélodie")
                LegendItem(Palette.chordTone.copy(alpha = 0.5f), "Pédale (pâle)")
                if (showScale) {
                    ScaleLegend("Gamme (gros : tonique)", tonic = true)
                    ScaleLegend("À éviter", avoid = true)
                }
            }
        },
    ) {
        PianoKeyboard(
            notes = snap.notes,
            chord = snap.current?.best,
            onKeyClick = onKeyClick,
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 40.dp),
            scale = if (showScale) snap.currentScale else null,
        )
    }
}

@Composable
private fun StatusBar(connected: String?, error: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(9.dp).padding(0.dp),
        ) {
            Surface(shape = CircleShape, color = if (connected != null) Palette.success else Palette.muted, modifier = Modifier.size(9.dp)) {}
        }
        Spacer(Modifier.width(8.dp))
        Text(
            error ?: (connected?.let { "Connecté à $it" } ?: "Aucune entrée MIDI connectée"),
            fontSize = 13.sp,
            color = if (error != null) Palette.danger else Palette.muted,
        )
        Spacer(Modifier.weight(1f))
        Text("Harmonie ${BuildInfo.VERSION} · build ${BuildInfo.BUILD}", fontSize = 12.sp, color = Palette.muted)
    }
}

/**
 * Zone principale, organisée selon le temps :
 * en haut le présent (accord courant), l'avenir (suites probables) et le contexte (tonalité) ;
 * au milieu le passé (historique) ; en bas le clavier.
 * Les frontières se déplacent à la souris (curseur ↔ ou ↕ au survol).
 */
@Composable
private fun MainArea(
    snap: EngineSnapshot,
    spelling: Spelling,
    style: MusicStyle,
    showMiniKeyboards: Boolean,
    predictionSource: String,
    showScaleOnKeyboard: Boolean,
    savedLayout: LayoutPrefs,
    onLayoutChanged: (LayoutPrefs) -> Unit,
    onClearHistory: () -> Unit,
    onKeyClick: (Int) -> Unit,
    modifier: Modifier,
) {
    var layout by remember(savedLayout) { mutableStateOf(savedLayout) }
    val save = { onLayoutChanged(layout) }
    BoxWithConstraints(modifier) {
        val total = maxHeight
        val width = maxWidth
        val usable = (width - 24.dp).value
        val chordR = layout.chordRatio.coerceIn(0.18f, 0.45f)
        val circleR = layout.circleRatio.coerceIn(0.15f, 0.40f)
        val treeR = 1f - chordR - circleR
        val keyboardH = layout.keyboardHeightDp.dp.coerceIn(70.dp, maxOf(70.dp, total * 0.4f))
        // Rangée du haut : réglable ; par défaut, elle prend tout sauf la hauteur naturelle de l'historique
        val historyNatural = 178.dp
        val topMax = maxOf(160.dp, total - keyboardH - 24.dp - 110.dp)
        val topAuto = total - keyboardH - 24.dp - historyNatural
        val topH = (if (layout.topHeightDp < 0) topAuto else layout.topHeightDp.dp).coerceIn(160.dp, topMax)

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(topH)) {
                CurrentChordPanel(snap.current, snap.currentAnalysis, snap, spelling, Modifier.weight(chordR).fillMaxHeight())
                VerticalSplitter(
                    onDrag = { dx ->
                        val d = dx.value / usable
                        val c = (chordR + d).coerceIn(0.18f, 0.45f)
                        if (1f - c - circleR >= 0.25f) layout = layout.copy(chordRatio = c)
                    },
                    onDragEnd = save,
                )
                PredictionPanel(snap.predictions, style, showMiniKeyboards, predictionSource, Modifier.weight(treeR).fillMaxHeight())
                VerticalSplitter(
                    onDrag = { dx ->
                        val d = dx.value / usable
                        val k = (circleR - d).coerceIn(0.15f, 0.40f)
                        if (1f - chordR - k >= 0.25f) layout = layout.copy(circleRatio = k)
                    },
                    onDragEnd = save,
                )
                KeyPanel(snap.key, Modifier.weight(circleR).fillMaxHeight())
            }
            HorizontalSplitter(
                onDrag = { dy -> layout = layout.copy(topHeightDp = (topH + dy).coerceIn(160.dp, topMax).value) },
                onDragEnd = save,
            )
            HistoryPanel(snap.history, onClear = onClearHistory, modifier = Modifier.fillMaxWidth().weight(1f))
            HorizontalSplitter(
                onDrag = { dy ->
                    layout = layout.copy(keyboardHeightDp = (keyboardH - dy).coerceIn(70.dp, maxOf(70.dp, total * 0.4f)).value)
                },
                onDragEnd = save,
            )
            KeyboardPanel(snap, showScaleOnKeyboard, onKeyClick = onKeyClick, modifier = Modifier.fillMaxWidth().height(keyboardH))
        }
    }
}

fun functionColor(f: HarmonicFunction): Color = when (f) {
    HarmonicFunction.TONIC -> Palette.tonic
    HarmonicFunction.SUBDOMINANT -> Palette.subdominant
    HarmonicFunction.DOMINANT -> Palette.dominant
}
