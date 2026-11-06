package fr.declodeur.harmonie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
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
import fr.declodeur.harmonie.engine.TensionModel
import fr.declodeur.harmonie.engine.PredictionSource
import fr.declodeur.harmonie.engine.ProgressionModel
import fr.declodeur.harmonie.engine.currentKeyParams
import fr.declodeur.harmonie.engine.withKeyParams
import fr.declodeur.harmonie.engine.withKeyParamsFor
import kotlin.math.roundToInt


/** Onglets de la fenêtre des Réglages. */
private enum class SettingsTab(val label: String) {
    RECOGNITION("Reconnaissance"),
    ANALYSIS("Analyse"),
    KEY("Tonalité"),
    DISPLAY("Suites et affichage"),
}

/** Hauteur fixe du contenu des onglets : la fenêtre ne change pas de taille d'un onglet à l'autre. */
private val TAB_CONTENT_H = 560.dp

/** Réglages : quatre onglets de deux colonnes, curseurs et interrupteurs compacts. */
@Composable
internal fun SettingsDialog(
    settings: AnalysisSettings,
    onChange: (AnalysisSettings) -> Unit,
    onResetLayout: () -> Unit,
    onClose: () -> Unit,
) {
    var tab by remember { mutableStateOf(SettingsTab.RECOGNITION) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Palette.surface,
            border = BorderStroke(1.dp, Palette.border),
            modifier = Modifier.width(1000.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text("Réglages", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                TabBar(tab) { tab = it }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.height(TAB_CONTENT_H), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    when (tab) {
                        SettingsTab.RECOGNITION -> RecognitionTab(settings, onChange)
                        SettingsTab.ANALYSIS -> AnalysisTab(settings, onChange)
                        SettingsTab.KEY -> KeyTab(settings, onChange)
                        SettingsTab.DISPLAY -> DisplayTab(settings, onChange)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
                Spacer(Modifier.height(8.dp))
                BottomActions(settings, onChange, onResetLayout, onClose)
            }
        }
    }
}

@Composable
private fun TabBar(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (t in SettingsTab.entries) {
                val on = t == selected
                Column(
                    Modifier.width(IntrinsicSize.Max).clickable { onSelect(t) }.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        t.label,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) Palette.accent else Palette.muted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) Palette.accent else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
    }
}

