package com.infamedavid.protoseq.features.transport

import android.content.Context
import android.media.midi.MidiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.infamedavid.protoseq.core.clock.ClockEngine
import com.infamedavid.protoseq.core.clock.TransportState
import com.infamedavid.protoseq.core.midi.AndroidMidiMessageSender
import com.infamedavid.protoseq.core.repeater.MidiNoteOffEvent
import com.infamedavid.protoseq.core.repeater.MidiNoteOnEvent
import com.infamedavid.protoseq.core.midi.MidiDeviceRepository
import com.infamedavid.protoseq.core.midi.MidiEngine
import com.infamedavid.protoseq.core.midi.NoOpMidiMessageSender
import com.infamedavid.protoseq.core.repeater.RepeaterEngine
import com.infamedavid.protoseq.core.repeater.RptrConfig
import com.infamedavid.protoseq.core.repeater.RptrDivision
import com.infamedavid.protoseq.core.repeater.RptrMidiOut
import com.infamedavid.protoseq.core.repeater.RptrState
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
import kotlin.math.roundToInt

class TransportViewModel(
    private val clockEngine: ClockEngine = ClockEngine(),
    private val midiEngine: MidiEngine = MidiEngine(
        messageSender = NoOpMidiMessageSender(),
        midiDeviceRepository = MidiDeviceRepository()
    )
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sequencerEngine = StochasticSequencerEngine()
    private val repeaterEngine = RepeaterEngine()
    private var sequencerConfig: StochasticSequencerConfig = StochasticSequencerUiState().toConfig()
    private val scheduledNoteOffs = mutableListOf<ScheduledNoteOff>()
    private var lastSentCcValue: Int? = null
    private var activeCcSlew: ActiveCcSlew? = null
    private var latestClockTick: Long = 1L

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
                latestClockTick = tick
                val repeaterTickResult = repeaterEngine.onTick(tick)
                sendRepeaterMidi(repeaterTickResult.midi)
                syncRptrUiState()
                processDueNoteOffs(tick)
                if (isSequencerStepTick(tick)) {
                    advanceSequencer(tick)
                }
                processCcSlewTick()
                if (tick % 4L == 0L) {
                    midiEngine.sendClock()
                }
            }
        }
    }

    fun setBpm(bpm: Float) {
        val clampedBpm = bpm.coerceIn(1f, 300f)
        clockEngine.setBpm(clampedBpm.toDouble())
        _uiState.value = _uiState.value.copy(bpm = clampedBpm)
    }

    fun incrementBpm() {
        setBpm(_uiState.value.bpm + 1f)
    }

    fun decrementBpm() {
        setBpm(_uiState.value.bpm - 1f)
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
                clearCcSlewState()
                sequencerEngine.reset()
                repeaterEngine.reset()
                syncRptrUiState()
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
                val stopResult = repeaterEngine.onTransportStop(currentTick = 0L)
                sendRepeaterMidi(stopResult.midi)
                syncRptrUiState()
                sendAndClearPendingNoteOffs()
                clearCcSlewState()
                clockEngine.stop()
                midiEngine.sendStop()
            }

            TransportState.Stopped -> Unit
        }
    }

    fun pause() {
        if (clockEngine.getTransportState() == TransportState.Playing) {
            val pauseResult = repeaterEngine.onTransportPause(currentTick = 0L)
            sendRepeaterMidi(pauseResult.midi)
            syncRptrUiState()
            sendAndClearPendingNoteOffs()
            clockEngine.pause()
        }
    }

    fun updateSequencerConfig(config: StochasticSequencerConfig) {
        sequencerConfig = config.sanitized()
    }

    fun pressRptr(division: RptrDivision, config: StochasticSequencerConfig) {
        val rptrConfig = RptrConfig(
            baseUnits = config.rptrBaseUnits,
            startMode = config.rptrStartMode
        )
        repeaterEngine.press(
            division = division,
            config = rptrConfig,
            currentTick = latestClockTick
        )
        syncRptrUiState()
    }

    fun releaseRptr() {
        val releaseResult = repeaterEngine.release(currentTick = latestClockTick)
        sendRepeaterMidi(releaseResult.midi)
        syncRptrUiState()
    }

    private fun isSequencerStepTick(tick: Long): Boolean = ((tick - 1L) % TICKS_PER_STEP) == 0L

    private fun processDueNoteOffs(currentTick: Long) {
        val iterator = scheduledNoteOffs.iterator()
        while (iterator.hasNext()) {
            val scheduled = iterator.next()
            if (scheduled.dueTick == currentTick) {
                val routeResult = repeaterEngine.onLiveNoteOff(
                    MidiNoteOffEvent(
                        tick = currentTick,
                        channel = scheduled.channel,
                        note = scheduled.note
                    )
                )
                sendRepeaterMidi(routeResult.extraMidi)
                if (routeResult.passThrough) {
                    midiEngine.sendNoteOff(
                        channel = scheduled.channel,
                        note = scheduled.note
                    )
                }
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

                val routeResult = repeaterEngine.onLiveNoteOn(
                    MidiNoteOnEvent(
                        tick = currentTick,
                        channel = sequencerConfig.midiChannel,
                        note = note,
                        velocity = 100
                    )
                )
                sendRepeaterMidi(routeResult.extraMidi)
                if (routeResult.passThrough) {
                    midiEngine.sendNoteOn(
                        channel = sequencerConfig.midiChannel,
                        note = note,
                        velocity = 100
                    )
                    scheduledNoteOffs += ScheduledNoteOff(
                        dueTick = currentTick + output.gateLengthTicks,
                        channel = sequencerConfig.midiChannel,
                        note = note
                    )
                }
            }

            MidiOutputMode.CC -> {
                val ccValue = output.ccValue ?: return
                if (!output.gate || !output.trigger) return

                val slewTicks = (sequencerConfig.slewAmount * TICKS_PER_STEP.toFloat())
                    .roundToInt()
                    .coerceIn(0, TICKS_PER_STEP.toInt())
                if (slewTicks <= 0) {
                    activeCcSlew = null
                    sendControlChangeIfNeeded(ccValue)
                    return
                }

                val startValue = lastSentCcValue ?: ccValue
                if (startValue == ccValue) {
                    activeCcSlew = null
                    sendControlChangeIfNeeded(ccValue)
                } else {
                    activeCcSlew = ActiveCcSlew(
                        startValue = startValue,
                        targetValue = ccValue,
                        durationTicks = slewTicks,
                        elapsedTicks = 0
                    )
                }
            }
        }
    }

    private fun processCcSlewTick() {
        val slew = activeCcSlew ?: return
        val nextElapsed = (slew.elapsedTicks + 1).coerceAtMost(slew.durationTicks)
        val progress = nextElapsed.toFloat() / slew.durationTicks.toFloat()
        val interpolated = slew.startValue + (slew.targetValue - slew.startValue) * progress
        val nextValue = if (nextElapsed >= slew.durationTicks) {
            slew.targetValue
        } else {
            interpolated.roundToInt().coerceIn(MIDI_MIN, MIDI_MAX)
        }

        sendControlChangeIfNeeded(nextValue)

        activeCcSlew = if (nextElapsed >= slew.durationTicks) {
            null
        } else {
            slew.copy(elapsedTicks = nextElapsed)
        }
    }

    private fun sendControlChangeIfNeeded(value: Int) {
        val ccValue = value.coerceIn(MIDI_MIN, MIDI_MAX)
        if (lastSentCcValue == ccValue) return

        midiEngine.sendControlChange(
            channel = sequencerConfig.midiChannel,
            controller = sequencerConfig.ccNumber,
            value = ccValue
        )
        lastSentCcValue = ccValue
    }

    private fun clearCcSlewState() {
        activeCcSlew = null
    }

    private fun sendRepeaterMidi(messages: List<RptrMidiOut>) {
        messages.forEach { message ->
            when (message) {
                is RptrMidiOut.NoteOn -> midiEngine.sendNoteOn(
                    channel = message.channel,
                    note = message.note,
                    velocity = message.velocity
                )

                is RptrMidiOut.NoteOff -> midiEngine.sendNoteOff(
                    channel = message.channel,
                    note = message.note
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

    private fun syncRptrUiState() {
        val (rptrState, activeRptrDivision) = when (val state = repeaterEngine.getState()) {
            is RptrState.Idle -> RptrUiRuntimeState.Idle to null
            is RptrState.Wait -> RptrUiRuntimeState.Wait to state.division
            is RptrState.Record -> RptrUiRuntimeState.Record to state.division
            is RptrState.Loop -> RptrUiRuntimeState.Loop to state.division
            is RptrState.Release -> RptrUiRuntimeState.Idle to null
        }
        _uiState.value = _uiState.value.copy(
            rptrState = rptrState,
            activeRptrDivision = activeRptrDivision
        )
    }

    override fun onCleared() {
        val stopResult = repeaterEngine.onTransportStop(currentTick = 0L)
        sendRepeaterMidi(stopResult.midi)
        syncRptrUiState()
        sendAndClearPendingNoteOffs()
        midiEngine.stopDeviceMonitoring()
        vmScope.cancel()
        super.onCleared()
    }

    companion object {
        private const val TICKS_PER_STEP = 24L
        private const val MIDI_MIN = 0
        private const val MIDI_MAX = 127

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

    private data class ActiveCcSlew(
        val startValue: Int,
        val targetValue: Int,
        val durationTicks: Int,
        val elapsedTicks: Int
    )

    private data class ScheduledNoteOff(
        val dueTick: Long,
        val channel: Int,
        val note: Int
    )
}
