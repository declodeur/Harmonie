package fr.declodeur.harmonie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.unit.sp
import fr.declodeur.harmonie.AppController
import fr.declodeur.harmonie.BuildInfo
import fr.declodeur.harmonie.engine.AnalysisMode
import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.ChordCandidate
import fr.declodeur.harmonie.engine.ChordResult
import fr.declodeur.harmonie.engine.EngineSnapshot
import fr.declodeur.harmonie.engine.HarmonicAnalysis
import fr.declodeur.harmonie.engine.HarmonicFunction
import fr.declodeur.harmonie.engine.HistoryEntry
import fr.declodeur.harmonie.engine.Key
import fr.declodeur.harmonie.engine.KeyEstimate
import fr.declodeur.harmonie.engine.Spelling
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
                val spelling = snap.key?.current?.spelling ?: Spelling.DEFAULT
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CurrentChordPanel(
                        snap.current,
                        snap.currentAnalysis,
                        spelling,
                        Modifier.weight(1.5f).fillMaxHeight(),
                    )
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KeyPanel(snap.key, Modifier.fillMaxWidth())
                        NotesPanel(snap, spelling, Modifier.fillMaxWidth().weight(1f))
                    }
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
private fun CurrentChordPanel(
    result: ChordResult?,
    analysis: HarmonicAnalysis?,
    spelling: Spelling,
    modifier: Modifier,
) {
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
                InfoLine("Notes", result.pitchClasses.sorted().joinToString("  ") { Notes.name(it, spelling) })
                if (result.intervalDescription == null) {
                    Text(
                        "Ces notes ne correspondent à aucun accord de mon dictionnaire.",
                        fontSize = 14.sp,
                        color = Palette.muted,
                    )
                }
            }
        } else {
            ChordDetails(best, result, analysis, spelling)
        }
    }
}

