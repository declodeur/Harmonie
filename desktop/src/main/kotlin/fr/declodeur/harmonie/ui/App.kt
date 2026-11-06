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
                    style = settings.style,
                    showMiniKeyboards = settings.prediction.showMiniKeyboards,
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
private fun CurrentChordPanel(
    result: ChordResult?,
    analysis: HarmonicAnalysis?,
    snap: EngineSnapshot,
    spelling: Spelling,
    modifier: Modifier,
) {
    Panel("Accord courant", modifier, help = Help.CURRENT_CHORD) {
      // Défilement à la molette si le cadre est trop bas pour tout afficher
      Column(Modifier.verticalScroll(rememberScrollState())) {
        val best = result?.best
        if (result == null) {
            Text("Jouez un accord…", fontSize = 22.sp, color = Palette.muted)
        } else if (best == null) {
            run {
                Text(
                    result.intervalDescription?.replaceFirstChar { it.uppercase() } ?: "Accord non répertorié",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                NotesLine(result, null, snap, spelling)
                if (result.intervalDescription == null) {
                    Text(
                        "Ces notes ne correspondent à aucun accord de mon dictionnaire.",
                        fontSize = 14.sp,
                        color = Palette.muted,
                    )
                }
            }
        } else {
            ChordDetails(best, result, analysis, snap, spelling)
        }
      }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordDetails(
    best: ChordCandidate,
    result: ChordResult,
    analysis: HarmonicAnalysis?,
    snap: EngineSnapshot,
    spelling: Spelling,
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold)) { append(best.rootNameIn(spelling)) }
            withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold)) { append(best.type.suffix) }
            best.slashIn(spelling)?.let { withStyle(SpanStyle(fontSize = 46.sp, color = Palette.muted)) { append("/$it") } }
        },
        lineHeight = 70.sp,
        maxLines = 1,
    )
    if (analysis != null) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NumeralBadge(analysis.numeral)
            analysis.function?.let { FunctionBadge(it) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(30.dp)) {
                Text(analysis.category.label, fontSize = 14.sp, color = Palette.muted)
                Spacer(Modifier.width(6.dp))
                HelpBubble(Help.FUNCTION)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(analysis.description, fontSize = 14.sp, lineHeight = 19.sp)
    } else {
        Text(best.type.frenchName.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, color = Palette.muted)
    }
    best.interpretation?.let {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(it, fontSize = 13.sp, color = Palette.muted, lineHeight = 17.sp, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            HelpBubble(Help.INTERPRETATION)
        }
    }
    if (result.arpeggiated) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Accord arpégé (interprétation).", fontSize = 14.sp, color = Palette.warning)
            Spacer(Modifier.width(6.dp))
            HelpBubble(Help.ARPEGGIO)
        }
    }
    Spacer(Modifier.height(8.dp))
    val inversion = if (best.bass != best.root && !best.bassDetached) "${best.inversionIn(spelling)}, serré (basse non notée)"
    else best.inversionIn(spelling)
    InfoLine("Type", best.type.frenchName)
    InfoLine("Renversement", inversion, Help.INVERSION)
    NotesLine(result, best, snap, spelling)
    snap.currentScale?.let { ScaleLines(it, spelling) }
    if (best.omittedFifth) {
        Text("Accord reconnu sans sa quinte (voicing courant).", fontSize = 13.sp, color = Palette.warning)
    }
    if (best.extraNotes.isNotEmpty()) {
        Text(
            "Note étrangère à l'accord : " + best.extraNotes.joinToString(", ") { Notes.name(it, spelling) },
            fontSize = 13.sp,
            color = Palette.extra,
        )
    }
    if (result.alternatives.isNotEmpty()) {
        InfoLine("Autres lectures", result.alternatives.joinToString("   ") { it.symbolIn(spelling) }, Help.ALTERNATIVES)
    }
}

/**
 * Notes de l'accord avec octave et degré, colorées selon leur rôle ; notes tenues par la pédale
 * en couleur pâle ; mélodie (autres canaux) sur une ligne à part. Remplace l'ancien cadre
 * « Notes jouées ».
 */
