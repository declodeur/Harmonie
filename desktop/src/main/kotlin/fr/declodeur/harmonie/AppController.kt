package fr.declodeur.harmonie

import fr.declodeur.harmonie.engine.AnalysisMode
import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.EngineSnapshot
import fr.declodeur.harmonie.engine.HarmonieEngine
import fr.declodeur.harmonie.engine.SettingsCodec
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
    // Tout le morceau est gardé pour la grille ; l'écran n'affiche que les derniers accords
    private val engine = HarmonieEngine(loadSettings(), historyLimit = FULL_HISTORY)
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

    /** Grille de tout le morceau joué (cadre « Grille »). */
    fun grid(byDuration: Boolean, groupRepeats: Boolean) = engine.grid(byDuration, groupRepeats)

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

    /** Réglages : une seule table dans le moteur ([SettingsCodec]), les mêmes clés qu'avant. */
    private fun loadSettings(): AnalysisSettings = SettingsCodec.decode({ prefs.get(it, null) })

    private fun saveSettings(s: AnalysisSettings) {
        for ((k, v) in SettingsCodec.encode(s)) prefs.put(k, v)
    }

    private object ShortCommands {
        const val NOTE_OFF = 0x80
        const val NOTE_ON = 0x90
        const val CONTROL_CHANGE = 0xB0
    }

    private companion object {
        const val KEY_DEVICE = "device"
        const val FULL_HISTORY = 2000
        const val KEY_L_CHORD = "layout2ChordRatio"
        const val KEY_L_CIRCLE = "layout2CircleRatio"
        const val KEY_L_KEYBOARD = "layout2KeyboardHeight"
        const val KEY_L_TOP2 = "layout2TopHeight"
    }
}