@Composable
private fun ChordDetails(best: ChordCandidate, result: ChordResult, analysis: HarmonicAnalysis?, spelling: Spelling) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 76.sp, fontWeight = FontWeight.Bold)) { append(best.rootNameIn(spelling)) }
                withStyle(SpanStyle(fontSize = 46.sp, fontWeight = FontWeight.SemiBold)) { append(best.type.suffix) }
                best.slashIn(spelling)?.let { withStyle(SpanStyle(fontSize = 54.sp, color = Palette.muted)) { append("/$it") } }
            },
            lineHeight = 84.sp,
        )
        if (analysis != null) {
            Spacer(Modifier.width(28.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(analysis.numeral, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Palette.accent)
                    Spacer(Modifier.width(8.dp))
                    HelpBubble(Help.NUMERAL)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    analysis.function?.let {
                        FunctionBadge(it)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(analysis.category.label, fontSize = 14.sp, color = Palette.muted)
                    Spacer(Modifier.width(6.dp))
                    HelpBubble(Help.FUNCTION)
                }
            }
        }
    }
    Text(best.type.frenchName.replaceFirstChar { it.uppercase() }, fontSize = 16.sp, color = Palette.muted)
    if (analysis != null) {
        Spacer(Modifier.height(6.dp))
        Text(analysis.description, fontSize = 15.sp, lineHeight = 21.sp)
    }
    Spacer(Modifier.height(12.dp))
    val inversion = if (best.bass != best.root && !best.bassDetached) "${best.inversionIn(spelling)}, serré (basse non notée)"
    else best.inversionIn(spelling)
    InfoLine("Renversement", inversion, Help.INVERSION)
    InfoLine(
        "Notes",
        result.pitchClasses.sortedBy { Notes.pc(it - best.root) }.joinToString("   ") { pc ->
            Notes.name(pc, spelling) + when {
                best.roleOf(pc) == ToneRole.BASS -> " (basse)"
                else -> best.degreeOf(pc)?.let { " ($it)" } ?: ""
            }
        },
    )
    if (best.omittedFifth) {
        Text("Accord reconnu sans sa quinte (voicing courant).", fontSize = 14.sp, color = Palette.warning)
    }
    if (best.extraNotes.isNotEmpty()) {
        Text(
            "Note étrangère à l'accord : " + best.extraNotes.joinToString(", ") { Notes.name(it, spelling) },
            fontSize = 14.sp,
            color = Palette.extra,
        )
    }
    if (result.alternatives.isNotEmpty()) {
        InfoLine("Autres lectures", result.alternatives.joinToString("   ") { it.symbolIn(spelling) }, Help.ALTERNATIVES)
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
private fun NotesPanel(snap: EngineSnapshot, spelling: Spelling, modifier: Modifier) {
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
                        name = Notes.nameWithOctave(n.note, spelling),
                        degree = when {
                            !n.analysed -> null
                            role == ToneRole.BASS -> "basse"
                            else -> chord?.degreeOf(n.note)
                        },
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
                    NoteChip(Notes.nameWithOctave(n.note, spelling), null, Palette.melody, n.sustained)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryPanel(history: List<HistoryEntry>, onClear: () -> Unit) {
    Panel(
        "Historique",
        Modifier.fillMaxWidth(),
        help = Help.HISTORY,
        action = { TextButton(onClick = onClear) { Text("Effacer", fontSize = 13.sp) } },
    ) {
        if (history.isEmpty()) {
            Text("Les accords reconnus s'afficheront ici, avec leur chiffrage.", fontSize = 14.sp, color = Palette.muted)
        } else {
            val scroll = rememberScrollState()
            // Accords regroupés par tonalité : un séparateur à chaque changement,
            // et sous chaque groupe un trait avec le nom de la tonalité.
            val groups = groupByKey(history)
            Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.Top) {
                groups.forEachIndexed { g, group ->
                    if (g > 0) {
                        Box(
                            Modifier.padding(horizontal = 10.dp).width(1.dp).height(78.dp)
                                .background(Palette.border),
                        )
                    }
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.entries.forEach { (i, entry) ->
                                HistoryChip(entry, last = i == history.lastIndex)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.fillMaxWidth().height(2.dp)
                                .background(Palette.accent.copy(alpha = 0.45f), RoundedCornerShape(1.dp)),
                        )
                        Text(
                            group.key?.name ?: "Tonalité indéterminée",
                            modifier = Modifier.padding(top = 3.dp),
                            fontSize = 12.sp,
                            color = Palette.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
            LaunchedScrollToEnd(scroll, history.size)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(Palette.tonic, "Tonique (T)")
                LegendItem(Palette.subdominant, "Sous-dominante (SD)")
                LegendItem(Palette.dominant, "Dominante (D)")
                LegendItem(Palette.muted, "Sans fonction (chromatique)")
            }
        }
    }
}

private data class KeyGroup(val key: Key?, val entries: List<IndexedValue<HistoryEntry>>)

/** Regroupe les accords consécutifs joués dans la même tonalité. */
private fun groupByKey(history: List<HistoryEntry>): List<KeyGroup> {
    val groups = mutableListOf<KeyGroup>()
    history.withIndex().forEach { iv ->
        val key = iv.value.analysis?.key
        val lastGroup = groups.lastOrNull()
        if (lastGroup != null && lastGroup.key == key) {
            groups[groups.lastIndex] = lastGroup.copy(entries = lastGroup.entries + iv)
        } else {
            groups += KeyGroup(key, listOf(iv))
        }
    }
    return groups
}

@Composable
private fun HistoryChip(entry: HistoryEntry, last: Boolean) {
    val sp = entry.analysis?.key?.spelling ?: Spelling.DEFAULT
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Palette.surfaceHigh,
        border = BorderStroke(if (last) 2.dp else 1.dp, if (last) Palette.accent else Palette.border),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                entry.chord.best?.symbolIn(sp) ?: "?",
                fontSize = 18.sp,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                entry.analysis?.numeral ?: "·",
                fontSize = 13.sp,
                color = entry.analysis?.function?.let { functionColor(it) } ?: Palette.muted,
            )
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
            LegendItem(Palette.bass, "Basse détachée hors accord")
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
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.width(500.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            ) {
                Column {
                    Label("Délai de stabilité : ${settings.stabilityMs} ms", Help.STABILITY)
                    Slider(
                        value = settings.stabilityMs.toFloat(),
                        onValueChange = { onChange(settings.copy(stabilityMs = ((it / 10f).roundToInt() * 10))) },
                        valueRange = 20f..200f,
                        steps = 17,
                    )
                }
                Column {
                    val gap = settings.slashMinGap
                    Label(
                        if (gap == 0) "Accord sur basse : basse toujours notée"
                        else "Accord sur basse : basse détachée d'au moins $gap demi-tons",
                        Help.SLASH_GAP,
                    )
                    Slider(
                        value = gap.toFloat(),
                        onValueChange = { onChange(settings.copy(slashMinGap = it.roundToInt())) },
                        valueRange = 0f..24f,
                        steps = 23,
                    )
                }
                SwitchLine("Inclure les notes tenues par la pédale", Help.SUSTAIN, settings.includeSustain) {
                    onChange(settings.copy(includeSustain = it))
                }
                SwitchLine(
                    "Basse fondamentale : garder la lecture complète (Am7 plutôt que C/A)",
                    Help.KEEP_ROOT_POSITION,
                    settings.bassOptions.keepRootPositionReading,
                ) { onChange(settings.copy(bassOptions = settings.bassOptions.copy(keepRootPositionReading = it))) }
                SwitchLine(
                    "Dessus incomplet : compter la basse (C/E plutôt que C1+5/E)",
                    Help.KEEP_FULL_WHEN_INCOMPLETE,
                    settings.bassOptions.keepFullReadingWhenUpperIncomplete,
                ) { onChange(settings.copy(bassOptions = settings.bassOptions.copy(keepFullReadingWhenUpperIncomplete = it))) }
                Column {
                    Label("Mémoire de la tonalité : ${settings.keyMemorySeconds} s", Help.KEY_MEMORY)
                    Slider(
                        value = settings.keyMemorySeconds.toFloat(),
                        onValueChange = { onChange(settings.copy(keyMemorySeconds = ((it / 2f).roundToInt() * 2))) },
                        valueRange = 4f..60f,
                        steps = 27,
                    )
                }
            }
        },
    )
}