@Composable
private fun RowScope.SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun RowScope.RecognitionTab(settings: AnalysisSettings, onChange: (AnalysisSettings) -> Unit) {
    SettingsColumn {
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
        val bo = settings.bassOptions
        SwitchSetting("Basse fondamentale : lecture complète (Am7 plutôt que C/A)", Help.KEEP_ROOT_POSITION, bo.keepRootPositionReading) {
            onChange(settings.copy(bassOptions = bo.copy(keepRootPositionReading = it)))
        }
        SwitchSetting(
            "Dessus incomplet : compter la basse (C/E plutôt que C1+5/E)", Help.KEEP_FULL_WHEN_INCOMPLETE,
            bo.keepFullReadingWhenUpperIncomplete,
        ) { onChange(settings.copy(bassOptions = bo.copy(keepFullReadingWhenUpperIncomplete = it))) }
        SwitchSetting("Classique : septième à la basse gardée (G7/F plutôt que G/F)", Help.KEEP_SEVENTH_IN_BASS, bo.keepSeventhInBass) {
            onChange(settings.copy(bassOptions = bo.copy(keepSeventhInBass = it)))
        }
    }
    SettingsColumn {
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
}

@Composable
private fun RowScope.AnalysisTab(settings: AnalysisSettings, onChange: (AnalysisSettings) -> Unit) {
    val ip = settings.interpretation
    SettingsColumn {
        SectionTitle("Interprétation des accords")
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
        SwitchSetting("Notes de passage : l'accord continue (mode Complète)", Help.PASSING_TONES, ip.passingTones) {
            onChange(settings.copy(interpretation = ip.copy(passingTones = it)))
        }
    }
    SettingsColumn {
        SectionTitle("Chiffrage")
        SwitchSetting(
            "Degrés en notation jazz (♭III, ♭VI, ♭VII en mineur)", Help.DEGREE_NOTATION,
            settings.degreeNotation == DegreeNotation.JAZZ,
        ) { onChange(settings.copy(degreeNotation = if (it) DegreeNotation.JAZZ else DegreeNotation.CLASSICAL)) }
        SwitchSetting("Fonction de VI et VII selon l'accord suivant", Help.CONTEXT_FUNCTIONS, settings.contextualFunctions) {
            onChange(settings.copy(contextualFunctions = it))
        }
        SliderSetting(
            if (settings.reanalysisDepth == 0) "Réanalyse a posteriori : désactivée"
            else "Réanalyse a posteriori : jusqu'à ${settings.reanalysisDepth} accords",
            Help.REANALYSIS, settings.reanalysisDepth.toFloat(), 0f..16f, 1f,
        ) { onChange(settings.copy(reanalysisDepth = it.roundToInt())) }
    }
}

@Composable
private fun RowScope.KeyTab(settings: AnalysisSettings, onChange: (AnalysisSettings) -> Unit) {
    SettingsColumn {
        SectionTitle("Tonalité")
        SwitchSetting("Changement immédiat sur un accord hors tonalité", Help.FAST_KEY_CHANGE, settings.fastKeyChange) {
            onChange(settings.copy(fastKeyChange = it))
        }
        SwitchSetting("Changement immédiat sur une cadence parfaite", Help.CADENCE_KEY_CHANGE, settings.cadenceKeyChange) {
            onChange(settings.copy(cadenceKeyChange = it))
        }
        SliderSetting(
            "Poids de la mélodie : ${settings.melodyWeightPercent} %", Help.MELODY_WEIGHT,
            settings.melodyWeightPercent.toFloat(), 0f..100f, 10f,
        ) { onChange(settings.copy(melodyWeightPercent = it.roundToInt())) }
    }
    SettingsColumn {
        // Style réglé : celui de la barre du haut par défaut, mais on peut régler les autres d'ici
        var tuned by remember { mutableStateOf(settings.style) }
        val kp = settings.keyParams[tuned] ?: KeyParams.POP
        fun edit(f: (KeyParams) -> KeyParams) = onChange(settings.withKeyParamsFor(tuned, f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Modèle de tonalité par style")
            Spacer(Modifier.width(6.dp))
            HelpBubble(Help.KEY_PER_STYLE)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { edit { KeyParams.DEFAULTS[tuned] ?: KeyParams.POP } }) {
                Text("Valeurs du banc d'essai", fontSize = 12.sp)
            }
        }
        StyleChips(tuned, current = settings.style) { tuned = it }
        SliderSetting(
            if (kp.memoryChords == 0) "Mémoire : en secondes (voir ci-dessous)" else "Mémoire : ${kp.memoryChords} accords",
            Help.KEY_MEMORY_CHORDS, kp.memoryChords.toFloat(), 0f..24f, 1f,
        ) { v -> edit { it.copy(memoryChords = v.roundToInt()) } }
        SliderSetting(
            "Mémoire en secondes : ${settings.keyMemorySeconds} s", Help.KEY_MEMORY,
            settings.keyMemorySeconds.toFloat(), 2f..60f, 1f, enabled = kp.memoryChords == 0,
        ) { onChange(settings.copy(keyMemorySeconds = it.roundToInt())) }
        SliderSetting(
            "Poids de l'accord de tonique : ${kp.tonicPercent} (dont dominante ${kp.dominantPercent} %)",
            Help.KEY_TONIC_WEIGHT, kp.tonicPercent.toFloat(), 0f..100f, 5f,
        ) { v -> edit { it.copy(tonicPercent = v.roundToInt()) } }
        SliderSetting(
            "Poids des arrivées (V → I, ♭VII → i, IV → I) : ${kp.arrivalPercent}",
            Help.KEY_ARRIVAL, kp.arrivalPercent.toFloat(), 0f..100f, 5f,
        ) { v -> edit { it.copy(arrivalPercent = v.roundToInt()) } }
        SliderSetting(
            "Poids du premier accord : ${kp.primacyPercent}", Help.KEY_ARRIVAL,
            kp.primacyPercent.toFloat(), 0f..100f, 5f,
        ) { v -> edit { it.copy(primacyPercent = v.roundToInt()) } }
        SliderSetting(
            if (kp.holdPercent == 0) "Maintien dans la gamme : désactivé" else "Maintien dans la gamme : ${kp.holdPercent}",
            Help.KEY_HOLD, kp.holdPercent.toFloat(), 0f..60f, 5f,
        ) { v -> edit { it.copy(holdPercent = v.roundToInt()) } }
        SliderSetting(
            "Inertie de la tonalité : ${kp.inertiaPercent}", Help.KEY_INERTIA,
            kp.inertiaPercent.toFloat(), 0f..10f, 1f,
        ) { v -> edit { it.copy(inertiaPercent = v.roundToInt()) } }
    }
}

/** Choix du style dont on règle le modèle de tonalité ; le style joué est repéré par un point. */
@Composable
private fun StyleChips(selected: MusicStyle, current: MusicStyle, onSelect: (MusicStyle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (st in MusicStyle.entries) {
            val on = st == selected
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (on) Palette.accent.copy(alpha = 0.18f) else Color.Transparent,
                border = BorderStroke(1.dp, if (on) Palette.accent else Palette.border),
                modifier = Modifier.clickable { onSelect(st) },
            ) {
                Text(
                    st.label + if (st == current) " •" else "",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (on) Palette.accent else Palette.text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text("• style joué", fontSize = 11.sp, color = Palette.muted)
    }
}

@Composable
private fun RowScope.DisplayTab(settings: AnalysisSettings, onChange: (AnalysisSettings) -> Unit) {
    val pr = settings.prediction
    SettingsColumn {
        SectionTitle("Suites probables")
        SwitchSetting("Statistiques des corpus d'experts (classique, pop/rock)", Help.PREDICTION_SOURCE, pr.source == PredictionSource.CORPUS) {
            onChange(settings.copy(prediction = pr.copy(source = if (it) PredictionSource.CORPUS else PredictionSource.THEORY)))
        }
        SliderSetting(
            "Contexte : ${pr.contextSize} accord(s) précédent(s)", Help.PREDICTION_CONTEXT,
            pr.contextSize.toFloat(), 1f..4f, 1f,
        ) { onChange(settings.copy(prediction = pr.copy(contextSize = it.roundToInt()))) }
        SliderSetting(
            if (pr.expandThresholdPercent == 0) "Prolonger toutes les branches"
            else "Prolonger une branche à partir de ${pr.expandThresholdPercent} %",
            Help.PREDICTION_THRESHOLD, pr.expandThresholdPercent.toFloat(), 0f..50f, 5f,
        ) { onChange(settings.copy(prediction = pr.copy(expandThresholdPercent = it.roundToInt()))) }
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
    SettingsColumn {
        SectionTitle("Conduite des voix")
        val vs = settings.voicing
        SwitchSetting("Signaler les quintes et octaves consécutives", Help.PARALLELS, vs.flagParallels) {
            onChange(settings.copy(voicing = vs.copy(flagParallels = it)))
        }
        SwitchSetting("… dans tous les styles (sinon en classique)", Help.PARALLELS_ALL_STYLES, vs.parallelsInAllStyles) {
            onChange(settings.copy(voicing = vs.copy(parallelsInAllStyles = it)))
        }
        Spacer(Modifier.height(4.dp))
        SectionTitle("Historique et cercle des quintes")
        SwitchSetting(
            "Tension : ancien modèle simple (v0.15)", Help.TENSION_MODEL,
            settings.tensionModel == TensionModel.SIMPLE,
        ) { onChange(settings.copy(tensionModel = if (it) TensionModel.SIMPLE else TensionModel.TONAL_SPACE)) }
        SliderSetting(
            "Parcours sur le cercle : ${settings.circlePathLength} derniers accords",
            Help.CIRCLE_PATH, settings.circlePathLength.toFloat(), 2f..12f, 1f,
        ) { onChange(settings.copy(circlePathLength = it.roundToInt())) }
        Spacer(Modifier.height(4.dp))
        Text(
            "Les options d'affichage (gamme, voicing, voix, mini claviers, parcours, tension, durées) sont dans la " +
                "barre de titre de chaque cadre de l'écran principal.",
            fontSize = 12.sp, lineHeight = 16.sp, color = Palette.muted,
        )
    }
}

@Composable
private fun BottomActions(
    settings: AnalysisSettings,
    onChange: (AnalysisSettings) -> Unit,
    onResetLayout: () -> Unit,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Remise à zéro en deux temps : un clic de trop ne doit pas effacer des réglages choisis
        var confirmDefaults by remember { mutableStateOf(false) }
        if (!confirmDefaults) {
            TextButton(onClick = { confirmDefaults = true }) { Text("Réglages par défaut") }
            HelpBubble(Help.DEFAULT_SETTINGS)
        } else {
            Text("Remettre tous les réglages à leur valeur par défaut ?", fontSize = 14.sp, color = Palette.warning)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                // Le style, le mode d'analyse et le canal (barre du haut) sont conservés
                onChange(AnalysisSettings(style = settings.style, mode = settings.mode, chordChannel = settings.chordChannel))
                confirmDefaults = false
            }) { Text("Oui, tout remettre") }
            TextButton(onClick = { confirmDefaults = false }) { Text("Annuler") }
        }
        Spacer(Modifier.width(16.dp))
        TextButton(onClick = onResetLayout) { Text("Réinitialiser la disposition de l'écran") }
        HelpBubble(Help.LAYOUT)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onClose) { Text("Fermer") }
    }
}
