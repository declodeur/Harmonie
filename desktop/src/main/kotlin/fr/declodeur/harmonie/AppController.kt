package fr.declodeur.harmonie

import fr.declodeur.harmonie.engine.AnalysisMode
import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.EngineSnapshot
import fr.declodeur.harmonie.engine.HarmonieEngine
import fr.declodeur.harmonie.engine.MusicStyle
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
        )
    }

    private fun saveSettings(s: AnalysisSettings) {
        prefs.put(KEY_STYLE, s.style.name)
        prefs.put(KEY_MODE, s.mode.name)
        prefs.putInt(KEY_CHANNEL, s.chordChannel)
        prefs.putInt(KEY_STABILITY, s.stabilityMs)
        prefs.putBoolean(KEY_SUSTAIN, s.includeSustain)
        prefs.putInt(KEY_SLASH_GAP, s.slashMinGap)
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
    }
}
