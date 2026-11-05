package fr.declodeur.harmonie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.sp
import fr.declodeur.harmonie.AppController
import fr.declodeur.harmonie.BuildInfo
import fr.declodeur.harmonie.engine.AnalysisMode
import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.ChordCandidate
import fr.declodeur.harmonie.engine.ChordResult
import fr.declodeur.harmonie.engine.EngineSnapshot
import fr.declodeur.harmonie.engine.MusicStyle
import fr.declodeur.harmonie.engine.Notes
import fr.declodeur.harmonie.engine.ToneRole
import kotlin.math.roundToInt

@Composable
fun App(controller: AppController) {
    val snap by controller.snapshot.collectAsState()
    val settings by controller.settings.collectAsState()
    val devices by controller.devices.collectAsState()
    val connected by controller.connected.collectAsState()
    val error by controller.error.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = Palette.scheme) {
        Surface(Modifier.fillMaxSize(), color = Palette.background) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CurrentChordPanel(snap.current, Modifier.weight(1.5f).fillMaxHeight())
                    NotesPanel(snap, Modifier.weight(1f).fillMaxHeight())
                }
                HistoryPanel(snap.history, onClear = controller::clearHistory)
                KeyboardPanel(snap, onKeyClick = controller::toggleKey)
                StatusBar(connected, error)
            }
        }
        if (showSettings) {
            SettingsDialog(settings, controller::updateSettings, onClose = { showSettings = false })
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
private fun CurrentChordPanel(result: ChordResult?, modifier: Modifier) {
    Panel("Accord courant", modifier, help = Help.CURRENT_CHORD) {
        val best = result?.best
        if (result == null) {
            Text("Jouez un accord…", fontSize = 22.sp, color = Palette.muted)
        } else if (best == null) {
            run {
                Text(
                    result.intervalDescription?.replaceFirstChar { it.uppercase() } ?: "Accord non répertorié",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                InfoLine("Notes", result.pitchClasses.sorted().joinToString("  ") { Notes.name(it) })
                if (result.intervalDescription == null) {
                    Text(
                        "Ces notes ne correspondent à aucun accord du dictionnaire Yamaha.",
                        fontSize = 14.sp,
                        color = Palette.muted,
                    )
                }
            }
        } else {
            ChordDetails(best, result)
        }
    }
}

@Composable
private fun ChordDetails(best: ChordCandidate, result: ChordResult) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 76.sp, fontWeight = FontWeight.Bold)) { append(best.rootName) }
            withStyle(SpanStyle(fontSize = 46.sp, fontWeight = FontWeight.SemiBold)) { append(best.type.suffix) }
            best.slash?.let { withStyle(SpanStyle(fontSize = 54.sp, color = Palette.muted)) { append("/$it") } }
        },
        lineHeight = 84.sp,
    )
    Text(best.type.frenchName.replaceFirstChar { it.uppercase() }, fontSize = 16.sp, color = Palette.muted)
    Spacer(Modifier.height(12.dp))
    InfoLine("Renversement", best.inversion, Help.INVERSION)
    InfoLine(
        "Notes",
        result.pitchClasses.sortedBy { Notes.pc(it - best.root) }.joinToString("   ") { pc ->
            Notes.name(pc) + (best.degreeOf(pc)?.let { " ($it)" } ?: "")
        },
    )
    if (best.omittedFifth) {
        Text("Accord reconnu sans sa quinte (voicing courant).", fontSize = 14.sp, color = Palette.warning)
    }
    if (best.extraNotes.isNotEmpty()) {
        Text(
            "Note étrangère à l'accord : " + best.extraNotes.joinToString(", ") { Notes.name(it) },
            fontSize = 14.sp,
            color = Palette.extra,
        )
    }
    if (result.alternatives.isNotEmpty()) {
        InfoLine("Autres lectures", result.alternatives.joinToString("   ") { it.symbol }, Help.ALTERNATIVES)
    }
}

