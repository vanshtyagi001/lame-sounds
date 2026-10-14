package com.lamesounds.mobile.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamesounds.mobile.models.SoundButtonConfig
import com.lamesounds.mobile.models.SoundSet
import com.lamesounds.mobile.ui.components.*
import com.lamesounds.mobile.ui.theme.*
import com.lamesounds.mobile.viewmodel.SoundboardViewModel
import com.lamesounds.mobile.viewmodel.StorageSummary

@Composable
fun MainSoundboardScreen(
    viewModel: SoundboardViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val soundSets by viewModel.soundSets.collectAsState()
    val librarySounds by viewModel.librarySounds.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var editingButton by remember { mutableStateOf<Pair<String, SoundButtonConfig>?>(null) }
    var assigningSlot by remember { mutableStateOf<Pair<String, SoundButtonConfig>?>(null) }
    var editingSet by remember { mutableStateOf<SoundSet?>(null) }
    var bulkImportTargetSetId by remember { mutableStateOf<String?>(null) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    // Keep screen awake while app is open
    LaunchedEffect(connectionState.keepScreenAwake) {
        val window = (context as? android.app.Activity)?.window
        if (connectionState.keepScreenAwake) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        topBar = {
            ConnectionTopBar(
                state = connectionState,
                onAutoConnectClick = { viewModel.triggerAutoDiscovery() },
                onLibraryClick = { showLibrary = true },
                onSearchClick = { showSearch = true },
                onSettingsClick = { showSettings = true },
                isLandscape = isLandscape
            )
        },
        bottomBar = {
            if (!isLandscape) {
                BottomVolumeBar(
                    speakerVolume = connectionState.speakerVolume,
                    micVolume = connectionState.micVolume,
                    onSpeakerVolumeChange = { viewModel.updateSpeakerVolume(it) },
                    onMicVolumeChange = { viewModel.updateMicVolume(it) },
                    onStopAllClick = { viewModel.stopAllSounds() }
                )
            }
        },
        containerColor = CanvasBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        SoundSetPager(
                            soundSets = soundSets,
                            onSoundTap = { btn -> viewModel.playSound(btn) },
                            onSoundLongPress = { set, btn -> editingButton = Pair(set.id, btn) },
                            onEmptySlotClick = { set, btn -> assigningSlot = Pair(set.id, btn) },
                            onEditSetClick = { set -> editingSet = set },
                            onRenameSet = { setId, newName -> viewModel.updateSetName(setId, newName) },
                            onDeleteButton = { setId, btnId -> viewModel.deleteButton(setId, btnId) },
                            onReorderButtons = { setId, newButtons -> viewModel.reorderButtonsInSet(setId, newButtons) },
                            onCreateNewSetClick = { viewModel.createNewSoundSet() },
                            onImportToNewSetClick = { bulkImportTargetSetId = "NEW_SET" },
                            hapticEnabled = connectionState.hapticFeedbackEnabled,
                            isLandscape = true
                        )
                    }

                    RightVolumeSidebar(
                        speakerVolume = connectionState.speakerVolume,
                        micVolume = connectionState.micVolume,
                        onSpeakerVolumeChange = { viewModel.updateSpeakerVolume(it) },
                        onMicVolumeChange = { viewModel.updateMicVolume(it) },
                        onStopAllClick = { viewModel.stopAllSounds() }
                    )
                }
            } else {
                SoundSetPager(
                    soundSets = soundSets,
                    onSoundTap = { btn -> viewModel.playSound(btn) },
                    onSoundLongPress = { set, btn -> editingButton = Pair(set.id, btn) },
                    onEmptySlotClick = { set, btn -> assigningSlot = Pair(set.id, btn) },
                    onEditSetClick = { set -> editingSet = set },
                    onRenameSet = { setId, newName -> viewModel.updateSetName(setId, newName) },
                    onDeleteButton = { setId, btnId -> viewModel.deleteButton(setId, btnId) },
                    onReorderButtons = { setId, newButtons -> viewModel.reorderButtonsInSet(setId, newButtons) },
                    onCreateNewSetClick = { viewModel.createNewSoundSet() },
                    onImportToNewSetClick = { bulkImportTargetSetId = "NEW_SET" },
                    hapticEnabled = connectionState.hapticFeedbackEnabled,
                    isLandscape = false
                )
            }
        }
    }

    // Audio Library Modal (Slot selection mode or multi-select into slot)
    assigningSlot?.let { (setId, slot) ->
        val targetSet = soundSets.firstOrNull { it.id == setId }
        val isReplacing = slot.soundId.isNotBlank() && !slot.id.startsWith("add_slot_")
        val existingSoundIds = remember(targetSet, slot) {
            targetSet?.buttons
                ?.filter { it.soundId.isNotBlank() && !it.label.startsWith("+ Slot") }
                ?.map { it.soundId }
                ?.filter { if (isReplacing) it != slot.soundId else true }
                ?.toSet() ?: emptySet()
        }
        val existingSoundTitles = remember(targetSet, slot) {
            targetSet?.buttons
                ?.filter { it.soundId.isNotBlank() && !it.label.startsWith("+ Slot") }
                ?.map { it.label.trim().lowercase() }
                ?.filter { if (isReplacing) it != slot.label.trim().lowercase() else true }
                ?.toSet() ?: emptySet()
        }
        val availableSounds = remember(librarySounds, existingSoundIds, existingSoundTitles) {
            if (existingSoundIds.isNotEmpty() || existingSoundTitles.isNotEmpty()) {
                librarySounds.filterNot { sound ->
                    sound.id in existingSoundIds || sound.title.trim().lowercase() in existingSoundTitles
                }
            } else {
                librarySounds
            }
        }

        SoundLibraryModal(
            librarySounds = availableSounds,
            titleText = "ASSIGN AUDIO TO SLOT",
            onSoundPreview = { viewModel.previewSound(it) },
            onSoundSelected = { selected ->
                viewModel.assignSoundToSlot(setId, slot, selected)
                assigningSlot = null
            },
            onMultipleSoundsSelected = { selectedList ->
                if (selectedList.size == 1) {
                    viewModel.assignSoundToSlot(setId, slot, selectedList.first())
                } else {
                    viewModel.importMultipleSoundsToSet(setId, selectedList)
                }
                assigningSlot = null
            },
            onImportAudio = { uri, name ->
                viewModel.importCustomAudio(uri, name)
            },
            onDeleteCustomAudio = { id ->
                viewModel.deleteCustomAudio(id)
            },
            onImportAllFromPc = { viewModel.importAllSoundsFromPc() },
            onSendAllToPc = { viewModel.sendAllSoundsToPc() },
            syncMessage = syncMessage,
            onClearSyncMessage = { viewModel.clearSyncMessage() },
            onDismiss = { assigningSlot = null }
        )
    }

    // Audio Library Modal for Bulk Import into New or Existing Set
    bulkImportTargetSetId?.let { targetId ->
        val targetSet = if (targetId != "NEW_SET") soundSets.firstOrNull { it.id == targetId } else null
        val existingSoundIds = remember(targetSet) {
            targetSet?.buttons
                ?.filter { it.soundId.isNotBlank() && !it.label.startsWith("+ Slot") }
                ?.map { it.soundId }
                ?.toSet() ?: emptySet()
        }
        val existingSoundTitles = remember(targetSet) {
            targetSet?.buttons
                ?.filter { it.soundId.isNotBlank() && !it.label.startsWith("+ Slot") }
                ?.map { it.label.trim().lowercase() }
                ?.toSet() ?: emptySet()
        }
        val availableSounds = remember(librarySounds, existingSoundIds, existingSoundTitles) {
            if (existingSoundIds.isNotEmpty() || existingSoundTitles.isNotEmpty()) {
                librarySounds.filterNot { sound ->
                    sound.id in existingSoundIds || sound.title.trim().lowercase() in existingSoundTitles
                }
            } else {
                librarySounds
            }
        }

        SoundLibraryModal(
            librarySounds = availableSounds,
            titleText = if (targetId == "NEW_SET") "IMPORT SOUNDS TO NEW SET" else "IMPORT SOUNDS TO SET",
            onSoundPreview = { viewModel.previewSound(it) },
            onSoundSelected = { selected ->
                val actualSetId = if (targetId == "NEW_SET") viewModel.createNewSoundSet().id else targetId
                viewModel.importMultipleSoundsToSet(actualSetId, listOf(selected))
                bulkImportTargetSetId = null
            },
            onMultipleSoundsSelected = { selectedList ->
                val actualSetId = if (targetId == "NEW_SET") viewModel.createNewSoundSet().id else targetId
                viewModel.importMultipleSoundsToSet(actualSetId, selectedList)
                bulkImportTargetSetId = null
            },
            onImportAudio = { uri, name ->
                viewModel.importCustomAudio(uri, name)
            },
            onDeleteCustomAudio = { id ->
                viewModel.deleteCustomAudio(id)
            },
            onImportAllFromPc = { viewModel.importAllSoundsFromPc() },
            onSendAllToPc = { viewModel.sendAllSoundsToPc() },
            syncMessage = syncMessage,
            onClearSyncMessage = { viewModel.clearSyncMessage() },
            onDismiss = { bulkImportTargetSetId = null }
        )
    }

    if (showLibrary && assigningSlot == null) {
        SoundLibraryModal(
            librarySounds = librarySounds,
            titleText = "AUDIO LIBRARY",
            onSoundPreview = { viewModel.previewSound(it) },
            onSoundSelected = null,
            onImportAudio = { uri, name ->
                viewModel.importCustomAudio(uri, name)
            },
            onDeleteCustomAudio = { id ->
                viewModel.deleteCustomAudio(id)
            },
            onImportAllFromPc = { viewModel.importAllSoundsFromPc() },
            onSendAllToPc = { viewModel.sendAllSoundsToPc() },
            syncMessage = syncMessage,
            onClearSyncMessage = { viewModel.clearSyncMessage() },
            onDismiss = { showLibrary = false }
        )
    }

    // Search Modal
    if (showSearch) {
        SearchModal(
            soundSets = soundSets,
            onSoundTap = { btn -> viewModel.playSound(btn) },
            onDismiss = { showSearch = false }
        )
    }

    // Edit Button Customizer Dialog
    editingButton?.let { (setId, btn) ->
        EditButtonDialog(
            button = btn,
            onBrowseLibrary = {
                assigningSlot = Pair(setId, btn)
                editingButton = null
            },
            onSave = { updated ->
                viewModel.updateButton(setId, updated)
                editingButton = null
            },
            onDelete = {
                viewModel.deleteButton(setId, btn.id)
                editingButton = null
            },
            onDismiss = { editingButton = null }
        )
    }

    // Edit Sound Set / Grid Size Dialog
    editingSet?.let { set ->
        GridSizeDialog(
            soundSet = set,
            canDelete = soundSets.size > 1,
            onDeleteSet = {
                viewModel.deleteSoundSet(set.id)
                editingSet = null
            },
            onSave = { rows, cols ->
                viewModel.updateGridDimensions(set.id, rows, cols)
                editingSet = null
            },
            onDismiss = { editingSet = null }
        )
    }

    // Host & Phone Storage Settings Dialog
    if (showSettings) {
        val storageSummary = remember(soundSets, librarySounds) {
            viewModel.getStorageSummary()
        }

        SettingsDialog(
            currentHost = connectionState.host,
            currentHttpPort = connectionState.httpPort,
            currentUdpPort = connectionState.udpPort,
            autoConnectEnabled = connectionState.autoConnectEnabled,
            hapticEnabled = connectionState.hapticFeedbackEnabled,
            keepScreenAwake = connectionState.keepScreenAwake,
            isAutoDiscovering = connectionState.isAutoDiscovering,
            storageSummary = storageSummary,
            onAutoConnectToggle = { viewModel.setAutoConnectEnabled(it) },
            onHapticToggle = { viewModel.setHapticFeedbackEnabled(it) },
            onKeepScreenAwakeToggle = { viewModel.setKeepScreenAwake(it) },
            onScanNow = { viewModel.triggerAutoDiscovery() },
            onSaveSettings = { host, httpPort, udpPort ->
                viewModel.updateNetworkSettings(host, httpPort, udpPort)
            },
            onExportBackup = { viewModel.exportProfileJson() },
            onImportBackup = { json -> viewModel.importProfileJson(json) },
            onResetDefaults = { viewModel.resetSoundSetsToDefault() },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun GridSizeDialog(
    soundSet: SoundSet,
    canDelete: Boolean = false,
    onDeleteSet: () -> Unit = {},
    onSave: (rows: Int, columns: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCols by remember { mutableStateOf(soundSet.columns.coerceIn(2, 5)) }
    val columnOptions = listOf(2, 3, 4, 5)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "BUTTONS PER ROW",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.03).em,
                    color = InkWhite
                )

                Text(
                    text = soundSet.name,
                    fontSize = 12.sp,
                    color = InkMuted,
                    letterSpacing = (-0.01).em
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose how many sound buttons to display per row. The set scrolls vertically as you add sounds.",
                    fontSize = 11.sp,
                    color = InkMuted,
                    letterSpacing = (-0.01).em
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    columnOptions.forEach { cols ->
                        val isSelected = selectedCols == cols
                        Button(
                            onClick = { selectedCols = cols },
                            shape = RoundedCornerShape(9999.dp),
                            border = BorderStroke(1.dp, if (isSelected) InkWhite else HairlineBorder),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) InkWhite else SurfaceCard,
                                contentColor = if (isSelected) CanvasBlack else InkWhite
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "$cols / ROW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.02).em
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Pill CTA (Analogue Spec)
                Button(
                    onClick = {
                        onSave(soundSet.rows, selectedCols)
                    },
                    shape = RoundedCornerShape(9999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkWhite,
                        contentColor = CanvasBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "APPLY LAYOUT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em
                    )
                }

                if (canDelete) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDeleteSet,
                        shape = RoundedCornerShape(9999.dp),
                        border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StatusRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "DELETE THIS SET",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentHost: String,
    currentHttpPort: Int,
    currentUdpPort: Int,
    autoConnectEnabled: Boolean,
    hapticEnabled: Boolean,
    keepScreenAwake: Boolean = true,
    isAutoDiscovering: Boolean,
    storageSummary: StorageSummary,
    onAutoConnectToggle: (Boolean) -> Unit,
    onHapticToggle: (Boolean) -> Unit,
    onKeepScreenAwakeToggle: (Boolean) -> Unit = {},
    onScanNow: () -> Unit,
    onSaveSettings: (host: String, httpPort: Int, udpPort: Int) -> Unit,
    onExportBackup: () -> String,
    onImportBackup: (String) -> Boolean,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember(currentHost) { mutableStateOf(currentHost) }
    var httpPortText by remember(currentHttpPort) { mutableStateOf(currentHttpPort.toString()) }
    var udpPortText by remember(currentUdpPort) { mutableStateOf(currentUdpPort.toString()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showImportInput by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(22.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SETTINGS & STORAGE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.03).em,
                        color = InkWhite
                    )

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = InkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // SECTION 1: NETWORK CONFIGURATION
                Text(
                    text = "NETWORK CONFIGURATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em,
                    color = InkMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Auto-Connect Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Connect",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em,
                            color = InkWhite
                        )
                        Text(
                            text = "Auto discover PC over Wi-Fi & Mobile Hotspot",
                            fontSize = 11.sp,
                            letterSpacing = (-0.01).em,
                            color = InkMuted
                        )
                    }

                    Switch(
                        checked = autoConnectEnabled,
                        onCheckedChange = onAutoConnectToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasBlack,
                            checkedTrackColor = InkWhite,
                            uncheckedThumbColor = InkMuted,
                            uncheckedTrackColor = SurfaceCard
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scan For PC Now Button
                Button(
                    onClick = onScanNow,
                    enabled = !isAutoDiscovering,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceCard,
                        contentColor = InkWhite
                    ),
                    border = BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAutoDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = InkWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SCANNING NETWORK...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em
                        )
                    } else {
                        Text(
                            "SCAN FOR PC NOW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Host IP Input
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("HOST IP ADDRESS", fontSize = 10.sp, letterSpacing = (-0.01).em) },
                    placeholder = { Text("e.g. 192.168.1.100") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = InkWhite,
                        unfocusedTextColor = InkWhite,
                        focusedBorderColor = InkWhite,
                        unfocusedBorderColor = HairlineBorder,
                        focusedLabelColor = InkWhite,
                        unfocusedLabelColor = InkMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // HTTP & UDP Port inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = httpPortText,
                        onValueChange = { httpPortText = it },
                        label = { Text("HTTP PORT", fontSize = 10.sp, letterSpacing = (-0.01).em) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = InkWhite,
                            unfocusedTextColor = InkWhite,
                            focusedBorderColor = InkWhite,
                            unfocusedBorderColor = HairlineBorder,
                            focusedLabelColor = InkWhite,
                            unfocusedLabelColor = InkMuted
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = udpPortText,
                        onValueChange = { udpPortText = it },
                        label = { Text("UDP PORT", fontSize = 10.sp, letterSpacing = (-0.01).em) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = InkWhite,
                            unfocusedTextColor = InkWhite,
                            focusedBorderColor = InkWhite,
                            unfocusedBorderColor = HairlineBorder,
                            focusedLabelColor = InkWhite,
                            unfocusedLabelColor = InkMuted
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hotspot tip card
                Surface(
                    color = CanvasBlack,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "HOTSPOT TIP: When using Windows Laptop Hotspot, gateway is 192.168.137.1.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.01).em,
                        color = InkFaint,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = HairlineBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(18.dp))

                // SECTION 2: AUDIO & HAPTIC FEEDBACK
                Text(
                    text = "FEEDBACK & AUDIO PREFERENCES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em,
                    color = InkMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Haptic Feedback Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Haptic Vibration",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em,
                            color = InkWhite
                        )
                        Text(
                            text = "Tactile vibration feedback on button presses",
                            fontSize = 11.sp,
                            letterSpacing = (-0.01).em,
                            color = InkMuted
                        )
                    }

                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = onHapticToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasBlack,
                            checkedTrackColor = InkWhite,
                            uncheckedThumbColor = InkMuted,
                            uncheckedTrackColor = SurfaceCard
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Keep Screen Awake Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Screen Awake",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em,
                            color = InkWhite
                        )
                        Text(
                            text = "Prevent phone screen from going to sleep while app is open",
                            fontSize = 11.sp,
                            letterSpacing = (-0.01).em,
                            color = InkMuted
                        )
                    }

                    Switch(
                        checked = keepScreenAwake,
                        onCheckedChange = onKeepScreenAwakeToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasBlack,
                            checkedTrackColor = InkWhite,
                            uncheckedThumbColor = InkMuted,
                            uncheckedTrackColor = SurfaceCard
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "✓ Monitoring and Mic volume levels are automatically saved to your phone.",
                    fontSize = 10.sp,
                    color = InkFaint
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = HairlineBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(18.dp))

                // SECTION 3: PHONE STORAGE & PROFILES
                Text(
                    text = "PHONE STORAGE & PROFILES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em,
                    color = InkMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Storage Overview Card
                Surface(
                    color = CanvasBlack,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("STORAGE STATUS", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = InkMuted)
                            Text("SAVED ON PHONE", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = StatusGreen)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• ${storageSummary.totalSets} Sound Sets configured\n" +
                                    "• ${storageSummary.totalButtons} Soundboard button slots\n" +
                                    "• ${storageSummary.customSoundsCount} Imported audio files (${formatBytes(storageSummary.customSoundsSizeBytes)})",
                            fontSize = 11.sp,
                            color = InkWhite,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Layouts, custom colors, shapes, and imported files persist across app restarts.",
                            fontSize = 10.sp,
                            color = InkFaint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Profile Export Button
                Button(
                    onClick = {
                        val exported = onExportBackup()
                        clipboardManager.setText(AnnotatedString(exported))
                        statusMessage = "✓ Profile backup JSON copied to clipboard!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                    border = BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("COPY PROFILE JSON TO CLIPBOARD", fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Profile Import Button
                Button(
                    onClick = { showImportInput = !showImportInput },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                    border = BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showImportInput) "HIDE IMPORT FIELD" else "IMPORT PROFILE FROM JSON",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em
                    )
                }

                if (showImportInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        label = { Text("PASTE PROFILE JSON HERE", fontSize = 10.sp) },
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = InkWhite,
                            unfocusedTextColor = InkWhite,
                            focusedBorderColor = InkWhite,
                            unfocusedBorderColor = HairlineBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val success = onImportBackup(importJsonText)
                            if (success) {
                                statusMessage = "✓ Profile imported and saved successfully!"
                                showImportInput = false
                                importJsonText = ""
                            } else {
                                statusMessage = "⚠️ Invalid profile JSON. Please verify the format."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InkWhite, contentColor = CanvasBlack),
                        shape = RoundedCornerShape(9999.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("APPLY IMPORTED PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Reset Defaults Button
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CanvasBlack, contentColor = StatusRed),
                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RESET SOUNDBOARD TO FACTORY DEFAULTS", fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em)
                }

                if (showResetConfirm) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = CanvasBlack,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StatusRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Are you sure you want to reset all sound sets and layouts to factory default?",
                                fontSize = 11.sp,
                                color = InkWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onResetDefaults()
                                        showResetConfirm = false
                                        statusMessage = "✓ Restored factory default sounds and sets."
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = CanvasBlack),
                                    shape = RoundedCornerShape(9999.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("CONFIRM RESET", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { showResetConfirm = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                                    shape = RoundedCornerShape(9999.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("CANCEL", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // Feedback status message banner
                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusMessage ?: "",
                        fontSize = 11.sp,
                        color = if (statusMessage?.startsWith("✓") == true) StatusGreen else StatusRed
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Save & Connect Primary Pill CTA
                Button(
                    onClick = {
                        val httpP = httpPortText.toIntOrNull() ?: 50050
                        val udpP = udpPortText.toIntOrNull() ?: 50051
                        onSaveSettings(host, httpP, udpP)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite, contentColor = CanvasBlack),
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "SAVE & CONNECT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    return when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
        else -> "$bytes B"
    }
}
