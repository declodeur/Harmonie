package fr.declodeur.harmonie.midi

import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.Sequencer
import javax.sound.midi.ShortMessage
import javax.sound.midi.Synthesizer
import javax.sound.midi.Transmitter

/** Une entrée MIDI disponible sur le PC. */
data class MidiInputInfo(val name: String, val description: String, internal val info: MidiDevice.Info) {
    override fun toString(): String = name
}

/**
 * Accès aux entrées MIDI de Windows via javax.sound.midi (API MIDI classique).
 * [onShort] reçoit (commande, canal, donnée 1, donnée 2) sur le fil MIDI de Java.
 */
class MidiInputManager(private val onShort: (command: Int, channel: Int, data1: Int, data2: Int) -> Unit) {
    private var device: MidiDevice? = null
    private var transmitter: Transmitter? = null

    var openedName: String? = null
        private set

    fun listInputs(): List<MidiInputInfo> = MidiSystem.getMidiDeviceInfo().mapNotNull { info ->
        val dev = runCatching { MidiSystem.getMidiDevice(info) }.getOrNull() ?: return@mapNotNull null
        if (dev is Sequencer || dev is Synthesizer) return@mapNotNull null
        if (dev.maxTransmitters == 0) return@mapNotNull null
        MidiInputInfo(info.name, info.description ?: "", info)
    }

    /** Ouvre l'entrée ; lève une exception si elle est occupée ou indisponible. */
    fun open(input: MidiInputInfo) {
        close()
        val dev = MidiSystem.getMidiDevice(input.info)
        dev.open()
        try {
            val tx = dev.transmitter
            tx.receiver = object : Receiver {
                override fun send(message: MidiMessage, timeStamp: Long) {
                    if (message is ShortMessage) {
                        onShort(message.command, message.channel, message.data1, message.data2)
                    }
                }

                override fun close() {}
            }
            device = dev
            transmitter = tx
            openedName = input.name
        } catch (e: Exception) {
            runCatching { dev.close() }
            throw e
        }
    }

    fun close() {
        runCatching { transmitter?.close() }
        runCatching { device?.close() }
        transmitter = null
        device = null
        openedName = null
    }
}
