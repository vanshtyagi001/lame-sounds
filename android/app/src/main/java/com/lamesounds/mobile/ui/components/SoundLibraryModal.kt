package com.lamesounds.mobile.ui.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lamesounds.mobile.models.SoundLibraryItem
import com.lamesounds.mobile.ui.theme.*
import java.io.File

@Composable
fun SoundLibraryModal(
    librarySounds: List<SoundLibraryItem>,
    onSoundPreview: (SoundLibraryItem) -> Unit,
    onSoundSelected: ((SoundLibraryItem) -> Unit)? = null,
    onMultipleSoundsSelected: ((List<SoundLibraryItem>) -> Unit)? = null,
    onImportAudio: (Uri, String) -> Unit,
    onDeleteCustomAudio: ((String) -> Unit)? = null,
    onImportAllFromPc: (() -> Unit)? = null,
    onSendAllToPc: (() -> Unit)? = null,
    syncMessage: String? = null,
    onClearSyncMessage: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    titleText: String = if (onMultipleSoundsSelected != null) "SELECT SOUNDS TO IMPORT"
        else if (onSoundSelected != null) "ASSIGN AUDIO TO SLOT"
        else "AUDIO LIBRARY"
) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedSoundIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var soundPendingDelete by remember { mutableStateOf<SoundLibraryItem?>(null) }

    // Audio file picker launcher - saves directly to phone storage
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri)
            onImportAudio(uri, fileName)
        }
    }

    val categories = remember(librarySounds) {
        listOf("All") + librarySounds.map { it.category }.distinct().filter { it != "All" }
    }

    val filteredSounds = remember(searchQuery, selectedCategory, librarySounds) {
        librarySounds.filter { item ->
            val matchesCat = selectedCategory == "All" || item.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.id.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.92f else 0.94f)
                .fillMaxHeight(if (isLandscape) 0.94f else 0.88f),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, HairlineBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isLandscape) 14.dp else 16.dp,
                        vertical = if (isLandscape) 10.dp else 14.dp
                    )
            ) {
                // Header section
                if (isLandscape) {
                    // Landscape: Compact single-row Title + Inline Search + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = titleText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).em,
                            color = InkWhite,
                            modifier = Modifier.padding(end = 10.dp)
                        )

                        // Compact inline search bar
                        LibrarySearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholderText = "Search audio clips...",
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = InkMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Landscape Actions Row (Compact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { audioPickerLauncher.launch("audio/*") },
                                shape = RoundedCornerShape(9999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                                border = BorderStroke(1.dp, HairlineBorder),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("+ FILE", fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            }

                            if (onImportAllFromPc != null) {
                                Button(
                                    onClick = onImportAllFromPc,
                                    shape = RoundedCornerShape(9999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("⬇ FROM PC", fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            if (onSendAllToPc != null) {
                                Button(
                                    onClick = onSendAllToPc,
                                    shape = RoundedCornerShape(9999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("⬆ TO PC", fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Quick Select All / Clear for multi-select mode
                            if (onMultipleSoundsSelected != null && filteredSounds.isNotEmpty()) {
                                val allSelected = filteredSounds.all { it.id in selectedSoundIds }
                                TextButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedSoundIds = if (allSelected) {
                                            selectedSoundIds - filteredSounds.map { it.id }.toSet()
                                        } else {
                                            selectedSoundIds + filteredSounds.map { it.id }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        text = if (allSelected) "DESELECT ALL" else "SELECT ALL",
                                        fontSize = 9.sp,
                                        color = StatusGreen
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (selectedSoundIds.isNotEmpty()) "${selectedSoundIds.size} SELECTED / ${filteredSounds.size}"
                                else "${filteredSounds.size} AVAILABLE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.01).em,
                            color = if (selectedSoundIds.isNotEmpty()) StatusGreen else InkFaint
                        )
                    }
                } else {
                    // Portrait: Standard sleek vertical flow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = titleText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).em,
                            color = InkWhite
                        )

                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = InkMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Portrait Search Bar
                    LibrarySearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search audio clips...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Portrait Category & Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { audioPickerLauncher.launch("audio/*") },
                                shape = RoundedCornerShape(9999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                                border = BorderStroke(1.dp, HairlineBorder),
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("+ FILE", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }

                            if (onImportAllFromPc != null) {
                                Button(
                                    onClick = onImportAllFromPc,
                                    shape = RoundedCornerShape(9999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("⬇ FROM PC", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            if (onSendAllToPc != null) {
                                Button(
                                    onClick = onSendAllToPc,
                                    shape = RoundedCornerShape(9999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = InkWhite),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("⬆ TO PC", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Quick Select All / Clear for multi-select mode
                            if (onMultipleSoundsSelected != null && filteredSounds.isNotEmpty()) {
                                val allSelected = filteredSounds.all { it.id in selectedSoundIds }
                                TextButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedSoundIds = if (allSelected) {
                                            selectedSoundIds - filteredSounds.map { it.id }.toSet()
                                        } else {
                                            selectedSoundIds + filteredSounds.map { it.id }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = if (allSelected) "DESELECT ALL" else "SELECT ALL",
                                        fontSize = 10.sp,
                                        color = StatusGreen
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (selectedSoundIds.isNotEmpty()) "${selectedSoundIds.size} SELECTED"
                                else "${filteredSounds.size} AVAILABLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.01).em,
                            color = if (selectedSoundIds.isNotEmpty()) StatusGreen else InkFaint
                        )
                    }
                }

                // Sync status banner if message present
                if (!syncMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceCard)
                            .border(1.dp, HairlineBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = syncMessage,
                            fontSize = 10.sp,
                            color = InkWhite,
                            modifier = Modifier.weight(1f)
                        )
                        if (onClearSyncMessage != null) {
                            IconButton(onClick = onClearSyncMessage, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = InkMuted, modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 10.dp))

                // Adaptive Grid of Sounds: 2 columns in landscape, 1 column in portrait
                if (filteredSounds.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No sounds match \"$searchQuery\""
                                    else if (titleText.contains("ASSIGN", ignoreCase = true) || titleText.contains("IMPORT", ignoreCase = true)) "All available library sounds are already in this set"
                                    else "No sounds available",
                                fontSize = 12.sp,
                                color = InkMuted
                            )
                            if (onImportAllFromPc != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap '⬇ FROM PC' to sync clips from your computer",
                                    fontSize = 10.sp,
                                    color = InkFaint
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isLandscape) 2 else 1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        itemsIndexed(
                            items = filteredSounds,
                            key = { index, sound -> "${sound.source}_${sound.id}_$index" }
                        ) { _, sound ->
                            val isSelected = sound.id in selectedSoundIds

                            SoundLibraryRow(
                                sound = sound,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    selectedSoundIds = if (isSelected) {
                                        selectedSoundIds - sound.id
                                    } else {
                                        selectedSoundIds + sound.id
                                    }
                                },
                                onSoundPreview = onSoundPreview,
                                onSoundSelected = onSoundSelected,
                                isMultiSelectEnabled = onMultipleSoundsSelected != null,
                                onRequestDelete = if (onDeleteCustomAudio != null) { item -> soundPendingDelete = item } else null,
                                isLandscape = isLandscape
                            )
                        }
                    }
                }

                // Docked Bottom Import Bar (when multiple sounds are selected)
                if (onMultipleSoundsSelected != null && selectedSoundIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = InkWhite,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(CanvasBlack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${selectedSoundIds.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkWhite
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedSoundIds.size == 1) "SOUND SELECTED" else "SOUNDS SELECTED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.01).em,
                                    color = CanvasBlack
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(
                                    onClick = { selectedSoundIds = emptySet() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("CLEAR", fontSize = 10.sp, color = CanvasBlack)
                                }

                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        val selectedItems = librarySounds.filter { it.id in selectedSoundIds }
                                        onMultipleSoundsSelected(selectedItems)
                                    },
                                    shape = RoundedCornerShape(9999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CanvasBlack,
                                        contentColor = InkWhite
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "IMPORT (${selectedSoundIds.size})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.02).em
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    soundPendingDelete?.let { sound ->
        AlertDialog(
            onDismissRequest = { soundPendingDelete = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(StatusRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = StatusRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Delete Audio Clip?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkWhite
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${sound.title}\"? This action cannot be undone.",
                    fontSize = 12.sp,
                    color = InkMuted,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        val idToDelete = sound.id
                        soundPendingDelete = null
                        selectedSoundIds = selectedSoundIds - idToDelete
                        onDeleteCustomAudio?.invoke(idToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusRed,
                        contentColor = InkWhite
                    ),
                    shape = RoundedCornerShape(9999.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "DELETE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.01).em
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { soundPendingDelete = null },
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "CANCEL",
                        fontSize = 11.sp,
                        color = InkMuted
                    )
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }
}

/**
 * Custom compact search bar with clean vertical alignment and zero text clipping.
 */
@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String = "Search audio clips...",
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
            color = InkWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        ),
        cursorBrush = SolidColor(StatusGreen),
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(CanvasBlack)
            .border(1.dp, HairlineBorder, RoundedCornerShape(9999.dp)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholderText,
                            color = InkMuted,
                            fontSize = 12.sp
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = InkMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    )
}

/**
 * Compact, proportional sound card component with separated preview play button and selection indicator.
 */
@Composable
private fun SoundLibraryRow(
    sound: SoundLibraryItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onSoundPreview: (SoundLibraryItem) -> Unit,
    onSoundSelected: ((SoundLibraryItem) -> Unit)?,
    isMultiSelectEnabled: Boolean,
    onRequestDelete: ((SoundLibraryItem) -> Unit)?,
    isLandscape: Boolean
) {
    val view = LocalView.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SurfaceCard else CanvasBlack)
            .border(
                1.dp,
                if (isSelected) StatusGreen else HairlineBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (isMultiSelectEnabled) {
                    onToggleSelect()
                } else if (onSoundSelected != null) {
                    onSoundSelected(sound)
                } else {
                    onSoundPreview(sound)
                }
            }
            .padding(horizontal = 8.dp, vertical = if (isLandscape) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: Play button, Thumbnail/Accent, Metadata
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Preview Play Button (Compact 26dp circle on far left)
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSoundPreview(sound)
                },
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard)
                    .border(1.dp, HairlineBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Preview",
                    tint = InkWhite,
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(modifier = Modifier.width(7.dp))

            // Artwork Thumbnail or Color Accent Strip (Compact 24dp)
            if (!sound.imagePath.isNullOrBlank()) {
                val img = remember(sound.imagePath) {
                    val f = File(sound.imagePath)
                    if (f.exists()) {
                        try {
                            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                        } catch (e: Exception) { null }
                    } else null
                }
                if (img != null) {
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, HairlineBorder, RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                }
            } else {
                val col = remember(sound.colorHex) {
                    try {
                        val c = sound.colorHex.removePrefix("#").toLong(16)
                        Color(if (sound.colorHex.length <= 7) c or 0xFF000000 else c)
                    } catch (e: Exception) {
                        Color(0xFF60CDFF)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 20.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(col)
                )
                Spacer(modifier = Modifier.width(7.dp))
            }

            // Sound Metadata & Titles
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sound.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.02).em,
                    color = InkWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(1.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Compact origin badge
                    val isFromPc = sound.source.contains("PC", ignoreCase = true) || sound.category.equals("PC", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(SurfaceDark)
                            .padding(horizontal = 3.5.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = if (isFromPc) "PC" else "LOCAL",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFromPc) StatusGreen else InkMuted
                        )
                    }

                    if (sound.category.isNotBlank() && sound.category != "All" && sound.category != "PC") {
                        Text(
                            text = sound.category,
                            fontSize = 8.5.sp,
                            color = InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(text = "•", fontSize = 7.5.sp, color = InkFaint)
                    }

                    if (sound.durationSeconds > 0) {
                        Text(
                            text = "${(sound.durationSeconds * 10).toInt() / 10.0}s",
                            fontSize = 8.5.sp,
                            color = InkFaint
                        )
                    }

                    if (sound.fileSizeBytes > 0) {
                        Text(text = "•", fontSize = 7.5.sp, color = InkFaint)
                        Text(
                            text = formatFileSize(sound.fileSizeBytes),
                            fontSize = 8.5.sp,
                            color = InkFaint
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Action Buttons Row (Far Right): Delete button + Single-Select Pill or Multi-Select Circle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Delete Custom Audio Button (Compact 24dp)
            if (sound.isCustom && onRequestDelete != null) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onRequestDelete(sound)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = InkFaint,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Quick Single-Select Action Pill (Compact 24dp)
            if (onSoundSelected != null) {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSoundSelected(sound)
                    },
                    shape = RoundedCornerShape(9999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkWhite,
                        contentColor = CanvasBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = "SELECT",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em
                    )
                }
            }

            // Multi-Select Checkbox Circle (Far right - completely separated from Play button)
            if (isMultiSelectEnabled) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) StatusGreen else CanvasBlack)
                        .border(
                            1.5.dp,
                            if (isSelected) StatusGreen else HairlineBright,
                            CircleShape
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onToggleSelect()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = CanvasBlack,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    return when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
        else -> "$bytes B"
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "Custom Sound"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
    } catch (e: Exception) {
        name = uri.lastPathSegment ?: "Custom Sound"
    }
    return name.substringBeforeLast(".")
}
