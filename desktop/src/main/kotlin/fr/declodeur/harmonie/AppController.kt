package fr.declodeur.harmonie

import fr.declodeur.harmonie.engine.AnalysisMode
import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.BassReadingOptions
import fr.declodeur.harmonie.engine.EngineSnapshot
import fr.declodeur.harmonie.engine.HarmonieEngine
import fr.declodeur.harmonie.engine.MusicStyle
import fr.declodeur.harmonie.engine.PredictionSettings
import fr.declodeur.harmonie.midi.MidiInputInfo
import fr.declodeur.harmonie.midi.MidiInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.prefs.Preferences

/**
 * Disposition de l'écran principal, ajustée en déplaçant les frontières des cadres.
 * [topHeightDp] < 0 : hauteur automatique de la rangée du haut.
 */
data class LayoutPrefs(
    /** Part de la largeur de la rangée du haut pour l'accord courant. */
    val chordRatio: Float = 0.30f,
    /** Part de la largeur de la rangée du haut pour le cercle des quintes (l'arbre prend le reste). */
    val circleRatio: Float = 0.27f,
    /** Hauteur du clavier. */
    val keyboardHeightDp: Float = 150f,
    /** Hauteur de la rangée du haut ; < 0 : automatique (l'historique garde sa hauteur naturelle). */
    val topHeightDp: Float = -1f,
)

/** Relie l'entrée MIDI, le moteur d'analyse et l'interface. */
class AppController(scope: CoroutineScope) {
    private val prefs: Preferences = Preferences.userRoot().node("fr/declodeur/harmonie")
    private val engine = HarmonieEngine(loadSettings())
    private val midi = MidiInputManager(::onShortMessage)

    private val _snapshot = MutableStateFlow(engine.snapshot())
    val snapshot: StateFlow<EngineSnapshot> = _snapshot.asStateFlow()

    private val _settings = MutableStateFlow(engine.settings)
    val settings: StateFlow<AnalysisSettings> = _settings.asStateFlow()

    private val _devices = MutableStateFlow<List<MidiInputInfo>>(emptyList())
    val devices: StateFlow<List<MidiInputInfo>> = _devices.asStateFlow()

    private val _connected = MutableStateFlow<String?>(null)
    val connected: StateFlow<String?> = _connected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _layout = MutableStateFlow(loadLayout())
    val layout: StateFlow<LayoutPrefs> = _layout.asStateFlow()