@Composable
private fun NotesLine(result: ChordResult, best: ChordCandidate?, snap: EngineSnapshot, spelling: Spelling) {
    val sustained = snap.notes.filter { it.sustained }.map { it.note }.toSet()
    val text = buildAnnotatedString {
        result.notes.forEachIndexed { i, n ->
            if (i > 0) append("  ")
            val role = best?.roleOf(n) ?: ToneRole.NONE
            val color = roleColor(role).let { if (n in sustained) it.copy(alpha = 0.55f) else it }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) { append(Notes.nameWithOctave(n, spelling)) }
            val degree = when {
                best == null -> null
                role == ToneRole.BASS -> "basse"
                else -> best.degreeOf(n)
            }
            if (degree != null) withStyle(SpanStyle(color = Palette.muted, fontSize = 12.sp)) { append(" $degree") }
        }
        if (result.notes.any { it in sustained }) {
            withStyle(SpanStyle(color = Palette.muted, fontSize = 12.sp)) { append("   (pâle = pédale)") }
        }
    }
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Notes : ", fontSize = 15.sp, color = Palette.muted)
        Text(text, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        HelpBubble(Help.NOTES)
    }
    val melody = snap.notes.filter { it.melody || it.solo }.sortedBy { it.note }
    if (melody.isNotEmpty()) {
        val melodyText = buildAnnotatedString {
            melody.forEachIndexed { i, m ->
                if (i > 0) append("   ")
                val role = m.melodyRole
                withStyle(SpanStyle(color = melodyRoleColor(role), fontWeight = FontWeight.Medium)) {
                    append(Notes.nameWithOctave(m.note, spelling))
                }
                val label = when {
                    role == null || best == null -> null
                    role == MelodyRole.CHORD_TONE -> best.degreeOf(m.note)
                    role == MelodyRole.TENSION -> ChordScales.intervalLabel(Notes.pc(m.note), best)
                    role == MelodyRole.AVOID -> "à éviter"
                    else -> "chromatique"
                }
                if (label != null) withStyle(SpanStyle(color = Palette.muted, fontSize = 12.sp)) { append(" $label") }
            }
        }
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mélodie : ", fontSize = 15.sp, color = Palette.muted)
            Text(melodyText, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            HelpBubble(Help.MELODY_ROLES)
        }
    }
}

/** Couleur d'une note de mélodie selon son rôle sur l'accord (même code que le clavier). */
fun melodyRoleColor(role: MelodyRole?): Color = when (role) {
    MelodyRole.CHORD_TONE -> Palette.chordTone
    MelodyRole.TENSION -> Palette.tension
    MelodyRole.AVOID -> Palette.danger
    MelodyRole.CHROMATIC -> Palette.pending
    null -> Palette.melody
}

