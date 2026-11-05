package fr.declodeur.harmonie.ui

import androidx.compose.foundation.BorderStroke
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
                val spelling = snap.key?.current?.spelling ?: Spelling.DEFAULT
                MainArea(
                    snap = snap,
                    spelling = spelling,
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
    if (result.arpeggiated) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Accord arpégé : reconstitué à partir des dernières notes jouées (interprétation).",
                fontSize = 14.sp,
                color = Palette.warning,
            )
            Spacer(Modifier.width(6.dp))
            HelpBubble(Help.ARPEGGIO)
        }
    }
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
private fun KeyboardPanel(snap: EngineSnapshot, onKeyClick: (Int) -> Unit, modifier: Modifier) {
    Panel("Clavier", modifier, help = Help.KEYBOARD) {
        PianoKeyboard(
            notes = snap.notes,
            chord = snap.current?.best,
            onKeyClick = onKeyClick,
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 48.dp),
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

/**
 * Zone principale : accord courant | tonalité, historique, clavier | notes jouées.
 * Les frontières entre cadres se déplacent à la souris (curseur ↔ ou ↕ au survol).
 */
@Composable
private fun MainArea(
    snap: EngineSnapshot,
    spelling: Spelling,
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
        val gap = 12.dp
        val bottomH = layout.bottomHeightDp.dp.coerceIn(120.dp, maxOf(120.dp, total * 0.55f))
        val topMax = maxOf(160.dp, total - bottomH - gap * 2 - 110.dp)
        val topAuto = total - bottomH - gap * 2 - 170.dp
        val topH = (if (layout.topHeightDp < 0) topAuto else layout.topHeightDp.dp).coerceIn(160.dp, topMax)
        val ratio = layout.chordRatio.coerceIn(0.25f, 0.75f)
        val notesW = layout.notesWidthDp.dp.coerceIn(200.dp, maxOf(200.dp, width * 0.5f))

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(topH)) {
                CurrentChordPanel(snap.current, snap.currentAnalysis, spelling, Modifier.weight(ratio).fillMaxHeight())
                VerticalSplitter(
                    onDrag = { dx ->
                        val newRatio = (ratio + dx.value / (width - gap).value).coerceIn(0.25f, 0.75f)
                        layout = layout.copy(chordRatio = newRatio)
                    },
                    onDragEnd = save,
                )
                KeyPanel(snap.key, Modifier.weight(1f - ratio).fillMaxHeight())
            }
            HorizontalSplitter(
                onDrag = { dy -> layout = layout.copy(topHeightDp = (topH + dy).coerceIn(160.dp, topMax).value) },
                onDragEnd = save,
            )
            HistoryPanel(snap.history, onClear = onClearHistory, modifier = Modifier.fillMaxWidth().weight(1f))
            HorizontalSplitter(
                onDrag = { dy ->
                    layout = layout.copy(bottomHeightDp = (bottomH - dy).coerceIn(120.dp, maxOf(120.dp, total * 0.55f)).value)
                },
                onDragEnd = save,
            )
            Row(Modifier.fillMaxWidth().height(bottomH)) {
                KeyboardPanel(snap, onKeyClick = onKeyClick, modifier = Modifier.weight(1f).fillMaxHeight())
                VerticalSplitter(
                    onDrag = { dx ->
                        layout = layout.copy(notesWidthDp = (notesW - dx).coerceIn(200.dp, maxOf(200.dp, width * 0.5f)).value)
                    },
                    onDragEnd = save,
                )
                NotesPanel(snap, spelling, Modifier.width(notesW).fillMaxHeight())
            }
        }
    }
}

