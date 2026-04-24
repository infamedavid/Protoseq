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
import androidx.compose.material3.HorizontalDivider
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
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerViewModel
import com.infamedavid.protoseq.features.stochastic.toConfig
import com.infamedavid.protoseq.features.transport.RptrUiRuntimeState
import com.infamedavid.protoseq.features.transport.TransportViewModel
import com.infamedavid.protoseq.ui.components.ProtoButton
import com.infamedavid.protoseq.ui.components.ProtoControlShape
import com.infamedavid.protoseq.ui.sequencers.TuringMachinePanel
import com.infamedavid.protoseq.ui.util.buildMidiTargetShortLabels
import com.infamedavid.protoseq.ui.util.midiNoteToDisplay

@Composable
fun AppScreen(
    stochasticViewModel: StochasticSequencerViewModel = viewModel()
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
    val stochasticState by stochasticViewModel.uiState.collectAsState()

    var showBpmInputDialog by rememberSaveable { mutableStateOf(false) }
    var bpmInputText by rememberSaveable { mutableStateOf("") }
    var showQuantizationDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showRptrBasePickerDialog by rememberSaveable { mutableStateOf(false) }
    var sessionState by remember {
        mutableStateOf(
            createDefaultProtoseqSessionState().updatePage(pageIndex = 0) { page ->
                page.withSequencerType(SequencerType.TURING_MACHINE)
            }
        )
    }
    val currentPage = sessionState.currentPage()

    LaunchedEffect(stochasticState) {
        transportViewModel.updateSequencerConfig(stochasticState.toConfig())
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

            Text(
                text = "Page ${currentPage.pageIndex + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            SequencerSelector(
                selectedSequencerType = currentPage.selectedSequencerType,
                onSelectType = { selectedType ->
                    sessionState = sessionState.updatePage(currentPage.pageIndex) { page ->
                        page.withSequencerType(selectedType)
                    }
                },
                onResetParameters = stochasticViewModel::resetToDefaults,
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
                        lockPosition = stochasticState.lockPosition,
                        sequenceLength = stochasticState.sequenceLength,
                        slewAmount = stochasticState.slewAmount,
                        bernoulliProbability = stochasticState.bernoulliProbability,
                        pitchRangeSemitones = stochasticState.pitchRangeSemitones,
                        pitchOffset = stochasticState.pitchOffset,
                        gateLength = stochasticState.gateLength,
                        randomGateLength = stochasticState.randomGateLength,
                        onLockPositionChange = stochasticViewModel::setLockPosition,
                        onSequenceLengthChange = stochasticViewModel::setSequenceLength,
                        onSlewAmountChange = stochasticViewModel::setSlewAmount,
                        onBernoulliProbabilityChange = stochasticViewModel::setBernoulliProbability,
                        onPitchRangeSemitonesChange = stochasticViewModel::setPitchRangeSemitones,
                        onPitchOffsetChange = stochasticViewModel::setPitchOffset,
                        onGateLengthChange = stochasticViewModel::setGateLength,
                        onRandomGateLengthChange = stochasticViewModel::setRandomGateLength,
                        outputMode = stochasticState.outputMode,
                        midiChannel = stochasticState.midiChannel,
                        baseNoteDisplay = midiNoteToDisplay(stochasticState.baseNote),
                        quantizationDisplayName = stochasticState.quantizationMode.displayName,
                        ccNumber = stochasticState.ccNumber,
                        onOutputModeChange = stochasticViewModel::setOutputMode,
                        onDecrementMidiChannel = stochasticViewModel::decrementMidiChannel,
                        onIncrementMidiChannel = stochasticViewModel::incrementMidiChannel,
                        onDecrementBaseNote = stochasticViewModel::decrementBaseNote,
                        onIncrementBaseNote = stochasticViewModel::incrementBaseNote,
                        onQuantizationClick = { showQuantizationDialog = true },
                        onDecrementCcNumber = {
                            stochasticViewModel.setCcNumber(stochasticState.ccNumber - 1)
                        },
                        onIncrementCcNumber = {
                            stochasticViewModel.setCcNumber(stochasticState.ccNumber + 1)
                        },
                        rptrIsRuntimeActive = transportState.rptrState != RptrUiRuntimeState.Idle,
                        activeRptrDivision = transportState.activeRptrDivision,
                        rptrBaseUnits = stochasticState.rptrBaseUnits,
                        rptrStartMode = stochasticState.rptrStartMode,
                        showRptrBasePickerDialog = showRptrBasePickerDialog,
                        onShowRptrBasePickerDialogChange = { showRptrBasePickerDialog = it },
                        onDecrementRptrBaseUnits = stochasticViewModel::decrementRptrBaseUnits,
                        onIncrementRptrBaseUnits = stochasticViewModel::incrementRptrBaseUnits,
                        onSetRptrBaseUnits = stochasticViewModel::setRptrBaseUnits,
                        onSetRptrStartMode = stochasticViewModel::setRptrStartMode,
                        onPressRptr = { division ->
                            transportViewModel.pressRptr(division, stochasticState.toConfig())
                        },
                        onReleaseRptr = transportViewModel::releaseRptr,
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
                                    stochasticViewModel.setQuantizationMode(mode)
                                    showQuantizationDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val selectedPrefix =
                                    if (mode == stochasticState.quantizationMode) "✓ " else ""
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
    HorizontalDivider(
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
