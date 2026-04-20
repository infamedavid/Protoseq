package com.infamedavid.protoseq.features.transport

import android.content.Context
import android.media.midi.MidiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.infamedavid.protoseq.core.clock.ClockEngine
import com.infamedavid.protoseq.core.clock.TransportState
import com.infamedavid.protoseq.core.midi.AndroidMidiMessageSender
import com.infamedavid.protoseq.core.midi.MidiDeviceRepository
import com.infamedavid.protoseq.core.midi.MidiEngine
import com.infamedavid.protoseq.core.midi.NoOpMidiMessageSender
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerConfig
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerEngine
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerUiState
import com.infamedavid.protoseq.features.stochastic.toConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransportViewModel(
    private val clockEngine: ClockEngine = ClockEngine(),
    private val midiEngine: MidiEngine = MidiEngine(
        messageSender = NoOpMidiMessageSender(),
        midiDeviceRepository = MidiDeviceRepository()
    )
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sequencerEngine = StochasticSequencerEngine()
    private var sequencerConfig: StochasticSequencerConfig = StochasticSequencerUiState().toConfig()
    private val scheduledNoteOffs = mutableListOf<ScheduledNoteOff>()

    private val _uiState = MutableStateFlow(
        TransportUiState(
            bpm = clockEngine.getBpm().toFloat(),
            state = clockEngine.getTransportState()
        )
    )
    val uiState: StateFlow<TransportUiState> = _uiState.asStateFlow()

    init {
        midiEngine.startDeviceMonitoring { targets, selected ->
            _uiState.value = _uiState.value.copy(
                midiOutputTargets = targets,
                selectedMidiOutputId = selected?.selectionId
            )
        }

        vmScope.launch {
            clockEngine.transportState.collect { state ->
                _uiState.value = _uiState.value.copy(state = state)
            }
        }

        vmScope.launch {
            clockEngine.ticks.collect { tick ->
                processDueNoteOffs(tick)
                if (isSequencerStepTick(tick)) {
                    advanceSequencer(tick)
                }
                if (tick % 4L == 0L) {
                    midiEngine.sendClock()
                }
            }
        }
    }

    fun setBpm(bpm: Float) {
        val clampedBpm = bpm.coerceIn(40f, 240f)
        clockEngine.setBpm(clampedBpm.toDouble())
        _uiState.value = _uiState.value.copy(bpm = clampedBpm)
    }

    fun selectMidiOutputTarget(selectionId: String) {
        val target = midiEngine.getOutputTargets().firstOrNull { it.selectionId == selectionId } ?: return
        midiEngine.selectOutputTarget(target)
        _uiState.value = _uiState.value.copy(selectedMidiOutputId = target.selectionId)
    }

    fun clearMidiOutputSelection() {
        midiEngine.clearSelection()
        _uiState.value = _uiState.value.copy(selectedMidiOutputId = null)
    }

    fun play() {
        when (clockEngine.getTransportState()) {
            TransportState.Stopped -> {
                sendAndClearPendingNoteOffs()
                sequencerEngine.reset()
                clockEngine.playFromStart()
                midiEngine.sendStart()
            }

            TransportState.Paused -> {
                clockEngine.resume()
                midiEngine.sendContinue()
            }

            TransportState.Playing -> Unit
        }
    }

    fun stop() {
        when (clockEngine.getTransportState()) {
            TransportState.Playing,
            TransportState.Paused -> {
                sendAndClearPendingNoteOffs()
                clockEngine.stop()
                midiEngine.sendStop()
            }

            TransportState.Stopped -> Unit
        }
    }

    fun pause() {
        if (clockEngine.getTransportState() == TransportState.Playing) {
            sendAndClearPendingNoteOffs()
            clockEngine.pause()
        }
    }

    fun updateSequencerConfig(config: StochasticSequencerConfig) {
        sequencerConfig = config.sanitized()
    }

    private fun isSequencerStepTick(tick: Long): Boolean = ((tick - 1L) % TICKS_PER_STEP) == 0L

    private fun processDueNoteOffs(currentTick: Long) {
        val iterator = scheduledNoteOffs.iterator()
        while (iterator.hasNext()) {
            val scheduled = iterator.next()
            if (scheduled.dueTick == currentTick) {
                midiEngine.sendNoteOff(
                    channel = scheduled.channel,
                    note = scheduled.note
                )
                iterator.remove()
            }
        }
    }

    private fun advanceSequencer(currentTick: Long) {
        val output = sequencerEngine.advance(sequencerConfig)
        when (sequencerConfig.outputMode) {
            MidiOutputMode.NOTE -> {
                val note = output.note ?: return
                if (!output.gate || !output.trigger) return

                midiEngine.sendNoteOn(
                    channel = sequencerConfig.midiChannel,
                    note = note
                )
                scheduledNoteOffs += ScheduledNoteOff(
                    dueTick = currentTick + output.gateLengthTicks,
                    channel = sequencerConfig.midiChannel,
                    note = note
                )
            }

            MidiOutputMode.CC -> {
                val ccValue = output.ccValue ?: return
                if (!output.gate || !output.trigger) return

                midiEngine.sendControlChange(
                    channel = sequencerConfig.midiChannel,
                    controller = sequencerConfig.ccNumber,
                    value = ccValue
                )
            }
        }
    }

    private fun sendAndClearPendingNoteOffs() {
        scheduledNoteOffs.forEach { scheduled ->
            midiEngine.sendNoteOff(channel = scheduled.channel, note = scheduled.note)
        }
        scheduledNoteOffs.clear()
    }

    override fun onCleared() {
        sendAndClearPendingNoteOffs()
        midiEngine.stopDeviceMonitoring()
        vmScope.cancel()
        super.onCleared()
    }

    companion object {
        private const val TICKS_PER_STEP = 24L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val midiManager = appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
                    val messageSender =
                        midiManager?.let { AndroidMidiMessageSender(it) } ?: NoOpMidiMessageSender()
                    val midiEngine = MidiEngine(
                        messageSender = messageSender,
                        midiDeviceRepository = MidiDeviceRepository(midiManager)
                    )
                    return TransportViewModel(
                        clockEngine = ClockEngine(),
                        midiEngine = midiEngine
                    ) as T
                }
            }
        }
    }

    private data class ScheduledNoteOff(
        val dueTick: Long,
        val channel: Int,
        val note: Int
    )
}
