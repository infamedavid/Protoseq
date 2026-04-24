package com.infamedavid.protoseq.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infamedavid.protoseq.R
import com.infamedavid.protoseq.core.music.QuantizationMode
import com.infamedavid.protoseq.features.sequencer.SequencerType
import com.infamedavid.protoseq.features.sequencer.createDefaultProtoseqSessionState
import com.infamedavid.protoseq.features.sequencer.currentPage
import com.infamedavid.protoseq.features.sequencer.selectPage
import com.infamedavid.protoseq.features.sequencer.updatePage
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerUiState
import com.infamedavid.protoseq.features.stochastic.toConfig
import com.infamedavid.protoseq.features.transport.RptrUiRuntimeState
import com.infamedavid.protoseq.features.transport.TuringPageConfig
import com.infamedavid.protoseq.features.transport.TransportViewModel
import com.infamedavid.protoseq.ui.components.ProtoButton
import com.infamedavid.protoseq.ui.components.ProtoControlShape
import com.infamedavid.protoseq.ui.sequencers.TuringMachinePanel
import com.infamedavid.protoseq.ui.util.buildMidiTargetShortLabels
import com.infamedavid.protoseq.ui.util.midiNoteToDisplay

@Composable
fun AppScreen(
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appVersion = remember(context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager
                    .getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                    .versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
            }
        }.getOrNull() ?: "?"
    }

    val transportViewModel: TransportViewModel = viewModel(
        factory = TransportViewModel.factory(context)
    )
    val transportState by transportViewModel.uiState.collectAsState()

    var showBpmInputDialog by rememberSaveable { mutableStateOf(false) }
    var bpmInputText by rememberSaveable { mutableStateOf("") }
    var showQuantizationDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showRptrBasePickerDialog by rememberSaveable { mutableStateOf(false) }
    var sessionState by remember {
        mutableStateOf(
            createDefaultProtoseqSessionState().updatePage(pageIndex = 0) { page ->
                page.copy(
                    selectedSequencerType = SequencerType.TURING_MACHINE,
                    enabled = true
                )
            }
        )
    }
    val currentPage = sessionState.currentPage()
    val currentTuringState = currentPage.turingState

    fun updateCurrentTuringState(
        transform: (StochasticSequencerUiState) -> StochasticSequencerUiState
    ) {
        sessionState = sessionState.updatePage(sessionState.selectedPageIndex) { page ->
            page.copy(turingState = transform(page.turingState))
        }
    }

    val turingPageConfigs = remember(sessionState.pages) {
        sessionState.pages
            .filter {
                it.selectedSequencerType == SequencerType.TURING_MACHINE && it.enabled
            }
            .map { page ->
                TuringPageConfig(
                    pageIndex = page.pageIndex,
                    config = page.turingState.toConfig()
                )
            }
    }

    LaunchedEffect(turingPageConfigs) {
        transportViewModel.updateTuringPageConfigs(turingPageConfigs)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = ((transportState.bpm - 1f) / 299f).coerceIn(0f, 1f),
                    onValueChange = { normalized ->
                        transportViewModel.setBpm(1f + (normalized * 299f))
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { transportViewModel.setBpm(transportState.bpm - 1f) },
                        shape = ProtoControlShape
                    ) {
                        Text(text = "-")
                    }

                    Text(
                        text = "${transportState.bpm.toInt()} BPM",
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                bpmInputText = transportState.bpm.toInt().toString()
                                showBpmInputDialog = true
                            }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedButton(
                        onClick = { transportViewModel.setBpm(transportState.bpm + 1f) },
                        shape = ProtoControlShape
                    ) {
                        Text(text = "+")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProtoButton(
                        label = "PLAY",
                        modifier = Modifier.weight(1f)
                    ) {
                        transportViewModel.play()
                    }

                    ProtoButton(
                        label = "STOP",
                        modifier = Modifier.weight(1f)
                    ) {
                        transportViewModel.stop()
                    }

                    ProtoButton(
                        label = "PAUS",
                        modifier = Modifier.weight(1f)
                    ) {
                        transportViewModel.pause()
                    }
                }

                val midiTargetLabels = remember(transportState.midiOutputTargets) {
                    buildMidiTargetShortLabels(transportState.midiOutputTargets)
                }

                if (transportState.midiOutputTargets.isEmpty()) {
                    Text(
                        text = "No MIDI output targets available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { transportViewModel.clearMidiOutputSelection() },
                            enabled = transportState.selectedMidiOutputId != null,
                            shape = ProtoControlShape
                        ) {
                            Text(text = "NONE")
                        }

                        transportState.midiOutputTargets.forEach { target ->
                            OutlinedButton(
                                onClick = {
                                    transportViewModel.selectMidiOutputTarget(target.selectionId)
                                },
                                enabled = transportState.selectedMidiOutputId != target.selectionId,
                                shape = ProtoControlShape
                            ) {
                                Text(text = midiTargetLabels[target.selectionId] ?: "OUT")
                            }
                        }
                    }
                }
            }

            SectionDivider()

            PageTabs(
                selectedPageIndex = sessionState.selectedPageIndex,
                onSelectPage = { pageIndex ->
                    sessionState = sessionState.selectPage(pageIndex)
                },
                modifier = Modifier.padding(top = 12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            PageHeader(
                pageIndex = currentPage.pageIndex,
                enabled = currentPage.enabled,
                onToggleEnabled = {
                    sessionState = sessionState.updatePage(currentPage.pageIndex) { page ->
                        page.copy(enabled = !page.enabled)
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            SequencerSelector(
                selectedSequencerType = currentPage.selectedSequencerType,
                onSelectType = { selectedType ->
                    if (
                        selectedType == SequencerType.EMPTY &&
                        currentPage.selectedSequencerType == SequencerType.TURING_MACHINE
                    ) {
                        transportViewModel.deactivatePageRuntime(currentPage.pageIndex)
                    }
                    sessionState = sessionState.updatePage(currentPage.pageIndex) { page ->
                        page.withSequencerType(selectedType)
                    }
                },
                onResetParameters = {
                    updateCurrentTuringState { StochasticSequencerUiState() }
                },
                modifier = Modifier.fillMaxWidth()
            )

            when (currentPage.selectedSequencerType) {
                SequencerType.EMPTY -> {
                    EmptySequencerView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }

                SequencerType.TURING_MACHINE -> {
                    TuringMachinePanel(
                        lockPosition = currentTuringState.lockPosition,
                        sequenceLength = currentTuringState.sequenceLength,
                        slewAmount = currentTuringState.slewAmount,
                        bernoulliProbability = currentTuringState.bernoulliProbability,
                        pitchRangeSemitones = currentTuringState.pitchRangeSemitones,
                        pitchOffset = currentTuringState.pitchOffset,
                        gateLength = currentTuringState.gateLength,
                        randomGateLength = currentTuringState.randomGateLength,
                        onLockPositionChange = { value ->
                            updateCurrentTuringState { it.copy(lockPosition = value.coerceIn(-1f, 1f)) }
                        },
                        onSequenceLengthChange = { length ->
                            updateCurrentTuringState { it.copy(sequenceLength = length.coerceIn(2, 64)) }
                        },
                        onSlewAmountChange = { value ->
                            updateCurrentTuringState { it.copy(slewAmount = value.coerceIn(0f, 1f)) }
                        },
                        onBernoulliProbabilityChange = { value ->
                            updateCurrentTuringState {
                                it.copy(bernoulliProbability = value.coerceIn(0f, 1f))
                            }
                        },
                        onPitchRangeSemitonesChange = { value ->
                            updateCurrentTuringState {
                                it.copy(pitchRangeSemitones = value.coerceIn(1, 64))
                            }
                        },
                        onPitchOffsetChange = { value ->
                            updateCurrentTuringState { it.copy(pitchOffset = value.coerceIn(-24, 24)) }
                        },
                        onGateLengthChange = { value ->
                            updateCurrentTuringState { it.copy(gateLength = value.coerceIn(0f, 1f)) }
                        },
                        onRandomGateLengthChange = { value ->
                            updateCurrentTuringState { it.copy(randomGateLength = value.coerceIn(0f, 1f)) }
                        },
                        outputMode = currentTuringState.outputMode,
                        midiChannel = currentTuringState.midiChannel,
                        baseNoteDisplay = midiNoteToDisplay(currentTuringState.baseNote),
                        quantizationDisplayName = currentTuringState.quantizationMode.displayName,
                        ccNumber = currentTuringState.ccNumber,
                        onOutputModeChange = { mode ->
                            updateCurrentTuringState { it.copy(outputMode = mode) }
                        },
                        onDecrementMidiChannel = {
                            updateCurrentTuringState {
                                val next = if (it.midiChannel <= 1) 16 else it.midiChannel - 1
                                it.copy(midiChannel = next.coerceIn(1, 16))
                            }
                        },
                        onIncrementMidiChannel = {
                            updateCurrentTuringState {
                                val next = if (it.midiChannel >= 16) 1 else it.midiChannel + 1
                                it.copy(midiChannel = next.coerceIn(1, 16))
                            }
                        },
                        onDecrementBaseNote = {
                            updateCurrentTuringState {
                                it.copy(baseNote = (it.baseNote - 1).coerceIn(0, 127))
                            }
                        },
                        onIncrementBaseNote = {
                            updateCurrentTuringState {
                                it.copy(baseNote = (it.baseNote + 1).coerceIn(0, 127))
                            }
                        },
                        onQuantizationClick = { showQuantizationDialog = true },
                        onDecrementCcNumber = {
                            updateCurrentTuringState {
                                it.copy(ccNumber = (it.ccNumber - 1).coerceIn(0, 127))
                            }
                        },
                        onIncrementCcNumber = {
                            updateCurrentTuringState {
                                it.copy(ccNumber = (it.ccNumber + 1).coerceIn(0, 127))
                            }
                        },
                        rptrIsRuntimeActive = (
                            transportState.rptrStatesByPage[currentPage.pageIndex]
                                ?: RptrUiRuntimeState.Idle
                            ) != RptrUiRuntimeState.Idle,
                        activeRptrDivision = transportState.activeRptrDivisionsByPage[currentPage.pageIndex],
                        rptrBaseUnits = currentTuringState.rptrBaseUnits,
                        rptrStartMode = currentTuringState.rptrStartMode,
                        showRptrBasePickerDialog = showRptrBasePickerDialog,
                        pageRuntimeEnabled = (
                            currentPage.enabled &&
                                currentPage.selectedSequencerType == SequencerType.TURING_MACHINE
                            ),
                        onShowRptrBasePickerDialogChange = { showRptrBasePickerDialog = it },
                        onDecrementRptrBaseUnits = {
                            updateCurrentTuringState {
                                it.copy(rptrBaseUnits = (it.rptrBaseUnits - 1).coerceAtLeast(1))
                            }
                        },
                        onIncrementRptrBaseUnits = {
                            updateCurrentTuringState {
                                it.copy(rptrBaseUnits = it.rptrBaseUnits + 1)
                            }
                        },
                        onSetRptrBaseUnits = { value ->
                            updateCurrentTuringState { it.copy(rptrBaseUnits = value.coerceAtLeast(1)) }
                        },
                        onSetRptrStartMode = { mode ->
                            updateCurrentTuringState { it.copy(rptrStartMode = mode) }
                        },
                        onPressRptr = { division ->
                            transportViewModel.pressRptr(
                                pageIndex = currentPage.pageIndex,
                                division = division,
                                config = currentTuringState.toConfig()
                            )
                        },
                        onReleaseRptr = { transportViewModel.releaseRptr(currentPage.pageIndex) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            SectionDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { showAboutDialog = true },
                    modifier = Modifier
                        .width(50.dp)
                        .height(44.dp),
                    shape = ProtoControlShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.protoseq_mark),
                        contentDescription = "About",
                        modifier = Modifier.size(34.dp)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.protoseq_wordmark),
                    contentDescription = "Protoseq",
                    modifier = Modifier.height(18.dp)
                )
            }
        }

        if (showBpmInputDialog) {
            AlertDialog(
                onDismissRequest = { showBpmInputDialog = false },
                title = { Text(text = "Set BPM") },
                text = {
                    OutlinedTextField(
                        value = bpmInputText,
                        onValueChange = { input: String ->
                            bpmInputText = input.filter { ch -> ch.isDigit() }
                        },
                        singleLine = true,
                        label = { Text("BPM (1-300)") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            bpmInputText
                                .toIntOrNull()
                                ?.coerceIn(1, 300)
                                ?.toFloat()
                                ?.let(transportViewModel::setBpm)
                            showBpmInputDialog = false
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBpmInputDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showQuantizationDialog) {
            AlertDialog(
                onDismissRequest = { showQuantizationDialog = false },
                title = { Text(text = "Select quantization") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        QuantizationMode.entries.forEach { mode ->
                            TextButton(
                                onClick = {
                                    updateCurrentTuringState { it.copy(quantizationMode = mode) }
                                    showQuantizationDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val selectedPrefix =
                                    if (mode == currentTuringState.quantizationMode) "✓ " else ""
                                Text(text = "$selectedPrefix${mode.displayName}")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showQuantizationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                icon = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.protoseq_mark),
                            contentDescription = "Protoseq logo",
                            modifier = Modifier.size(92.dp)
                        )
                    }
                },
                title = { Text(text = "Protoseq") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Version $appVersion\n\n" +
                                    "Protoseq is an open-source MIDI sequencer for mobile devices. " +
                                    "Its core generative behavior is inspired by the Turing Machine, " +
                                    "the random looping sequencer by Tom Whitwell / Music Thing Modular."
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    uriHandler.openUri("https://github.com/infamedavid/Protoseq")
                                },
                                modifier = Modifier.weight(1f),
                                shape = ProtoControlShape
                            ) {
                                Text("SOURCE")
                            }

                            OutlinedButton(
                                onClick = {
                                    uriHandler.openUri("https://www.paypal.com/paypalme/Davidb3d")
                                },
                                modifier = Modifier.weight(1f),
                                shape = ProtoControlShape
                            ) {
                                Text("DONATE")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    Divider(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    )
}

@Composable
private fun PageTabs(
    selectedPageIndex: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (0..4).forEach { pageIndex ->
            val isSelected = pageIndex == selectedPageIndex
            OutlinedButton(
                onClick = { onSelectPage(pageIndex) },
                shape = ProtoControlShape,
                colors = if (isSelected) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "P${pageIndex + 1}")
            }
        }
    }
}

@Composable
private fun SequencerSelector(
    selectedSequencerType: SequencerType,
    onSelectType: (SequencerType) -> Unit,
    onResetParameters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Sequencer",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SequencerType.entries.forEach { sequencerType ->
                val isSelected = selectedSequencerType == sequencerType
                OutlinedButton(
                    onClick = { onSelectType(sequencerType) },
                    shape = ProtoControlShape,
                    colors = if (isSelected) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = sequencerType.label)
                }
            }
        }

        OutlinedButton(
            onClick = onResetParameters,
            enabled = selectedSequencerType == SequencerType.TURING_MACHINE,
            shape = ProtoControlShape
        ) {
            Text(text = "Reset Parameters")
        }
    }
}

@Composable
private fun PageHeader(
    pageIndex: Int,
    enabled: Boolean,
    onToggleEnabled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Page ${pageIndex + 1}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedButton(
            onClick = onToggleEnabled,
            shape = ProtoControlShape
        ) {
            Text(text = if (enabled) "ON" else "OFF")
        }
    }
}

@Composable
private fun EmptySequencerView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Select a sequencer to activate this page.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
