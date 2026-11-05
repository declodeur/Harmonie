package fr.declodeur.harmonie.engine

/**
 * État des notes enfoncées, par canal MIDI (0 à 15), avec la pédale de sustain (CC 64).
 * Une note relâchée pendant que la pédale est enfoncée reste « tenue par la pédale »
 * jusqu'au relâchement de la pédale.
 */
class NoteTracker {
    private val held = Array(16) { LinkedHashMap<Int, Int>() }
    private val sustained = Array(16) { LinkedHashMap<Int, Int>() }
    private val pedal = BooleanArray(16)

    fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (velocity == 0) {
            noteOff(channel, note)
            return
        }
        sustained[channel].remove(note)
        held[channel][note] = velocity
    }

    fun noteOff(channel: Int, note: Int) {
        val velocity = held[channel].remove(note) ?: return
        if (pedal[channel]) sustained[channel][note] = velocity
    }

    fun controlChange(channel: Int, controller: Int, value: Int) {
        when (controller) {
            64 -> {
                val down = value >= 64
                if (!down) sustained[channel].clear()
                pedal[channel] = down
            }
            120, 123 -> {
                held[channel].clear()
                sustained[channel].clear()
            }
        }
    }

    fun isHeld(channel: Int, note: Int): Boolean = note in held[channel]

    fun isPedalDown(channel: Int): Boolean = pedal[channel]

    fun reset() {
        for (ch in 0 until 16) {
            held[ch].clear()
            sustained[ch].clear()
            pedal[ch] = false
        }
    }

    /** Toutes les notes qui sonnent, sur tous les canaux. */
    fun sounding(): List<TrackedNote> {
        val result = ArrayList<TrackedNote>()
        for (ch in 0 until 16) {
            held[ch].forEach { (note, vel) -> result += TrackedNote(note, ch, vel, sustained = false) }
            sustained[ch].forEach { (note, vel) -> result += TrackedNote(note, ch, vel, sustained = true) }
        }
        return result
    }

    data class TrackedNote(val note: Int, val channel: Int, val velocity: Int, val sustained: Boolean)
}
