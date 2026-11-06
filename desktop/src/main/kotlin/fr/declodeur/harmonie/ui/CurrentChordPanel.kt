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
internal fun CurrentChordPanel(
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
internal fun ChordDetails(
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
            analysis.function?.let { FunctionBadge(it, analysis.substitute) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(30.dp)) {
                Text(
                    analysis.category.label + if (analysis.contextPredicted) " · fonction provisoire" else "",
                    fontSize = 14.sp, color = Palette.muted,
                )
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
    analysis?.let { a ->
        a.degreeName?.let { InfoLine("Degré", "${a.numeral.takeWhile { c -> c in "IV♭♯" }}, $it", Help.DEGREE_NAME) }
    }
    InfoLine("Renversement", inversion, Help.INVERSION)
    NotesLine(result, best, snap, spelling)
    VoiceLines(snap)
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

/** Voicing de l'accord et conduite des voix depuis l'accord précédent (étape 5b). */
@Composable
internal fun VoiceLines(snap: EngineSnapshot) {
    snap.voicing?.let { v ->
        val details = buildList {
            add(v.layout)
            addAll(v.doublings)
            v.sopranoName?.let { add("soprano sur la $it") }
        }.joinToString(", ")
        WrappedInfoLine("Voicing", details, Help.VOICING)
        v.warning?.let { Text("Classique : $it", fontSize = 13.sp, color = Palette.warning) }
    }
    snap.voiceLeading?.let { vl ->
        fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
        val parts = buildList {
            if (vl.commonTones > 0) add(count(vl.commonTones, "note commune", "notes communes"))
            if (vl.steps > 0) add(count(vl.steps, "voix par degré conjoint", "voix par degré conjoint"))
            if (vl.leaps > 0) add(count(vl.leaps, "saut", "sauts"))
            vl.outerMotion?.let { add("mouvement $it des voix extrêmes") }
        }
        WrappedInfoLine("Depuis ${vl.from}", parts.joinToString(", "), Help.VOICE_LEADING)
        if (vl.guideTones.isNotEmpty()) {
            Text("Notes guides : " + vl.guideTones.joinToString("   "), fontSize = 13.sp, color = Palette.accent)
        }
        if (vl.guideTones.isNotEmpty() || vl.parallels.isNotEmpty() || snap.voicing?.warning != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                LegendItem(Palette.accent, "Notes guides (3ce, 7e)")
                LegendItem(Palette.warning, "Faute d'écriture classique")
            }
        }
        if (vl.parallels.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vl.parallels.joinToString(" ; ").replaceFirstChar { it.uppercase() },
                    fontSize = 13.sp, color = Palette.warning, modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                HelpBubble(Help.PARALLELS)
            }
        }
    }
}

/** Comme [InfoLine], mais la valeur peut passer à la ligne. */
@Composable
internal fun WrappedInfoLine(label: String, value: String, help: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label : ", fontSize = 15.sp, color = Palette.muted)
        Text(value, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(6.dp))
        HelpBubble(help)
    }
}

/**
 * Notes de l'accord avec octave et degré, colorées selon leur rôle ; notes tenues par la pédale
 * en couleur pâle ; mélodie (autres canaux) sur une ligne à part. Remplace l'ancien cadre
 * « Notes jouées ».
 */
@Composable
internal fun NotesLine(result: ChordResult, best: ChordCandidate?, snap: EngineSnapshot, spelling: Spelling) {
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
internal fun ScaleLines(scale: ChordScaleResult, chordSpelling: Spelling) {
    val chord = scale.chord
    val primary = scale.primary
    // Gamme de 7 notes : une lettre par note (C♯ altérée : C♯ D E F G A B, sans doublon de lettre)
    val spelling = primary.spelling(chordSpelling)
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
        val additions = scale.additions
        if (additions.isNotEmpty()) {
            withStyle(SpanStyle(color = Palette.muted)) { append("   ·   Ajouts : ") }
            withStyle(SpanStyle(color = Palette.chordTone)) { append(additions.joinToString(" ")) }
        }
    }
    Text(detail, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
    if (scale.alternatives.isNotEmpty()) {
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Aussi : " + scale.alternatives.joinToString(", ") { it.name(chordSpelling) },
                fontSize = 13.sp,
                color = Palette.muted,
            )
            Spacer(Modifier.width(6.dp))
            HelpBubble(scale.alternatives.joinToString("\n") { "• " + it.name(chordSpelling) + " : " + it.reason })
        }
    }
}

/** Chiffrage dans un badge encadré (un « I » seul ne doit pas ressembler à un trait). */
@Composable
internal fun NumeralBadge(numeral: String) {
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
internal fun InfoLine(label: String, value: String, help: String? = null) {
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
internal fun FunctionBadge(f: HarmonicFunction, substitute: Boolean = false) {
    val c = functionColor(f)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = c.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, c),
    ) {
        Text(
            if (substitute) "${f.label} substitutive (${f.short})" else "${f.label} (${f.short})",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 14.sp,
            color = c,
        )
    }
}