@Composable
private fun SwitchLine(label: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Label(label, help) }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun functionColor(f: HarmonicFunction): Color = when (f) {
    HarmonicFunction.TONIC -> Palette.tonic
    HarmonicFunction.SUBDOMINANT -> Palette.subdominant
    HarmonicFunction.DOMINANT -> Palette.dominant
}

@Composable
private fun FunctionBadge(f: HarmonicFunction) {
    val c = functionColor(f)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = c.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, c),
    ) {
        Text(
            "${f.label} (${f.short})",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 14.sp,
            color = c,
        )
    }
}

@Composable
private fun KeyPanel(estimate: KeyEstimate?, modifier: Modifier) {
    Panel("Tonalité probable", modifier, help = Help.KEY) {
        val current = estimate?.current
        if (estimate == null || current == null) {
            Text("Jouez quelques accords…", fontSize = 18.sp, color = Palette.muted)
        } else {
            KeyDetails(estimate, current)
        }
    }
}

@Composable
private fun KeyDetails(estimate: KeyEstimate, current: Key) {
    run {
        Text(current.name, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Relative : ${current.relative.name}",
            fontSize = 13.sp,
            color = Palette.muted,
        )
        Spacer(Modifier.height(10.dp))
        estimate.ranking.take(3).forEach { kp ->
            val isCurrent = kp.key == current
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    kp.key.name,
                    modifier = Modifier.width(120.dp),
                    fontSize = 14.sp,
                    color = if (isCurrent) Palette.text else Palette.muted,
                )
                Box(Modifier.weight(1f).height(8.dp)) {
                    Surface(color = Palette.surfaceHigh, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().height(8.dp)) {}
                    Surface(
                        color = if (isCurrent) Palette.accent else Palette.muted,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth(kp.probability.toFloat().coerceIn(0.01f, 1f)).height(8.dp),
                    ) {}
                }
                Text(
                    "${(kp.probability * 100).roundToInt()} %",
                    modifier = Modifier.width(52.dp),
                    fontSize = 14.sp,
                    color = if (isCurrent) Palette.text else Palette.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}