@Composable
private fun InfoLine(label: String, value: String, help: String? = null) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label : ", fontSize = 15.sp, color = Palette.muted)
        Text(value, fontSize = 15.sp)
        if (help != null) {
            Spacer(Modifier.width(6.dp))
            HelpBubble(help)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesPanel(snap: EngineSnapshot, modifier: Modifier) {
    val chord = snap.current?.best
    Panel("Notes jouées", modifier, help = Help.NOTES) {
        val analysed = snap.notes.filter { !it.melody }.sortedBy { it.note }
        val melody = snap.notes.filter { it.melody }.sortedBy { it.note }
        if (analysed.isEmpty() && melody.isEmpty()) {
            Text("Aucune note enfoncée.", fontSize = 15.sp, color = Palette.muted)
        }
        if (analysed.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                analysed.forEach { n ->
                    val role = if (chord != null && n.analysed) chord.roleOf(n.note) else ToneRole.NONE
                    NoteChip(
                        name = Notes.nameWithOctave(n.note),
                        degree = if (n.analysed) chord?.degreeOf(n.note) else null,
                        color = roleColor(role),
                        sustained = n.sustained,
                    )
                }
            }
        }
        if (melody.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Label("Mélodie (autres canaux)")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                melody.forEach { n ->
                    NoteChip(Notes.nameWithOctave(n.note), null, Palette.melody, n.sustained)
                }
            }
        }
    }
}

@Composable
private fun NoteChip(name: String, degree: String?, color: Color, sustained: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = if (sustained) 0.12f else 0.22f),
        border = BorderStroke(1.dp, color.copy(alpha = if (sustained) 0.5f else 1f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(degree ?: if (sustained) "pédale" else " ", fontSize = 12.sp, color = Palette.muted)
        }
    }
}

@Composable
private fun HistoryPanel(history: List<ChordResult>, onClear: () -> Unit) {
    Panel(
        "Historique",
        Modifier.fillMaxWidth(),
        help = Help.HISTORY,
        action = { TextButton(onClick = onClear) { Text("Effacer", fontSize = 13.sp) } },
    ) {
        if (history.isEmpty()) {
            Text("Les accords reconnus s'afficheront ici.", fontSize = 14.sp, color = Palette.muted)
        } else {
            val scroll = rememberScrollState()
            Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                history.forEachIndexed { i, r ->
                    val last = i == history.lastIndex
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Palette.surfaceHigh,
                        border = BorderStroke(if (last) 2.dp else 1.dp, if (last) Palette.accent else Palette.border),
                    ) {
                        Text(
                            r.best?.symbol ?: "?",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 18.sp,
                            fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            LaunchedScrollToEnd(scroll, history.size)
        }
    }
}

@Composable
private fun LaunchedScrollToEnd(scroll: androidx.compose.foundation.ScrollState, key: Int) {
    androidx.compose.runtime.LaunchedEffect(key) { scroll.animateScrollTo(scroll.maxValue) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyboardPanel(snap: EngineSnapshot, onKeyClick: (Int) -> Unit) {
    Panel("Clavier", Modifier.fillMaxWidth(), help = Help.KEYBOARD) {
        PianoKeyboard(
            notes = snap.notes,
            chord = snap.current?.best,
            onKeyClick = onKeyClick,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(Palette.root, "Fondamentale")
            LegendItem(Palette.chordTone, "Note de l'accord")
            LegendItem(Palette.tension, "Tension (9, 11, 13)")
            LegendItem(Palette.extra, "Note étrangère")
            LegendItem(Palette.pending, "Non analysée")
            LegendItem(Palette.melody, "Mélodie")
            LegendItem(Palette.chordTone.copy(alpha = 0.5f), "Tenue par la pédale (couleur pâle)")
        }
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

@Composable
private fun SettingsDialog(settings: AnalysisSettings, onChange: (AnalysisSettings) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Fermer") } },
        title = { Text("Réglages") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.width(440.dp)) {
                Column {
                    Label("Délai de stabilité : ${settings.stabilityMs} ms", Help.STABILITY)
                    Slider(
                        value = settings.stabilityMs.toFloat(),
                        onValueChange = { onChange(settings.copy(stabilityMs = ((it / 10f).roundToInt() * 10))) },
                        valueRange = 20f..200f,
                        steps = 17,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label("Inclure les notes tenues par la pédale", Help.SUSTAIN)
                    }
                    Switch(
                        checked = settings.includeSustain,
                        onCheckedChange = { onChange(settings.copy(includeSustain = it)) },
                    )
                }
            }
        },
    )
}
