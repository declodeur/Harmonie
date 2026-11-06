package fr.declodeur.harmonie.bench

import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.ChordCandidate
import fr.declodeur.harmonie.engine.HarmonicAnalysis
import fr.declodeur.harmonie.engine.HarmonieEngine
import fr.declodeur.harmonie.engine.Key

/** Ce qu'affiche l'application à un instant donné. */
data class Display(val chord: ChordCandidate?, val key: Key?, val analysis: HarmonicAnalysis?)

/** Ce qu'affiche l'application au fil du morceau, échantillonné toutes les [stepMs] ms. */
class Timeline(val stepMs: Long, val samples: List<Display>) {
    fun at(ms: Long): Display = samples[(ms / stepMs).toInt().coerceIn(0, samples.lastIndex)]
}

/**
 * Joue un morceau dans le moteur comme si un musicien le jouait sur un clavier MIDI, à tempo
 * constant, toutes les voix sur le même canal (mode Complète), sans pédale.
 */
object Player {
    const val TICK_MS = 5L
    const val SAMPLE_MS = 25L

    fun play(piece: Piece, settings: AnalysisSettings, msPerQuarter: Double): Timeline {
        val engine = HarmonieEngine(settings)
        // Événements : relâchés avant les attaques au même instant (note rejouée)
        data class Ev(val ms: Long, val on: Boolean, val midi: Int)
        val events = piece.notes.flatMap {
            val start = (it.start * msPerQuarter).toLong()
            val end = ((it.start + it.duration) * msPerQuarter).toLong()
            listOf(Ev(start, true, it.midi), Ev(maxOf(start + 1, end), false, it.midi))
        }.sortedWith(compareBy<Ev> { it.ms }.thenBy { it.on })
        val endMs = (events.maxOfOrNull { it.ms } ?: 0L) + 1000
        val samples = ArrayList<Display>()
        val held = IntArray(128)
        var i = 0
        var t = 0L
        var last = Display(null, null, null)
        var dirty = true
        while (t <= endMs) {
            while (i < events.size && events[i].ms <= t) {
                val e = events[i++]
                // Deux voix sur la même note : une seule touche, relâchée quand la dernière voix s'arrête
                if (e.on) {
                    if (held[e.midi]++ == 0) engine.noteOn(0, e.midi, 80, e.ms)
                    else { engine.noteOff(0, e.midi, e.ms); engine.noteOn(0, e.midi, 80, e.ms) }
                } else if (held[e.midi] > 0 && --held[e.midi] == 0) {
                    engine.noteOff(0, e.midi, e.ms)
                }
                dirty = true
            }
            if (engine.tick(t)) dirty = true
            if (t % SAMPLE_MS == 0L) {
                if (dirty) {
                    val s = engine.snapshot()
                    last = Display(s.current?.best, s.key?.current, s.currentAnalysis)
                    dirty = false
                }
                samples += last
            }
            t += TICK_MS
        }
        return Timeline(SAMPLE_MS, samples)
    }
}
