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


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeyPanel(estimate: KeyEstimate?, modifier: Modifier) {
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
            // Une autre tonalité presque aussi probable : on le dit plutôt que de trancher en silence
            val rival = estimate.ranking.firstOrNull { it.key != current && it.probability >= 0.25 }
            if (rival != null) {
                Text(
                    "Hésite avec ${rival.key.name} (${(rival.probability * 100).roundToInt()} %)" +
                        (if (rival.key == current.relative) ", sa relative" else "") +
                        " · ${estimate.compatible.size} compatible(s)",
                    fontSize = 12.sp,
                    color = Palette.compatible,
                )
            } else {
                Text(
                    "Relative : ${current.relative.name} · ${estimate.compatible.size} compatible(s)",
                    fontSize = 12.sp,
                    color = Palette.muted,
                )
            }
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
