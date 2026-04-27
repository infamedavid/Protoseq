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
import com.infamedavid.protoseq.features.grid616.GRID_616_GATE_TICKS
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_FINAL_DELAY_TICKS
import com.infamedavid.protoseq.features.grid616.GRID_616_TICKS_PER_STEP
import com.infamedavid.protoseq.features.grid616.Grid616SequencerConfig
import com.infamedavid.protoseq.features.grid616.resolveGrid616StepIndex
import com.infamedavid.protoseq.features.ginaarp.GinaArpPlayMode
import com.infamedavid.protoseq.features.ginaarp.GinaArpSequencerUiState
import com.infamedavid.protoseq.features.ginaarp.GinaArpStepState
import com.infamedavid.protoseq.features.ginaarp.generateGinaArpNote
import com.infamedavid.protoseq.features.ginaarp.normalized
import com.infamedavid.protoseq.features.stp116.Stp116SequencerConfig
import com.infamedavid.protoseq.features.stp116.Stp116StepConfig
import com.infamedavid.protoseq.features.stp116.STP_116_PITCH_BEND_CENTER
import com.infamedavid.protoseq.features.stp116.advanceStp116ClockDivider
import com.infamedavid.protoseq.features.stp116.calculateStp116FinalGateLengthTicks
import com.infamedavid.protoseq.features.stp116.calculateStp116FinalVelocity
import com.infamedavid.protoseq.features.stp116.resolveStp116StepIndex
import com.infamedavid.protoseq.features.stp116.stp116PitchBendValueFromCents
import com.infamedavid.protoseq.features.stp116.shouldDropStp116Gate
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerConfig
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class TransportViewModel(
    private val clockEngine: ClockEngine = ClockEngine(),
    private val midiEngine: MidiEngine = MidiEngine(
        messageSender = NoOpMidiMessageSender(),
        midiDeviceRepository = MidiDeviceRepository()
    )
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runtimeMutex = Mutex()
    private val activeTuringPageConfigs = mutableMapOf<Int, StochasticSequencerConfig>()
    private val activeGrid616PageConfigs = mutableMapOf<Int, Grid616SequencerConfig>()
    private val activeGinaArpPageConfigs = mutableMapOf<Int, GinaArpSequencerUiState>()
    private val activeStp116PageConfigs = mutableMapOf<Int, Stp116SequencerConfig>()
    private val pageRuntimes = mutableMapOf<Int, PageRuntime>()
    private val grid616PageRuntimes = mutableMapOf<Int, Grid616PageRuntime>()
    private val ginaArpPageRuntimes = mutableMapOf<Int, GinaArpPageRuntime>()
    private val stp116PageRuntimes = mutableMapOf<Int, Stp116PageRuntime>()
    private val activeMidiNotes = ActiveMidiNoteRegistry()
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
                runtimeMutex.withLock {
                    latestClockTick = tick
                    pageRuntimes.forEach { (pageIndex, runtime) ->
                        val repeaterTickResult = runtime.repeaterEngine.onTick(tick)
                        sendRepeaterMidi(pageIndex, repeaterTickResult.midi)
                        processDueNoteOffs(pageIndex, runtime, tick)
                        val config = activeTuringPageConfigs[pageIndex]
                        if (config != null && isSequencerStepTick(tick)) {
                            advanceSequencer(pageIndex, runtime, config, tick)
                        }
                        processCcSlewTick(runtime, config)
                    }
                    processGrid616TickLocked(tick)
                    processGinaArpTickLocked(tick)
                    processStp116TickLocked(tick)
                    syncRptrUiState()
                }
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
        vmScope.launch {
            runtimeMutex.withLock {
                when (clockEngine.getTransportState()) {
                    TransportState.Stopped -> {
                        pageRuntimes.values.forEach { runtime ->
                            sendAndClearPendingNoteOffs(runtime)
                            clearCcSlewState(runtime)
                            runtime.sequencerEngine.reset()
                            runtime.repeaterEngine.reset()
                        }
                        grid616PageRuntimes.values.forEach { runtime ->
                            clearGrid616ScheduledEvents(runtime)
                            runtime.globalStepCounter = 0L
                            runtime.lastProcessedStepTick = null
                        }
                        ginaArpPageRuntimes.values.forEach { runtime ->
                            resetGinaArpRuntimeForTransportStart(
                                runtime = runtime,
                                state = activeGinaArpPageConfigs[runtime.pageIndex]
                            )
                        }
                        stp116PageRuntimes.values.forEach { runtime ->
                            resetStp116RuntimeForTransportStart(
                                runtime = runtime,
                                config = activeStp116PageConfigs[runtime.pageIndex]
                            )
                        }
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
        }
    }

    fun stop() {
        vmScope.launch {
            runtimeMutex.withLock {
                when (clockEngine.getTransportState()) {
                    TransportState.Playing,
                    TransportState.Paused -> {
                        pageRuntimes.values.forEach { runtime ->
                            val stopResult = runtime.repeaterEngine.onTransportStop(currentTick = 0L)
                            sendRepeaterMidi(runtime.pageIndex, stopResult.midi)
                            sendAndClearPendingNoteOffs(runtime)
                            clearCcSlewState(runtime)
                        }
                        resetAllGrid616RuntimesLocked()
                        pauseAllGinaArpRuntimesLocked()
                        pauseAllStp116RuntimesLocked()
                        releaseAllNotesLocked()
                        syncRptrUiState()
                        clockEngine.stop()
                        midiEngine.sendStop()
                    }

                    TransportState.Stopped -> Unit
                }
            }
        }
    }

    fun pause() {
        vmScope.launch {
            runtimeMutex.withLock {
                if (clockEngine.getTransportState() == TransportState.Playing) {
                    pageRuntimes.values.forEach { runtime ->
                        val pauseResult = runtime.repeaterEngine.onTransportPause(currentTick = 0L)
                        sendRepeaterMidi(runtime.pageIndex, pauseResult.midi)
                        sendAndClearPendingNoteOffs(runtime)
                    }
                    pauseAllGrid616RuntimesLocked()
                    pauseAllGinaArpRuntimesLocked()
                    pauseAllStp116RuntimesLocked()
                    syncRptrUiState()
                    clockEngine.pause()
                }
            }
        }
    }

    fun updateTuringPageConfigs(configs: List<TuringPageConfig>) {
        vmScope.launch {
            runtimeMutex.withLock {
                val nextConfigsByPage = configs.associate { it.pageIndex to it.config.sanitized() }

                nextConfigsByPage.keys.forEach { pageIndex ->
                    pageRuntimes.getOrPut(pageIndex) { PageRuntime(pageIndex = pageIndex) }
                }

                val removedPageIndexes = activeTuringPageConfigs.keys - nextConfigsByPage.keys
                removedPageIndexes.forEach { pageIndex ->
                    clearPageRuntimeLocked(pageIndex)
                }

                activeTuringPageConfigs.clear()
                activeTuringPageConfigs.putAll(nextConfigsByPage)
                syncRptrUiState()
            }
        }
    }

    fun deactivatePageRuntime(pageIndex: Int) {
        vmScope.launch {
            runtimeMutex.withLock {
                activeTuringPageConfigs.remove(pageIndex)
                activeGrid616PageConfigs.remove(pageIndex)
                activeGinaArpPageConfigs.remove(pageIndex)
                activeStp116PageConfigs.remove(pageIndex)
                clearPageRuntimeLocked(pageIndex)
                clearGrid616PageRuntimeLocked(pageIndex)
                clearGinaArpPageRuntimeLocked(pageIndex)
                clearStp116PageRuntimeLocked(pageIndex)
                syncRptrUiState()
            }
        }
    }

    fun updateGrid616PageConfigs(configs: List<Grid616PageConfig>) {
        vmScope.launch {
            runtimeMutex.withLock {
                val nextConfigsByPage = configs.associate { it.pageIndex to it.config }
                val removedPageIndexes = activeGrid616PageConfigs.keys - nextConfigsByPage.keys
                removedPageIndexes.forEach { pageIndex ->
                    activeGrid616PageConfigs.remove(pageIndex)
                    clearGrid616PageRuntimeLocked(pageIndex)
                }

                nextConfigsByPage.forEach { (pageIndex, nextConfig) ->
                    activeGrid616PageConfigs[pageIndex] = nextConfig
                    grid616PageRuntimes.getOrPut(pageIndex) { Grid616PageRuntime(pageIndex = pageIndex) }
                }
            }
        }
    }

    fun updateGinaArpPageConfigs(configs: List<GinaArpPageConfig>) {
        vmScope.launch {
            runtimeMutex.withLock {
                val nextConfigsByPage = configs.associate { it.pageIndex to it.state.normalized() }
                val removedPageIndexes = activeGinaArpPageConfigs.keys - nextConfigsByPage.keys
                removedPageIndexes.forEach { pageIndex ->
                    activeGinaArpPageConfigs.remove(pageIndex)
                    clearGinaArpPageRuntimeLocked(pageIndex)
                }

                nextConfigsByPage.forEach { (pageIndex, nextState) ->
                    activeGinaArpPageConfigs[pageIndex] = nextState
                    val runtime = ginaArpPageRuntimes.getOrPut(pageIndex) { GinaArpPageRuntime(pageIndex = pageIndex) }
                    clampGinaArpRuntimePosition(runtime, nextState)
                }
            }
        }
    }

    fun updateStp116PageConfigs(configs: List<Stp116PageConfig>) {
        vmScope.launch {
            runtimeMutex.withLock {
                val nextConfigsByPage = configs.associate { it.pageIndex to it.config }
                val removedPageIndexes = activeStp116PageConfigs.keys - nextConfigsByPage.keys

                removedPageIndexes.forEach { pageIndex ->
                    activeStp116PageConfigs.remove(pageIndex)
                    clearStp116PageRuntimeLocked(pageIndex)
                }

                nextConfigsByPage.forEach { (pageIndex, nextConfig) ->
                    activeStp116PageConfigs[pageIndex] = nextConfig
                    val runtime = stp116PageRuntimes.getOrPut(pageIndex) {
                        Stp116PageRuntime(
                            pageIndex = pageIndex,
                            dividerCounter = initialStp116DividerCounter(nextConfig.clockDivider)
                        )
                    }
                    clampStp116RuntimeLocked(runtime, nextConfig)
                }
            }
        }
    }

    fun prepareGrid616PageForPatternReplace(pageIndex: Int) {
        vmScope.launch {
            runtimeMutex.withLock {
                prepareGrid616PageForPatternReplaceLocked(pageIndex)
            }
        }
    }

    fun pressRptr(pageIndex: Int, division: RptrDivision, config: StochasticSequencerConfig) {
        vmScope.launch {
            runtimeMutex.withLock {
                if (activeTuringPageConfigs[pageIndex] == null) return@withLock
                val runtime = pageRuntimes.getOrPut(pageIndex) { PageRuntime(pageIndex = pageIndex) }
                if (isRptrBusy(runtime)) return@withLock

                val rptrConfig = RptrConfig(
                    baseUnits = config.rptrBaseUnits,
                    startMode = config.rptrStartMode
                )
                runtime.repeaterEngine.press(
                    division = division,
                    config = rptrConfig,
                    currentTick = latestClockTick
                )
                syncRptrUiState()
            }
        }
    }

    fun releaseRptr(pageIndex: Int) {
        vmScope.launch {
            runtimeMutex.withLock {
                val runtime = pageRuntimes[pageIndex] ?: return@withLock
                val releaseResult = runtime.repeaterEngine.release(currentTick = latestClockTick)
                sendRepeaterMidi(pageIndex, releaseResult.midi)
                syncRptrUiState()
            }
        }
    }

    private fun isRptrBusy(runtime: PageRuntime): Boolean = when (runtime.repeaterEngine.getState()) {
        is RptrState.Wait,
        is RptrState.Record,
        is RptrState.Loop -> true

        is RptrState.Idle,
        is RptrState.Release -> false
    }

    private fun isSequencerStepTick(tick: Long): Boolean = ((tick - 1L) % TICKS_PER_STEP) == 0L

    private fun processDueNoteOffs(pageIndex: Int, runtime: PageRuntime, currentTick: Long) {
        val iterator = runtime.scheduledNoteOffs.iterator()
        while (iterator.hasNext()) {
            val scheduled = iterator.next()
            if (scheduled.dueTick == currentTick) {
                val routeResult = runtime.repeaterEngine.onLiveNoteOff(
                    MidiNoteOffEvent(
                        tick = currentTick,
                        channel = scheduled.channel,
                        note = scheduled.note
                    )
                )
                sendRepeaterMidi(pageIndex, routeResult.extraMidi)
                if (routeResult.passThrough) {
                    releasePageNoteLocked(pageIndex, scheduled.channel, scheduled.note)
                }
                iterator.remove()
            }
        }
    }

    private fun processGrid616TickLocked(currentTick: Long) {
        grid616PageRuntimes.forEach { (pageIndex, runtime) ->
            val config = activeGrid616PageConfigs[pageIndex] ?: return@forEach
            processDueGrid616NoteOffsLocked(pageIndex, runtime, currentTick)
            processDueGrid616TriggersLocked(pageIndex, runtime, currentTick)

            if (!isGrid616StepTick(currentTick) || runtime.lastProcessedStepTick == currentTick) return@forEach

            runtime.lastProcessedStepTick = currentTick
            val isSwungStep = runtime.globalStepCounter % 2L == 1L
            val swingDepth = (config.swingAmount / GRID_616_MAX_SWING_AMOUNT).coerceIn(0f, 1f)

            config.tracks.forEachIndexed { trackIndex, track ->
                if (track.muted) return@forEachIndexed
                val stepIndex = resolveGrid616StepIndex(
                    globalStepCounter = runtime.globalStepCounter,
                    length = track.length,
                    playbackMode = track.playbackMode,
                    randomIndexProvider = { length -> Random.nextInt(until = length) }
                )
                val step = track.steps.getOrNull(stepIndex) ?: return@forEachIndexed
                if (!step.enabled) return@forEachIndexed

                val trackMaxSwingTicks = GRID_616_TRACK_MAX_SWING_DELAY_TICKS
                    .getOrElse(trackIndex) { GRID_616_TRACK_MAX_SWING_DELAY_TICKS.last() }
                val swingDelayTicks = if (isSwungStep) {
                    (trackMaxSwingTicks * swingDepth).roundToInt()
                } else {
                    0
                }
                val finalDelayTicks = (swingDelayTicks + step.delayTicks)
                    .coerceIn(0, GRID_616_MAX_FINAL_DELAY_TICKS)
                val dueTick = currentTick + finalDelayTicks
                val trigger = Grid616ScheduledTrigger(
                    dueTick = dueTick,
                    channel = config.midiChannel,
                    note = track.note,
                    velocity = step.velocity
                )
                if (dueTick == currentTick) {
                    fireGrid616TriggerLocked(pageIndex, runtime, trigger)
                } else {
                    runtime.scheduledTriggers += trigger
                }
            }

            runtime.globalStepCounter += 1L
        }
    }

    private fun processDueGrid616TriggersLocked(
        pageIndex: Int,
        runtime: Grid616PageRuntime,
        currentTick: Long
    ) {
        val iterator = runtime.scheduledTriggers.iterator()
        while (iterator.hasNext()) {
            val scheduled = iterator.next()
            if (scheduled.dueTick <= currentTick) {
                fireGrid616TriggerLocked(pageIndex, runtime, scheduled)
                iterator.remove()
            }
        }
    }

    private fun fireGrid616TriggerLocked(
        pageIndex: Int,
        runtime: Grid616PageRuntime,
        trigger: Grid616ScheduledTrigger
    ) {
        sendPageNoteOnLocked(
            pageIndex = pageIndex,
            channel = trigger.channel,
            note = trigger.note,
            velocity = trigger.velocity
        )
        runtime.scheduledNoteOffs += Grid616ScheduledNoteOff(
            dueTick = trigger.dueTick + GRID_616_GATE_TICKS,
            channel = trigger.channel,
            note = trigger.note
        )
    }

    private fun processDueGrid616NoteOffsLocked(
        pageIndex: Int,
        runtime: Grid616PageRuntime,
        currentTick: Long
    ) {
        val iterator = runtime.scheduledNoteOffs.iterator()
        while (iterator.hasNext()) {
            val scheduled = iterator.next()
            if (scheduled.dueTick <= currentTick) {
                releasePageNoteLocked(pageIndex, scheduled.channel, scheduled.note)
                iterator.remove()
            }
        }
    }

    private fun processGinaArpTickLocked(currentTick: Long) {
        ginaArpPageRuntimes.forEach { (pageIndex, runtime) ->
            val state = activeGinaArpPageConfigs[pageIndex] ?: return@forEach
            clampGinaArpRuntimePosition(runtime, state)
            processDueGinaArpNoteOffsLocked(pageIndex, runtime, currentTick)
            if (currentTick < runtime.nextGateTick) return@forEach

            val step = state.steps[runtime.currentStepIndex]
            val shouldPassBernoulli = when {
                state.bernoulliGate <= 0f -> false
                state.bernoulliGate >= 1f -> true
                else -> runtime.random.nextFloat() < state.bernoulliGate
            }

            if (shouldPassBernoulli) {
                val generated = generateGinaArpNote(
                    state = state,
                    stepIndex = runtime.currentStepIndex,
                    divisionIndex = runtime.currentDivisionIndex,
                    random = runtime.random
                )
                if (generated != null) {
                    val channel = state.midiChannel.coerceIn(1, 16)
                    sendPageNoteOnLocked(
                        pageIndex = pageIndex,
                        channel = channel,
                        note = generated.midiNote,
                        velocity = generated.velocity
                    )
                    val gateTicks = calculateGinaArpGateTicks(state, runtime)
                    runtime.scheduledNoteOffs += GinaArpScheduledNoteOff(
                        dueTick = currentTick + gateTicks,
                        channel = channel,
                        note = generated.midiNote
                    )
                }
            }

            advanceGinaArpPosition(runtime, state, step)
            val ticksPerGeneratedGate = (TICKS_PER_STEP * state.tempoDivisor.toLong()).coerceAtLeast(1L)
            runtime.nextGateTick = currentTick + ticksPerGeneratedGate
        }
    }

    private fun processDueGinaArpNoteOffsLocked(
        pageIndex: Int,
        runtime: GinaArpPageRuntime,
        currentTick: Long
    ) {
        val iterator = runtime.scheduledNoteOffs.iterator()
        while (iterator.hasNext()) {
            val scheduled = iterator.next()
            if (scheduled.dueTick <= currentTick) {
                releasePageNoteLocked(pageIndex, scheduled.channel, scheduled.note)
                iterator.remove()
            }
        }
    }

    private fun processStp116TickLocked(currentTick: Long) {
        stp116PageRuntimes.forEach { (pageIndex, runtime) ->
            val config = activeStp116PageConfigs[pageIndex] ?: return@forEach

            processDueStp116NoteOffsLocked(pageIndex, runtime, currentTick)
            processDueStp116TriggersLocked(pageIndex, runtime, currentTick)

            if (!isSequencerStepTick(currentTick) || runtime.lastProcessedStepTick == currentTick) {
                return@forEach
            }

            runtime.lastProcessedStepTick = currentTick

            val dividerResult = advanceStp116ClockDivider(
                currentCounter = runtime.dividerCounter,
                clockDivider = config.clockDivider
            )
            runtime.dividerCounter = dividerResult.nextCounter

            if (!dividerResult.shouldAdvance) {
                return@forEach
            }

            val stepIndex = resolveStp116StepIndex(
                globalStepCounter = runtime.globalStepCounter,
                length = config.sequenceLength,
                playbackMode = config.playbackMode,
                randomIndexProvider = { length -> runtime.random.nextInt(until = length) }
            )

            runtime.globalStepCounter += 1L

            val step = config.steps.getOrNull(stepIndex) ?: return@forEach
            if (!step.enabled) {
                return@forEach
            }

            val shouldDrop = shouldDropStp116Gate(
                bernoulliDropChance = config.bernoulliDropChance,
                randomFloatProvider = { runtime.random.nextFloat() }
            )
            if (shouldDrop) {
                return@forEach
            }

            val finalVelocity = calculateStp116FinalVelocity(
                velocity = step.velocity,
                randomVelocityAmount = config.randomVelocityAmount,
                randomIntProvider = { minInclusive, maxInclusive ->
                    runtime.random.nextInt(minInclusive, maxInclusive + 1)
                }
            )

            val finalGateLengthTicks = calculateStp116FinalGateLengthTicks(
                gateLengthTicks = step.gateLengthTicks,
                gateDelayTicks = step.gateDelayTicks,
                randomGateLengthAmount = config.randomGateLengthAmount,
                randomIntProvider = { minInclusive, maxInclusive ->
                    runtime.random.nextInt(minInclusive, maxInclusive + 1)
                }
            )

            scheduleStp116StepTriggerLocked(
                runtime = runtime,
                step = step,
                currentTick = currentTick,
                config = config,
                finalVelocity = finalVelocity,
                finalGateLengthTicks = finalGateLengthTicks
            )

            processDueStp116TriggersLocked(pageIndex, runtime, currentTick)
        }
    }

    private fun sendStp116PitchBendLocked(channel: Int, value: Int) {
        midiEngine.sendPitchBend(
            channel = channel,
            value = value.coerceIn(0, 16383)
        )
    }

    private fun registerStp116BentNoteLocked(
        runtime: Stp116PageRuntime,
        channel: Int,
        pitchBendValue: Int,
    ) {
        if (pitchBendValue == STP_116_PITCH_BEND_CENTER) return

        val safeChannel = channel.coerceIn(1, 16)
        val nextCount = (runtime.activeBentNoteCountsByChannel[safeChannel] ?: 0) + 1
        runtime.activeBentNoteCountsByChannel[safeChannel] = nextCount
    }

    private fun releaseStp116BentNoteLocked(
        runtime: Stp116PageRuntime,
        channel: Int,
        pitchBendValue: Int,
    ) {
        if (pitchBendValue == STP_116_PITCH_BEND_CENTER) return

        val safeChannel = channel.coerceIn(1, 16)
        val currentCount = runtime.activeBentNoteCountsByChannel[safeChannel] ?: 0
        val nextCount = (currentCount - 1).coerceAtLeast(0)

        if (nextCount == 0) {
            runtime.activeBentNoteCountsByChannel.remove(safeChannel)
            sendStp116PitchBendLocked(
                channel = safeChannel,
                value = STP_116_PITCH_BEND_CENTER
            )
        } else {
            runtime.activeBentNoteCountsByChannel[safeChannel] = nextCount
        }
    }

    private fun resetStp116PitchBendsLocked(runtime: Stp116PageRuntime) {
        runtime.activeBentNoteCountsByChannel.keys.toList().forEach { channel ->
            sendStp116PitchBendLocked(
                channel = channel,
                value = STP_116_PITCH_BEND_CENTER
            )
        }
        runtime.activeBentNoteCountsByChannel.clear()
    }

    private fun scheduleStp116StepTriggerLocked(
        runtime: Stp116PageRuntime,
        step: Stp116StepConfig,
        currentTick: Long,
        config: Stp116SequencerConfig,
        finalVelocity: Int,
        finalGateLengthTicks: Long
    ) {
        val triggerDueTick = currentTick + step.gateDelayTicks.toLong()
        val pitchBendValue = stp116PitchBendValueFromCents(step.fineTuneCents)

        runtime.scheduledTriggers += Stp116ScheduledTrigger(
            dueTick = triggerDueTick,
            channel = config.midiChannel,
            note = step.midiNote,
            velocity = finalVelocity,
            pitchBendValue = pitchBendValue,
            gateLengthTicks = finalGateLengthTicks,
        )
    }

    private fun processDueStp116TriggersLocked(
        pageIndex: Int,
        runtime: Stp116PageRuntime,
        currentTick: Long
    ) {
        val iterator = runtime.scheduledTriggers.iterator()
        while (iterator.hasNext()) {
            val trigger = iterator.next()
            if (trigger.dueTick <= currentTick) {
                sendStp116PitchBendLocked(
                    channel = trigger.channel,
                    value = trigger.pitchBendValue,
                )

                sendPageNoteOnLocked(
                    pageIndex = pageIndex,
                    channel = trigger.channel,
                    note = trigger.note,
                    velocity = trigger.velocity,
                )

                registerStp116BentNoteLocked(
                    runtime = runtime,
                    channel = trigger.channel,
                    pitchBendValue = trigger.pitchBendValue,
                )

                runtime.scheduledNoteOffs += Stp116ScheduledNoteOff(
                    dueTick = currentTick + trigger.gateLengthTicks,
                    channel = trigger.channel,
                    note = trigger.note,
                    pitchBendValue = trigger.pitchBendValue,
                )

                iterator.remove()
            }
        }
    }

    private fun processDueStp116NoteOffsLocked(
        pageIndex: Int,
        runtime: Stp116PageRuntime,
        currentTick: Long
    ) {
        val iterator = runtime.scheduledNoteOffs.iterator()
        while (iterator.hasNext()) {
            val scheduled = iterator.next()
            if (scheduled.dueTick <= currentTick) {
                releasePageNoteLocked(pageIndex, scheduled.channel, scheduled.note)

                releaseStp116BentNoteLocked(
                    runtime = runtime,
                    channel = scheduled.channel,
                    pitchBendValue = scheduled.pitchBendValue,
                )

                iterator.remove()
            }
        }
    }

    private fun calculateGinaArpGateTicks(
        state: GinaArpSequencerUiState,
        runtime: GinaArpPageRuntime
    ): Long {
        val ticksPerGeneratedGate = (TICKS_PER_STEP * state.tempoDivisor.toLong()).coerceAtLeast(1L)
        val baseGateTicks = max(1, (ticksPerGeneratedGate.toFloat() * state.gateLength).roundToInt())
        val maxExtraTicks = if (state.randomGateLength <= 0f) {
            0
        } else {
            (ticksPerGeneratedGate.toFloat() * state.randomGateLength).roundToInt().coerceAtLeast(0)
        }
        val randomExtraTicks = if (maxExtraTicks <= 0) 0 else runtime.random.nextInt(maxExtraTicks + 1)
        val unclamped = baseGateTicks + randomExtraTicks
        val maxGateTicks = (ticksPerGeneratedGate - 1L).coerceAtLeast(1L).toInt()
        return unclamped.coerceIn(1, maxGateTicks).toLong()
    }

    private fun advanceGinaArpPosition(
        runtime: GinaArpPageRuntime,
        state: GinaArpSequencerUiState,
        currentStep: GinaArpStepState
    ) {
        val divisions = currentStep.divisions.coerceAtLeast(1)
        runtime.currentDivisionIndex += 1
        if (runtime.currentDivisionIndex < divisions) {
            return
        }
        runtime.currentDivisionIndex = 0
        val (nextStepIndex, nextDirection) = nextGinaArpStepIndex(
            currentStepIndex = runtime.currentStepIndex,
            sequenceLength = state.sequenceLength,
            playMode = state.playMode,
            direction = runtime.direction,
            random = runtime.random
        )
        runtime.currentStepIndex = nextStepIndex
        runtime.direction = nextDirection
    }

    private fun nextGinaArpStepIndex(
        currentStepIndex: Int,
        sequenceLength: Int,
        playMode: GinaArpPlayMode,
        direction: Int,
        random: Random
    ): Pair<Int, Int> {
        val length = sequenceLength.coerceAtLeast(1)
        if (length <= 1) return 0 to 1
        return when (playMode) {
            GinaArpPlayMode.FORWARD -> ((currentStepIndex + 1) % length) to 1
            GinaArpPlayMode.REVERSE -> ((currentStepIndex - 1 + length) % length) to -1
            GinaArpPlayMode.RANDOM -> random.nextInt(until = length) to 1
            GinaArpPlayMode.PING_PONG -> {
                val currentDirection = if (direction >= 0) 1 else -1
                var nextIndex = currentStepIndex + currentDirection
                var nextDirection = currentDirection
                if (nextIndex >= length) {
                    nextDirection = -1
                    nextIndex = (length - 2).coerceAtLeast(0)
                } else if (nextIndex < 0) {
                    nextDirection = 1
                    nextIndex = 1.coerceAtMost(length - 1)
                }
                nextIndex to nextDirection
            }
        }
    }

    private fun clampGinaArpRuntimePosition(runtime: GinaArpPageRuntime, state: GinaArpSequencerUiState) {
        val length = state.sequenceLength.coerceAtLeast(1)
        runtime.currentStepIndex = runtime.currentStepIndex.coerceIn(0, length - 1)
        val stepDivisions = state.steps
            .getOrNull(runtime.currentStepIndex)
            ?.divisions
            ?.coerceAtLeast(1)
            ?: 1
        runtime.currentDivisionIndex = runtime.currentDivisionIndex.coerceIn(0, stepDivisions - 1)
        runtime.direction = if (runtime.direction >= 0) 1 else -1
    }

    private fun advanceSequencer(
        pageIndex: Int,
        runtime: PageRuntime,
        config: StochasticSequencerConfig,
        currentTick: Long
    ) {
        val output = runtime.sequencerEngine.advance(config)
        when (config.outputMode) {
            MidiOutputMode.NOTE -> {
                val note = output.note ?: return
                if (!output.gate || !output.trigger) return

                val routeResult = runtime.repeaterEngine.onLiveNoteOn(
                    MidiNoteOnEvent(
                        tick = currentTick,
                        channel = config.midiChannel,
                        note = note,
                        velocity = 100
                    )
                )
                sendRepeaterMidi(pageIndex, routeResult.extraMidi)
                if (routeResult.passThrough) {
                    sendPageNoteOnLocked(
                        pageIndex = pageIndex,
                        channel = config.midiChannel,
                        note = note,
                        velocity = 100
                    )
                    runtime.scheduledNoteOffs += ScheduledNoteOff(
                        dueTick = currentTick + output.gateLengthTicks,
                        channel = config.midiChannel,
                        note = note
                    )
                }
            }

            MidiOutputMode.CC -> {
                val ccValue = output.ccValue ?: return
                if (!output.gate || !output.trigger) return

                val slewTicks = (config.slewAmount * TICKS_PER_STEP.toFloat())
                    .roundToInt()
                    .coerceIn(0, TICKS_PER_STEP.toInt())
                if (slewTicks <= 0) {
                    runtime.activeCcSlew = null
                    sendControlChangeIfNeeded(runtime, config, ccValue)
                    return
                }

                val startValue = runtime.lastSentCcValue ?: ccValue
                if (startValue == ccValue) {
                    runtime.activeCcSlew = null
                    sendControlChangeIfNeeded(runtime, config, ccValue)
                } else {
                    runtime.activeCcSlew = ActiveCcSlew(
                        startValue = startValue,
                        targetValue = ccValue,
                        durationTicks = slewTicks,
                        elapsedTicks = 0
                    )
                }
            }
        }
    }

    private fun processCcSlewTick(runtime: PageRuntime, config: StochasticSequencerConfig?) {
        val activeConfig = config ?: return
        val slew = runtime.activeCcSlew ?: return
        val nextElapsed = (slew.elapsedTicks + 1).coerceAtMost(slew.durationTicks)
        val progress = nextElapsed.toFloat() / slew.durationTicks.toFloat()
        val interpolated = slew.startValue + (slew.targetValue - slew.startValue) * progress
        val nextValue = if (nextElapsed >= slew.durationTicks) {
            slew.targetValue
        } else {
            interpolated.roundToInt().coerceIn(MIDI_MIN, MIDI_MAX)
        }

        sendControlChangeIfNeeded(runtime, activeConfig, nextValue)

        runtime.activeCcSlew = if (nextElapsed >= slew.durationTicks) {
            null
        } else {
            slew.copy(elapsedTicks = nextElapsed)
        }
    }

    private fun sendControlChangeIfNeeded(
        runtime: PageRuntime,
        config: StochasticSequencerConfig,
        value: Int
    ) {
        val ccValue = value.coerceIn(MIDI_MIN, MIDI_MAX)
        if (runtime.lastSentCcValue == ccValue) return

        midiEngine.sendControlChange(
            channel = config.midiChannel,
            controller = config.ccNumber,
            value = ccValue
        )
        runtime.lastSentCcValue = ccValue
    }

    private fun clearCcSlewState(runtime: PageRuntime) {
        runtime.activeCcSlew = null
        runtime.lastSentCcValue = null
    }

    private fun sendPageNoteOnLocked(pageIndex: Int, channel: Int, note: Int, velocity: Int) {
        midiEngine.sendNoteOn(channel = channel, note = note, velocity = velocity)
        activeMidiNotes.registerNoteOn(pageIndex = pageIndex, channel = channel, note = note)
    }

    private fun releasePageNoteLocked(pageIndex: Int, channel: Int, note: Int) {
        if (activeMidiNotes.releaseNote(pageIndex = pageIndex, channel = channel, note = note)) {
            midiEngine.sendNoteOff(channel = channel, note = note)
        }
    }

    private fun releasePageNotesLocked(pageIndex: Int) {
        activeMidiNotes.releasePage(pageIndex).forEach { key ->
            midiEngine.sendNoteOff(channel = key.channel, note = key.note)
        }
    }

    private fun releaseAllNotesLocked() {
        activeMidiNotes.releaseAll().forEach { key ->
            midiEngine.sendNoteOff(channel = key.channel, note = key.note)
        }
    }

    private fun sendRepeaterMidi(pageIndex: Int, messages: List<RptrMidiOut>) {
        messages.forEach { message ->
            when (message) {
                is RptrMidiOut.NoteOn -> sendPageNoteOnLocked(
                    pageIndex = pageIndex,
                    channel = message.channel,
                    note = message.note,
                    velocity = message.velocity
                )

                is RptrMidiOut.NoteOff -> releasePageNoteLocked(
                    pageIndex = pageIndex,
                    channel = message.channel,
                    note = message.note
                )
            }
        }
    }

    private fun sendAndClearPendingNoteOffs(runtime: PageRuntime) {
        runtime.scheduledNoteOffs.forEach { scheduled ->
            releasePageNoteLocked(runtime.pageIndex, scheduled.channel, scheduled.note)
        }
        runtime.scheduledNoteOffs.clear()
    }

    private fun clearPageRuntimeLocked(pageIndex: Int) {
        pageRuntimes.remove(pageIndex)?.let { runtime ->
            val stopResult = runtime.repeaterEngine.onTransportStop(currentTick = latestClockTick)
            sendRepeaterMidi(pageIndex, stopResult.midi)
            sendAndClearPendingNoteOffs(runtime)
            releasePageNotesLocked(pageIndex)
            clearCcSlewState(runtime)
        }
    }

    private fun isGrid616StepTick(tick: Long): Boolean = ((tick - 1L) % GRID_616_TICKS_PER_STEP.toLong()) == 0L

    private fun clearGrid616ScheduledEvents(runtime: Grid616PageRuntime) {
        runtime.scheduledTriggers.clear()
        runtime.scheduledNoteOffs.clear()
    }

    private fun prepareGrid616PageForPatternReplaceLocked(pageIndex: Int) {
        val runtime = grid616PageRuntimes[pageIndex]
        if (runtime != null) {
            clearGrid616ScheduledEvents(runtime)
        }
        releasePageNotesLocked(pageIndex)
    }

    private fun clearGrid616PageRuntimeLocked(pageIndex: Int) {
        grid616PageRuntimes.remove(pageIndex)?.let { runtime ->
            clearGrid616ScheduledEvents(runtime)
            releasePageNotesLocked(pageIndex)
        }
    }

    private fun clearGinaArpScheduledEvents(runtime: GinaArpPageRuntime) {
        runtime.scheduledNoteOffs.clear()
    }

    private fun resetGinaArpRuntimeForTransportStart(
        runtime: GinaArpPageRuntime,
        state: GinaArpSequencerUiState?
    ) {
        val normalizedState = state?.normalized()
        val sequenceLength = normalizedState?.sequenceLength?.coerceAtLeast(1) ?: 1
        val reverseStart = normalizedState?.playMode == GinaArpPlayMode.REVERSE
        runtime.currentStepIndex = if (reverseStart) {
            sequenceLength - 1
        } else {
            0
        }
        runtime.direction = if (reverseStart) -1 else 1
        runtime.currentDivisionIndex = 0
        runtime.nextGateTick = 1L
        clearGinaArpScheduledEvents(runtime)
    }

    private fun clearGinaArpPageRuntimeLocked(pageIndex: Int) {
        ginaArpPageRuntimes.remove(pageIndex)?.let { runtime ->
            clearGinaArpScheduledEvents(runtime)
            releasePageNotesLocked(pageIndex)
        }
    }

    private fun clearStp116ScheduledEvents(runtime: Stp116PageRuntime) {
        runtime.scheduledTriggers.clear()
        runtime.scheduledNoteOffs.clear()
    }

    private fun clearStp116PageRuntimeLocked(pageIndex: Int) {
        stp116PageRuntimes.remove(pageIndex)?.let { runtime ->
            clearStp116ScheduledEvents(runtime)
            releasePageNotesLocked(pageIndex)
            resetStp116PitchBendsLocked(runtime)
        }
    }

    private fun resetStp116RuntimeForTransportStart(
        runtime: Stp116PageRuntime,
        config: Stp116SequencerConfig?
    ) {
        runtime.globalStepCounter = 0L
        runtime.lastProcessedStepTick = null
        clearStp116ScheduledEvents(runtime)
        resetStp116PitchBendsLocked(runtime)
        runtime.dividerCounter = initialStp116DividerCounter(config?.clockDivider ?: 1)
    }

    private fun resetAllGrid616RuntimesLocked() {
        grid616PageRuntimes.values.forEach { runtime ->
            clearGrid616ScheduledEvents(runtime)
            runtime.globalStepCounter = 0L
            runtime.lastProcessedStepTick = null
        }
    }

    private fun clearAllGrid616RuntimesLocked() {
        val pageIndexes = grid616PageRuntimes.keys.toList()
        pageIndexes.forEach(::clearGrid616PageRuntimeLocked)
    }

    private fun pauseAllGrid616RuntimesLocked() {
        grid616PageRuntimes.keys.toList().forEach { pageIndex ->
            val runtime = grid616PageRuntimes[pageIndex] ?: return@forEach
            clearGrid616ScheduledEvents(runtime)
            releasePageNotesLocked(pageIndex)
        }
    }

    private fun pauseAllGinaArpRuntimesLocked() {
        ginaArpPageRuntimes.keys.toList().forEach { pageIndex ->
            val runtime = ginaArpPageRuntimes[pageIndex] ?: return@forEach
            clearGinaArpScheduledEvents(runtime)
            releasePageNotesLocked(pageIndex)
        }
    }

    private fun pauseAllStp116RuntimesLocked() {
        stp116PageRuntimes.keys.toList().forEach { pageIndex ->
            val runtime = stp116PageRuntimes[pageIndex] ?: return@forEach
            clearStp116ScheduledEvents(runtime)
            releasePageNotesLocked(pageIndex)
            resetStp116PitchBendsLocked(runtime)
        }
    }

    private fun resetAllStp116RuntimesLocked() {
        stp116PageRuntimes.values.forEach { runtime ->
            clearStp116ScheduledEvents(runtime)
            runtime.globalStepCounter = 0L
            runtime.lastProcessedStepTick = null
            resetStp116PitchBendsLocked(runtime)
            val config = activeStp116PageConfigs[runtime.pageIndex]
            runtime.dividerCounter = initialStp116DividerCounter(config?.clockDivider ?: 1)
        }
    }

    private fun clearAllStp116RuntimesLocked() {
        stp116PageRuntimes.keys.toList().forEach(::clearStp116PageRuntimeLocked)
    }

    private fun clampStp116RuntimeLocked(
        runtime: Stp116PageRuntime,
        config: Stp116SequencerConfig
    ) {
        val safeDivider = config.clockDivider.coerceAtLeast(1)
        runtime.dividerCounter = runtime.dividerCounter.coerceIn(0, safeDivider - 1)
    }

    private fun initialStp116DividerCounter(clockDivider: Int): Int {
        val safeDivider = clockDivider.coerceAtLeast(1)
        return safeDivider - 1
    }

    private fun syncRptrUiState() {
        val statesByPage = mutableMapOf<Int, RptrUiRuntimeState>()
        val activeDivisionsByPage = mutableMapOf<Int, RptrDivision?>()

        pageRuntimes.forEach { (pageIndex, runtime) ->
            val (rptrState, activeRptrDivision) = when (val state = runtime.repeaterEngine.getState()) {
                is RptrState.Idle -> RptrUiRuntimeState.Idle to null
                is RptrState.Wait -> RptrUiRuntimeState.Wait to state.division
                is RptrState.Record -> RptrUiRuntimeState.Record to state.division
                is RptrState.Loop -> RptrUiRuntimeState.Loop to state.division
                is RptrState.Release -> RptrUiRuntimeState.Idle to null
            }
            statesByPage[pageIndex] = rptrState
            activeDivisionsByPage[pageIndex] = activeRptrDivision
        }

        _uiState.value = _uiState.value.copy(
            rptrStatesByPage = statesByPage,
            activeRptrDivisionsByPage = activeDivisionsByPage
        )
    }

    override fun onCleared() {
        runBlocking {
            runtimeMutex.withLock {
                pageRuntimes.values.forEach { runtime ->
                    val stopResult = runtime.repeaterEngine.onTransportStop(currentTick = 0L)
                    sendRepeaterMidi(runtime.pageIndex, stopResult.midi)
                    sendAndClearPendingNoteOffs(runtime)
                }
                clearAllGrid616RuntimesLocked()
                ginaArpPageRuntimes.keys.toList().forEach(::clearGinaArpPageRuntimeLocked)
                clearAllStp116RuntimesLocked()
                releaseAllNotesLocked()
                syncRptrUiState()
            }
        }
        midiEngine.stopDeviceMonitoring()
        vmScope.cancel()
        super.onCleared()
    }

    companion object {
        private const val TICKS_PER_STEP = 24L
        private const val MIDI_MIN = 0
        private const val MIDI_MAX = 127
        private const val GRID_616_MAX_SWING_AMOUNT = 0.75f
        private val GRID_616_TRACK_MAX_SWING_DELAY_TICKS = listOf(3, 2, 4, 3, 2, 4)

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

    private data class Grid616ScheduledTrigger(
        val dueTick: Long,
        val channel: Int,
        val note: Int,
        val velocity: Int
    )

    private data class Grid616ScheduledNoteOff(
        val dueTick: Long,
        val channel: Int,
        val note: Int
    )

    private data class Grid616PageRuntime(
        val pageIndex: Int,
        var lastProcessedStepTick: Long? = null,
        var globalStepCounter: Long = 0L,
        val scheduledTriggers: MutableList<Grid616ScheduledTrigger> = mutableListOf(),
        val scheduledNoteOffs: MutableList<Grid616ScheduledNoteOff> = mutableListOf()
    )

    private data class GinaArpScheduledNoteOff(
        val dueTick: Long,
        val channel: Int,
        val note: Int
    )

    private data class GinaArpPageRuntime(
        val pageIndex: Int,
        var currentStepIndex: Int = 0,
        var currentDivisionIndex: Int = 0,
        var direction: Int = 1,
        var nextGateTick: Long = 1L,
        val scheduledNoteOffs: MutableList<GinaArpScheduledNoteOff> = mutableListOf(),
        val random: Random = Random.Default
    )

    private data class Stp116ScheduledNoteOff(
        val dueTick: Long,
        val channel: Int,
        val note: Int,
        val pitchBendValue: Int,
    )

    private data class Stp116ScheduledTrigger(
        val dueTick: Long,
        val channel: Int,
        val note: Int,
        val velocity: Int,
        val pitchBendValue: Int,
        val gateLengthTicks: Long,
    )

    private data class Stp116PageRuntime(
        val pageIndex: Int,
        var lastProcessedStepTick: Long? = null,
        var globalStepCounter: Long = 0L,
        var dividerCounter: Int = 0,
        val scheduledTriggers: MutableList<Stp116ScheduledTrigger> = mutableListOf(),
        val scheduledNoteOffs: MutableList<Stp116ScheduledNoteOff> = mutableListOf(),
        val activeBentNoteCountsByChannel: MutableMap<Int, Int> = mutableMapOf(),
        val random: Random = Random.Default
    )

    private data class PageRuntime(
        val pageIndex: Int,
        val sequencerEngine: StochasticSequencerEngine = StochasticSequencerEngine(),
        val repeaterEngine: RepeaterEngine = RepeaterEngine(),
        val scheduledNoteOffs: MutableList<ScheduledNoteOff> = mutableListOf(),
        var activeCcSlew: ActiveCcSlew? = null,
        var lastSentCcValue: Int? = null
    )
}

data class TuringPageConfig(
    val pageIndex: Int,
    val config: StochasticSequencerConfig
)

data class Grid616PageConfig(
    val pageIndex: Int,
    val config: Grid616SequencerConfig
)

data class GinaArpPageConfig(
    val pageIndex: Int,
    val state: GinaArpSequencerUiState
)

data class Stp116PageConfig(
    val pageIndex: Int,
    val config: Stp116SequencerConfig
)