/** Réglages : deux colonnes thématiques, curseurs et interrupteurs compacts. */
@Composable
private fun SettingsDialog(
    settings: AnalysisSettings,
    onChange: (AnalysisSettings) -> Unit,
    onResetLayout: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Palette.surface,
            border = BorderStroke(1.dp, Palette.border),
            modifier = Modifier.width(900.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text("Réglages", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Reconnaissance des accords")
                        SliderSetting(
                            "Délai de stabilité : ${settings.stabilityMs} ms", Help.STABILITY,
                            settings.stabilityMs.toFloat(), 20f..200f, 10f,
                        ) { onChange(settings.copy(stabilityMs = it.roundToInt())) }
                        val gap = settings.slashMinGap
                        SliderSetting(
                            if (gap == 0) "Accord sur basse : basse toujours notée"
                            else "Accord sur basse : basse détachée d'au moins $gap demi-tons",
                            Help.SLASH_GAP, gap.toFloat(), 0f..24f, 1f,
                        ) { onChange(settings.copy(slashMinGap = it.roundToInt())) }
                        SwitchSetting("Inclure les notes tenues par la pédale", Help.SUSTAIN, settings.includeSustain) {
                            onChange(settings.copy(includeSustain = it))
                        }
                        SwitchSetting(
                            "Basse fondamentale : lecture complète (Am7 plutôt que C/A)",
                            Help.KEEP_ROOT_POSITION,
                            settings.bassOptions.keepRootPositionReading,
                        ) { onChange(settings.copy(bassOptions = settings.bassOptions.copy(keepRootPositionReading = it))) }
                        SwitchSetting(
                            "Dessus incomplet : compter la basse (C/E plutôt que C1+5/E)",
                            Help.KEEP_FULL_WHEN_INCOMPLETE,
                            settings.bassOptions.keepFullReadingWhenUpperIncomplete,
                        ) { onChange(settings.copy(bassOptions = settings.bassOptions.copy(keepFullReadingWhenUpperIncomplete = it))) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Accords arpégés")
                        SwitchSetting("Reconnaître les accords arpégés", Help.ARPEGGIO, settings.arpeggioDetection) {
                            onChange(settings.copy(arpeggioDetection = it))
                        }
                        SliderSetting(
                            "Remonter jusqu'à ${settings.arpeggioMaxNotes} notes en arrière", Help.ARPEGGIO_WINDOW,
                            settings.arpeggioMaxNotes.toFloat(), 3f..12f, 1f, enabled = settings.arpeggioDetection,
                        ) { onChange(settings.copy(arpeggioMaxNotes = it.roundToInt())) }
                        SliderSetting(
                            "… et au plus ${"%.2f".format(settings.arpeggioWindowMs / 1000.0).trimEnd('0').trimEnd(',', '.')} s en arrière",
                            Help.ARPEGGIO_WINDOW,
                            settings.arpeggioWindowMs.toFloat(), 500f..5000f, 250f, enabled = settings.arpeggioDetection,
                        ) { onChange(settings.copy(arpeggioWindowMs = it.roundToInt())) }
                        Spacer(Modifier.height(4.dp))
                        SectionTitle("Tonalité")
                        SwitchSetting(
                            "Changement immédiat sur un accord hors tonalité",
                            Help.FAST_KEY_CHANGE,
                            settings.fastKeyChange,
                        ) { onChange(settings.copy(fastKeyChange = it)) }
                        SliderSetting(
                            "Poids de la mélodie : ${settings.melodyWeightPercent} %", Help.MELODY_WEIGHT,
                            settings.melodyWeightPercent.toFloat(), 0f..100f, 10f,
                        ) { onChange(settings.copy(melodyWeightPercent = it.roundToInt())) }
                        SliderSetting(
                            "Mémoire de la tonalité : ${settings.keyMemorySeconds} s", Help.KEY_MEMORY,
                            settings.keyMemorySeconds.toFloat(), 4f..60f, 2f,
                        ) { onChange(settings.copy(keyMemorySeconds = it.roundToInt())) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onResetLayout) { Text("Réinitialiser la disposition de l'écran") }
                    HelpBubble(Help.LAYOUT)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onClose) { Text("Fermer") }
                }
            }
        }
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
            // Texte à gauche, cercle à droite sur toute la hauteur disponible
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(200.dp).fillMaxHeight()) {
                    Text(current.name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
                    Text(
                        "${(estimate.probabilityOf(current) * 100).roundToInt()} %",
                        fontSize = 18.sp,
                        color = Palette.muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Relative : ${current.relative.name}", fontSize = 13.sp, color = Palette.muted)
                    Text(
                        "${estimate.compatible.size} tonalité(s) compatible(s)",
                        fontSize = 13.sp,
                        color = Palette.muted,
                    )
                    Spacer(Modifier.weight(1f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LegendItem(Palette.accent, "Probabilité")
                        LegendOutline(Palette.compatible, "Compatible")
                        LegendOutline(Palette.text, "Retenue")
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CircleOfFifths(estimate, Modifier.fillMaxSize())
                }
            }
        }
    }
}