    init {
        refreshDevices(autoConnect = true)
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (engine.tick(now())) publish()
                delay(5)
            }
        }
    }

    private fun now(): Long = System.nanoTime() / 1_000_000

    private fun onShortMessage(command: Int, channel: Int, data1: Int, data2: Int) {
        val t = now()
        when (command) {
            ShortCommands.NOTE_ON -> engine.noteOn(channel, data1, data2, t)
            ShortCommands.NOTE_OFF -> engine.noteOff(channel, data1, t)
            ShortCommands.CONTROL_CHANGE -> engine.controlChange(channel, data1, data2, t)
            else -> return
        }
        publish()
    }

    private fun publish() {
        _snapshot.value = engine.snapshot()
    }

    fun refreshDevices(autoConnect: Boolean = false) {
        val list = runCatching { midi.listInputs() }.getOrElse {
            _error.value = "Impossible de lister les entrées MIDI : ${it.message}"
            emptyList()
        }
        _devices.value = list
        if (autoConnect && _connected.value == null && list.isNotEmpty()) {
            val saved = prefs.get(KEY_DEVICE, null)
            connect(list.firstOrNull { it.name == saved } ?: list.first())
        }
    }

    fun connect(input: MidiInputInfo) {
        try {
            midi.open(input)
            _connected.value = input.name
            _error.value = null
            prefs.put(KEY_DEVICE, input.name)
        } catch (e: Exception) {
            _connected.value = null
            _error.value = "Impossible d'ouvrir « ${input.name} » : ${e.message ?: e.javaClass.simpleName}. " +
                "Est-elle utilisée par un autre logiciel ?"
        }
        engine.releaseAll(now())
        publish()
    }

    fun updateSettings(newSettings: AnalysisSettings) {
        engine.updateSettings(newSettings, now())
        _settings.value = newSettings
        saveSettings(newSettings)
        publish()
    }

    /** Clic sur une touche du clavier affiché : enfonce ou relâche la note (test sans instrument). */
    fun toggleKey(note: Int) {
        val s = engine.settings
        val channel = if (s.mode == AnalysisMode.CHORDS_AND_MELODY) s.chordChannel else 0
        if (engine.isHeld(channel, note)) engine.noteOff(channel, note, now())
        else engine.noteOn(channel, note, 90, now())
        publish()
    }

    fun releaseAll() {
        engine.releaseAll(now())
        publish()
    }

    fun clearHistory() {
        engine.clearHistory()
        publish()
    }

    fun saveLayout(l: LayoutPrefs) {
        _layout.value = l
        prefs.putFloat(KEY_L_CHORD, l.chordRatio)
        prefs.putFloat(KEY_L_CIRCLE, l.circleRatio)
        prefs.putFloat(KEY_L_KEYBOARD, l.keyboardHeightDp)
        prefs.putFloat(KEY_L_TOP2, l.topHeightDp)
    }

    fun resetLayout() = saveLayout(LayoutPrefs())

    private fun loadLayout(): LayoutPrefs {
        val d = LayoutPrefs()
        return LayoutPrefs(
            chordRatio = prefs.getFloat(KEY_L_CHORD, d.chordRatio),
            circleRatio = prefs.getFloat(KEY_L_CIRCLE, d.circleRatio),
            keyboardHeightDp = prefs.getFloat(KEY_L_KEYBOARD, d.keyboardHeightDp),
            topHeightDp = prefs.getFloat(KEY_L_TOP2, d.topHeightDp),
        )
    }

    fun dispose() {
        midi.close()
    }

    private fun loadSettings(): AnalysisSettings {
        val d = AnalysisSettings()
        return AnalysisSettings(
            style = runCatching { MusicStyle.valueOf(prefs.get(KEY_STYLE, d.style.name)) }.getOrDefault(d.style),
            mode = runCatching { AnalysisMode.valueOf(prefs.get(KEY_MODE, d.mode.name)) }.getOrDefault(d.mode),
            chordChannel = prefs.getInt(KEY_CHANNEL, d.chordChannel).coerceIn(0, 15),
            stabilityMs = prefs.getInt(KEY_STABILITY, d.stabilityMs).coerceIn(20, 200),
            includeSustain = prefs.getBoolean(KEY_SUSTAIN, d.includeSustain),
            slashMinGap = prefs.getInt(KEY_SLASH_GAP, d.slashMinGap).coerceIn(0, 24),
            bassOptions = BassReadingOptions(
                keepRootPositionReading = prefs.getBoolean(KEY_KEEP_ROOT, d.bassOptions.keepRootPositionReading),
                keepFullReadingWhenUpperIncomplete =
                    prefs.getBoolean(KEY_KEEP_FULL, d.bassOptions.keepFullReadingWhenUpperIncomplete),
            ),
            keyMemorySeconds = prefs.getInt(KEY_KEY_MEMORY, d.keyMemorySeconds).coerceIn(4, 60),
            melodyWeightPercent = prefs.getInt(KEY_MELODY_WEIGHT, d.melodyWeightPercent).coerceIn(0, 100),
            fastKeyChange = prefs.getBoolean(KEY_FAST_KEY, d.fastKeyChange),
            arpeggioDetection = prefs.getBoolean(KEY_ARPEGGIO, d.arpeggioDetection),
            arpeggioWindowMs = prefs.getInt(KEY_ARPEGGIO_WINDOW, d.arpeggioWindowMs).coerceIn(500, 5000),
            arpeggioMaxNotes = prefs.getInt(KEY_ARPEGGIO_NOTES, d.arpeggioMaxNotes).coerceIn(3, 12),
            prediction = PredictionSettings(
                contextSize = prefs.getInt(KEY_P_CONTEXT, d.prediction.contextSize).coerceIn(1, 4),
                level1 = prefs.getInt(KEY_P_L1, d.prediction.level1).coerceIn(1, 6),
                level2 = prefs.getInt(KEY_P_L2, d.prediction.level2).coerceIn(0, 4),
                level3 = prefs.getInt(KEY_P_L3, d.prediction.level3).coerceIn(0, 3),
                showMiniKeyboards = prefs.getBoolean(KEY_P_KEYBOARDS, d.prediction.showMiniKeyboards),
            ),
        )
    }

    private fun saveSettings(s: AnalysisSettings) {
        prefs.put(KEY_STYLE, s.style.name)
        prefs.put(KEY_MODE, s.mode.name)
        prefs.putInt(KEY_CHANNEL, s.chordChannel)
        prefs.putInt(KEY_STABILITY, s.stabilityMs)
        prefs.putBoolean(KEY_SUSTAIN, s.includeSustain)
        prefs.putInt(KEY_SLASH_GAP, s.slashMinGap)
        prefs.putBoolean(KEY_KEEP_ROOT, s.bassOptions.keepRootPositionReading)
        prefs.putBoolean(KEY_KEEP_FULL, s.bassOptions.keepFullReadingWhenUpperIncomplete)
        prefs.putInt(KEY_KEY_MEMORY, s.keyMemorySeconds)
        prefs.putInt(KEY_MELODY_WEIGHT, s.melodyWeightPercent)
        prefs.putBoolean(KEY_FAST_KEY, s.fastKeyChange)
        prefs.putBoolean(KEY_ARPEGGIO, s.arpeggioDetection)
        prefs.putInt(KEY_ARPEGGIO_WINDOW, s.arpeggioWindowMs)
        prefs.putInt(KEY_ARPEGGIO_NOTES, s.arpeggioMaxNotes)
        prefs.putInt(KEY_P_CONTEXT, s.prediction.contextSize)
        prefs.putInt(KEY_P_L1, s.prediction.level1)
        prefs.putInt(KEY_P_L2, s.prediction.level2)
        prefs.putInt(KEY_P_L3, s.prediction.level3)
        prefs.putBoolean(KEY_P_KEYBOARDS, s.prediction.showMiniKeyboards)
    }

    private object ShortCommands {
        const val NOTE_OFF = 0x80
        const val NOTE_ON = 0x90
        const val CONTROL_CHANGE = 0xB0
    }

    private companion object {
        const val KEY_DEVICE = "device"
        const val KEY_STYLE = "style"
        const val KEY_MODE = "mode"
        const val KEY_CHANNEL = "chordChannel"
        const val KEY_STABILITY = "stabilityMs"
        const val KEY_SUSTAIN = "includeSustain"
        const val KEY_SLASH_GAP = "slashMinGap"
        const val KEY_KEEP_ROOT = "keepRootPositionReading"
        const val KEY_KEEP_FULL = "keepFullReadingWhenUpperIncomplete"
        const val KEY_KEY_MEMORY = "keyMemorySeconds"
        const val KEY_MELODY_WEIGHT = "melodyWeightPercent"
        const val KEY_ARPEGGIO = "arpeggioDetection"
        const val KEY_FAST_KEY = "fastKeyChange"
        const val KEY_L_CHORD = "layout2ChordRatio"
        const val KEY_L_CIRCLE = "layout2CircleRatio"
        const val KEY_L_KEYBOARD = "layout2KeyboardHeight"
        const val KEY_L_TOP2 = "layout2TopHeight"
        const val KEY_P_KEYBOARDS = "predictionMiniKeyboards"
        const val KEY_P_CONTEXT = "predictionContext"
        const val KEY_P_L1 = "predictionLevel1"
        const val KEY_P_L2 = "predictionLevel2"
        const val KEY_P_L3 = "predictionLevel3"
        const val KEY_ARPEGGIO_WINDOW = "arpeggioWindowMs"
        const val KEY_ARPEGGIO_NOTES = "arpeggioMaxNotes"
    }
}