/**
 * Gamme de l'accord : nom et notes (fondamentale, notes de l'accord, tensions, notes à éviter
 * colorées comme sur le clavier), tensions disponibles, notes à éviter, variantes.
 * La bulle d'aide explique pourquoi cette gamme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleLines(scale: ChordScaleResult, spelling: Spelling) {
    val chord = scale.chord
    val primary = scale.primary
    val avoid = scale.avoid
    val tones = chord.chordPitchClasses
    val notes = buildAnnotatedString {
        primary.pitchClasses.forEachIndexed { i, pc ->
            if (i > 0) append(" ")
            val color = when {
                pc == chord.root -> Palette.root
                pc in tones -> Palette.chordTone
                pc in avoid -> Palette.danger
                else -> Palette.tension
            }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) { append(Notes.name(pc, spelling)) }
        }
    }
    FlowRow(
        Modifier.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Gamme : ", fontSize = 15.sp, color = Palette.muted, modifier = Modifier.align(Alignment.CenterVertically))
        Text(primary.name(spelling), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterVertically))
        Text(notes, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterVertically))
        Box(Modifier.align(Alignment.CenterVertically)) { HelpBubble(primary.reason + "\n\n" + Help.SCALE) }
    }
    val detail = buildAnnotatedString {
        if (avoid.isEmpty()) {
            withStyle(SpanStyle(color = Palette.muted)) { append("Aucune note à éviter") }
        } else {
            withStyle(SpanStyle(color = Palette.muted)) { append("À éviter : ") }
            withStyle(SpanStyle(color = Palette.danger, fontWeight = FontWeight.Medium)) {
                append(primary.pitchClasses.filter { it in avoid }.joinToString(" ") { Notes.name(it, spelling) })
            }
        }
        val tensions = scale.tensions
        if (tensions.isNotEmpty()) {
            withStyle(SpanStyle(color = Palette.muted)) { append("   ·   Tensions : ") }
            withStyle(SpanStyle(color = Palette.tension)) { append(tensions.joinToString(" ")) }
        }
    }
    Text(detail, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
    if (scale.alternatives.isNotEmpty()) {
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Aussi : " + scale.alternatives.joinToString(", ") { it.name(spelling) },
                fontSize = 13.sp,
                color = Palette.muted,
            )
            Spacer(Modifier.width(6.dp))
            HelpBubble(scale.alternatives.joinToString("\n") { "• " + it.name(spelling) + " : " + it.reason })
        }
    }
}

/** Chiffrage dans un badge encadré (un « I » seul ne doit pas ressembler à un trait). */
@Composable
private fun NumeralBadge(numeral: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.height(30.dp).border(1.5.dp, Palette.accent, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(numeral, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.accent)
        }
        Spacer(Modifier.width(4.dp))
        HelpBubble(Help.NUMERAL)
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
        val historyNatural = 172.dp
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
                PredictionPanel(snap.predictions, style, spelling, showMiniKeyboards, Modifier.weight(treeR).fillMaxHeight())
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
            modifier = Modifier.width(1240.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text("Réglages", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
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
                        Spacer(Modifier.height(4.dp))
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
                        val win = settings.interpretation.harmonicWindowMs
                        SliderSetting(
                            if (win == 0) "Fenêtre harmonique : désactivée" else "Fenêtre harmonique : $win ms",
                            Help.HARMONIC_WINDOW, win.toFloat(), 0f..1500f, 100f, enabled = settings.arpeggioDetection,
                        ) { onChange(settings.copy(interpretation = settings.interpretation.copy(harmonicWindowMs = it.roundToInt()))) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Interprétation des accords")
                        val ip = settings.interpretation
                        SwitchSetting("Classique : sixte ajoutée lue comme septième (Am7/C)", Help.SIXTH_AS_SEVENTH, ip.sixthAsSeventh) {
                            onChange(settings.copy(interpretation = ip.copy(sixthAsSeventh = it)))
                        }
                        SwitchSetting("Classique : retards (Gsus4 lu G, retard 4-3)", Help.SUSPENSIONS, ip.suspensions) {
                            onChange(settings.copy(interpretation = ip.copy(suspensions = it)))
                        }
                        SwitchSetting("Septième diminuée nommée d'après la sensible", Help.DIMINISHED_BY_KEY, ip.diminishedByKey) {
                            onChange(settings.copy(interpretation = ip.copy(diminishedByKey = it)))
                        }
                        SwitchSetting("Une ou deux notes de l'accord ne le remplacent pas", Help.KEEP_ON_SUBSET, ip.keepChordOnSubset) {
                            onChange(settings.copy(interpretation = ip.copy(keepChordOnSubset = it)))
                        }
                        SwitchSetting("Tierce seule lue comme accord (A–C♯ = A)", Help.COMPLETE_DYADS, ip.completeDyads) {
                            onChange(settings.copy(interpretation = ip.copy(completeDyads = it)))
                        }
                        Spacer(Modifier.height(4.dp))
                        SectionTitle("Gammes")
                        SwitchSetting("Afficher la gamme de l'accord sur le clavier", Help.SCALE_KEYBOARD, settings.scaleOnKeyboard) {
                            onChange(settings.copy(scaleOnKeyboard = it))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Tonalité")
                        SwitchSetting(
                            "Changement immédiat sur un accord hors tonalité",
                            Help.FAST_KEY_CHANGE,
                            settings.fastKeyChange,
                        ) { onChange(settings.copy(fastKeyChange = it)) }
                        SwitchSetting("Changement immédiat sur une cadence parfaite", Help.CADENCE_KEY_CHANGE, settings.cadenceKeyChange) {
                            onChange(settings.copy(cadenceKeyChange = it))
                        }
                        SliderSetting(
                            "Poids de la mélodie : ${settings.melodyWeightPercent} %", Help.MELODY_WEIGHT,
                            settings.melodyWeightPercent.toFloat(), 0f..100f, 10f,
                        ) { onChange(settings.copy(melodyWeightPercent = it.roundToInt())) }
                        SliderSetting(
                            "Mémoire de la tonalité : ${settings.keyMemorySeconds} s", Help.KEY_MEMORY,
                            settings.keyMemorySeconds.toFloat(), 2f..60f, 1f,
                        ) { onChange(settings.copy(keyMemorySeconds = it.roundToInt())) }
                        SliderSetting(
                            "Inertie de la tonalité : ${settings.keyInertiaPercent}", Help.KEY_INERTIA,
                            settings.keyInertiaPercent.toFloat(), 0f..10f, 1f,
                        ) { onChange(settings.copy(keyInertiaPercent = it.roundToInt())) }
                        Spacer(Modifier.height(4.dp))
                        SectionTitle("Suites probables")
                        val pr = settings.prediction
                        SwitchSetting("Afficher les mini claviers", Help.MINI_KEYBOARDS, pr.showMiniKeyboards) {
                            onChange(settings.copy(prediction = pr.copy(showMiniKeyboards = it)))
                        }
                        SliderSetting(
                            "Contexte : ${pr.contextSize} accord(s) précédent(s)", Help.PREDICTION_CONTEXT,
                            pr.contextSize.toFloat(), 1f..4f, 1f,
                        ) { onChange(settings.copy(prediction = pr.copy(contextSize = it.roundToInt()))) }
                        Label("Forme de l'arbre : ${pr.level1}-${pr.level2}-${pr.level3}", Help.PREDICTION_SHAPE)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Niveau 1 : ${pr.level1}", fontSize = 12.sp, color = Palette.muted)
                                CompactSlider(pr.level1.toFloat(), { onChange(settings.copy(prediction = pr.copy(level1 = it.roundToInt()))) }, 1f..6f, 1f)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Niveau 2 : ${pr.level2}", fontSize = 12.sp, color = Palette.muted)
                                CompactSlider(pr.level2.toFloat(), { onChange(settings.copy(prediction = pr.copy(level2 = it.roundToInt()))) }, 0f..4f, 1f)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Niveau 3 : ${pr.level3}", fontSize = 12.sp, color = Palette.muted)
                                CompactSlider(
                                    pr.level3.toFloat(),
                                    { onChange(settings.copy(prediction = pr.copy(level3 = it.roundToInt()))) },
                                    0f..3f,
                                    1f,
                                    enabled = pr.level2 > 0,
                                )
                            }
                        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyPanel(estimate: KeyEstimate?, modifier: Modifier) {
    Panel("Tonalité probable", modifier, help = Help.KEY) {
        val current = estimate?.current
        if (estimate == null || current == null) {
            Text("Jouez quelques accords…", fontSize = 18.sp, color = Palette.muted)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(current.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(estimate.probabilityOf(current) * 100).roundToInt()} %",
                    fontSize = 15.sp,
                    color = Palette.muted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                "Relative : ${current.relative.name} · ${estimate.compatible.size} compatible(s)",
                fontSize = 12.sp,
                color = Palette.muted,
            )
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp)) {
                CircleOfFifths(estimate, Modifier.fillMaxSize())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem(Palette.accent, "Probabilité")
                LegendOutline(Palette.compatible, "Compatible")
                LegendOutline(Palette.text, "Retenue")
            }
        }
    }
}
