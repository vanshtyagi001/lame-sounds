package com.lamesounds.mobile.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.lamesounds.mobile.models.SoundButtonConfig
import com.lamesounds.mobile.models.SoundSet
import com.lamesounds.mobile.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SoundSetPager(
    soundSets: List<SoundSet>,
    onSoundTap: (SoundButtonConfig) -> Unit,
    onSoundLongPress: (SoundSet, SoundButtonConfig) -> Unit,
    onEmptySlotClick: (SoundSet, SoundButtonConfig) -> Unit = { set, btn -> onSoundLongPress(set, btn) },
    onEditSetClick: (SoundSet) -> Unit,
    onRenameSet: (setId: String, newName: String) -> Unit = { _, _ -> },
    onDeleteButton: (setId: String, buttonId: String) -> Unit = { _, _ -> },
    onReorderButtons: (setId: String, newButtons: List<SoundButtonConfig>) -> Unit = { _, _ -> },
    onCreateNewSetClick: () -> Unit = {},
    onImportToNewSetClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
    isLandscape: Boolean = false
) {
    val view = LocalView.current
    if (soundSets.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("NO SOUND SETS", color = InkMuted, fontSize = 13.sp, letterSpacing = (-0.02).em)
        }
        return
    }

    var isEditMode by remember { mutableStateOf(false) }
    var renamingSet by remember { mutableStateOf<SoundSet?>(null) }

    // soundSets.size + 1 includes the trailing "+ New Empty Set" page
    val totalPages = soundSets.size + 1
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val coroutineScope = rememberCoroutineScope()
    val isNewSetPage = pagerState.currentPage == soundSets.size
    val currentSet = if (!isNewSetPage) soundSets[pagerState.currentPage] else null

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(toastKey) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pager Navigation Header: ←  [NAME] ✎ [3 / ROW] [✓ DONE]  →
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = if (isLandscape) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Prev Arrow
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.size(if (isLandscape) 28.dp else 34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Set",
                        tint = if (pagerState.currentPage > 0) InkWhite else InkFaint
                    )
                }

                // Set Title & Indicators
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(enabled = currentSet != null) {
                        if (isEditMode) {
                            currentSet?.let { renamingSet = it }
                        } else {
                            currentSet?.let { onEditSetClick(it) }
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isNewSetPage) "NEW SET" else (currentSet?.name?.uppercase() ?: ""),
                            fontSize = if (isLandscape) 13.sp else 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.03).em,
                            color = if (isNewSetPage) StatusGreen else InkWhite
                        )

                        // Small edit icon indicator beside name when in edit mode
                        if (isEditMode && !isNewSetPage) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename Set",
                                tint = StatusGreen,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { currentSet?.let { renamingSet = it } }
                            )
                        }

                        // Grid Pill or New Set Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(SurfaceCard)
                                .border(1.dp, if (isNewSetPage) StatusGreen.copy(alpha = 0.5f) else HairlineBorder, RoundedCornerShape(9999.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isNewSetPage) "+ EMPTY" else "${currentSet?.columns ?: 3} / ROW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.02).em,
                                color = if (isNewSetPage) StatusGreen else InkMuted
                            )
                        }
                    }

                    if (!isLandscape) {
                        Spacer(modifier = Modifier.height(5.dp))

                        // Discrete Page Dots with highlight for the new set page
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(totalPages) { index ->
                                val isSelected = pagerState.currentPage == index
                                val isNewPageDot = index == soundSets.size
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 7.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                if (isNewPageDot) StatusGreen else InkWhite
                                            } else {
                                                if (isNewPageDot) StatusGreen.copy(alpha = 0.4f) else HairlineBright
                                            }
                                        )
                                        .clickable {
                                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                        }
                                )
                            }
                        }
                    }
                }

                // Right Group: Edit Mode Toggle Button (Pencil Icon / Done Button) & Next Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isNewSetPage) {
                        if (isEditMode) {
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    isEditMode = false
                                },
                                shape = RoundedCornerShape(9999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusGreen,
                                    contentColor = CanvasBlack
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("✓ DONE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    isEditMode = true
                                },
                                modifier = Modifier.size(if (isLandscape) 28.dp else 32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Set",
                                    tint = InkMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Next Arrow
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < totalPages - 1) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        enabled = pagerState.currentPage < totalPages - 1,
                        modifier = Modifier.size(if (isLandscape) 28.dp else 34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Set",
                            tint = if (pagerState.currentPage < totalPages - 1) InkWhite else InkFaint
                        )
                    }
                }
            }

            var isDraggingCard by remember { mutableStateOf(false) }

            // Discrete Horizontal Swipe Pager with caching & smooth pre-rendering
            HorizontalPager(
                state = pagerState,
                key = { index -> if (index < soundSets.size) soundSets[index].id else "new_empty_set_slot" },
                beyondViewportPageCount = 1,
                userScrollEnabled = !isDraggingCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                if (pageIndex < soundSets.size) {
                    val set = soundSets[pageIndex]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CanvasBlack)
                            .padding(horizontal = if (isLandscape) 8.dp else 14.dp, vertical = if (isLandscape) 4.dp else 10.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        SoundGridLayout(
                            soundSet = set,
                            isEditMode = isEditMode,
                            onSoundTap = onSoundTap,
                            onSoundLongPress = { btn -> onSoundLongPress(set, btn) },
                            onEmptySlotClick = { btn -> onEmptySlotClick(set, btn) },
                            onDeleteButton = { btnId -> onDeleteButton(set.id, btnId) },
                            onReorderButtons = { newButtons -> onReorderButtons(set.id, newButtons) },
                            onSoundRemoved = { soundTitle ->
                                toastMessage = "$soundTitle sound removed"
                                toastKey++
                            },
                            onDragActiveChanged = { isDraggingCard = it },
                            hapticEnabled = hapticEnabled,
                            isLandscape = isLandscape
                        )
                    }
                } else {
                    // New Empty Set Page: lets user import multiple sounds from library or create blank set
                    NewSetEmptyState(
                        setNumber = soundSets.size + 1,
                        onCreateBlankSet = onCreateNewSetClick,
                        onImportSounds = onImportToNewSetClick,
                        isLandscape = isLandscape
                    )
                }
            }
        }

        // Small animated popup animation pill saying "$xyz sound removed"
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = 600f)) { it } + scaleIn(initialScale = 0.88f),
            exit = fadeOut(tween(160)) + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.88f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isLandscape) 14.dp else 24.dp)
                .zIndex(500f)
        ) {
            toastMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = Color(0xFF1E1E20),
                    border = BorderStroke(1.dp, Color(0xFF38383A)),
                    shadowElevation = 14.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(StatusRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = StatusRed,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkWhite,
                            letterSpacing = (-0.01).em
                        )
                    }
                }
            }
        }
    }

    // Modal to Edit Set Name
    renamingSet?.let { set ->
        RenameSetDialog(
            currentName = set.name,
            onSave = { newName ->
                onRenameSet(set.id, newName)
                renamingSet = null
            },
            onDismiss = { renamingSet = null }
        )
    }
}

/**
 * Dialog to edit the name of an existing sound set.
 */
@Composable
private fun RenameSetDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameText by remember { mutableStateOf(currentName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "EDIT SET NAME",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                    color = InkWhite
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    placeholder = { Text("Set Name...", fontSize = 12.sp, color = InkMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = InkWhite,
                        unfocusedTextColor = InkWhite,
                        focusedBorderColor = StatusGreen,
                        unfocusedBorderColor = HairlineBorder,
                        focusedContainerColor = CanvasBlack,
                        unfocusedContainerColor = CanvasBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(9999.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL", fontSize = 11.sp, color = InkMuted)
                    }

                    Button(
                        onClick = {
                            if (nameText.isNotBlank()) {
                                onSave(nameText.trim())
                            }
                        },
                        shape = RoundedCornerShape(9999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkWhite,
                            contentColor = CanvasBlack
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SAVE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Empty Set landing page displayed when swiping past the last set.
 */
@Composable
private fun NewSetEmptyState(
    setNumber: Int,
    onCreateBlankSet: () -> Unit,
    onImportSounds: () -> Unit,
    isLandscape: Boolean
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(horizontal = if (isLandscape) 24.dp else 20.dp, vertical = if (isLandscape) 8.dp else 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.65f else 0.95f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon Badge
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 42.dp else 52.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard)
                        .border(1.dp, HairlineBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Set",
                        tint = StatusGreen,
                        modifier = Modifier.size(if (isLandscape) 22.dp else 28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 14.dp))

                Text(
                    text = "SOUND SET $setNumber",
                    fontSize = if (isLandscape) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                    color = InkWhite
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "This set is currently empty. Tap below to select and import multiple sounds at once from your library, or start with a blank set.",
                    fontSize = if (isLandscape) 10.sp else 12.sp,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = if (isLandscape) 14.sp else 18.sp
                )

                Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 20.dp))

                // Primary Import from Library Button (multi-select)
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onImportSounds()
                    },
                    shape = RoundedCornerShape(9999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkWhite,
                        contentColor = CanvasBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = if (isLandscape) 6.dp else 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📁 IMPORT SOUNDS FROM LIBRARY",
                        fontSize = if (isLandscape) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Create Blank Set Button
                OutlinedButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onCreateBlankSet()
                    },
                    shape = RoundedCornerShape(9999.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = InkMuted
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = if (isLandscape) 4.dp else 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "+ Create Blank Set",
                        fontSize = if (isLandscape) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundGridLayout(
    soundSet: SoundSet,
    isEditMode: Boolean,
    onSoundTap: (SoundButtonConfig) -> Unit,
    onSoundLongPress: (SoundButtonConfig) -> Unit,
    onEmptySlotClick: (SoundButtonConfig) -> Unit,
    onDeleteButton: (String) -> Unit,
    onReorderButtons: (List<SoundButtonConfig>) -> Unit,
    onSoundRemoved: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDragActiveChanged: (Boolean) -> Unit = {},
    hapticEnabled: Boolean = true,
    isLandscape: Boolean = false
) {
    val view = LocalView.current
    val cols = soundSet.columns.coerceIn(1, 6)

    // Filter only active added sounds
    val activeButtons = remember(soundSet.buttons) {
        soundSet.buttons.filter { it.soundId.isNotBlank() && !it.label.startsWith("+ Slot") }
    }

    // Local mutable list for live auto-aligning drag and drop reordering
    var activeList by remember(activeButtons) { mutableStateOf(activeButtons) }

    // Drag states
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Notify parent pager when drag state changes
    LaunchedEffect(draggingId) {
        onDragActiveChanged(draggingId != null)
    }

    // Update activeList if external soundSet.buttons change and not actively dragging
    LaunchedEffect(activeButtons) {
        if (draggingId == null) {
            activeList = activeButtons
        }
    }

    // Dimensions of each card slot in pixels for drag calculations
    var itemWidthPx by remember { mutableFloatStateOf(1f) }
    var itemHeightPx by remember { mutableFloatStateOf(1f) }

    val spacingDp = if (isLandscape) 8.dp else 10.dp
    val density = LocalDensity.current
    val spacingPx = remember(density, spacingDp) { with(density) { spacingDp.toPx() } }

    // Only 1 "+ Add Sound" slot at the end of the sound list
    val addSlotIndex = activeList.size
    val addSlotConfig = remember(soundSet.id, addSlotIndex, cols) {
        SoundButtonConfig(
            id = "add_slot_${soundSet.id}",
            soundId = "",
            label = "+ Add Sound",
            colorHex = "#161616",
            gradientHex = null,
            row = addSlotIndex / cols,
            column = addSlotIndex % cols
        )
    }

    val allButtons = remember(activeList, addSlotConfig) {
        activeList + addSlotConfig
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = draggingId == null,
        horizontalArrangement = Arrangement.spacedBy(spacingDp),
        verticalArrangement = Arrangement.spacedBy(spacingDp),
        contentPadding = PaddingValues(bottom = if (isLandscape) 16.dp else 32.dp)
    ) {
        items(
            items = allButtons,
            key = { it.id }
        ) { config ->
            val isEmptySlot = config.id.startsWith("add_slot_") || config.soundId.isBlank()
            val isDraggingThis = draggingId == config.id

            val interactionSource = remember(config.id) { MutableInteractionSource() }
            val isPressedThis by interactionSource.collectIsPressedAsState()

            // Upper layer spring elevation & scale
            val dragScale by animateFloatAsState(
                targetValue = if (isDraggingThis) 1.12f else 1.0f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 600f),
                label = "DragScale"
            )

            val dragElevation by animateDpAsState(
                targetValue = if (isDraggingThis) 32.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "DragElevation"
            )

            val dragRotation by animateFloatAsState(
                targetValue = if (isDraggingThis) -2.5f else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "DragRotation"
            )

            Box(
                modifier = Modifier
                    .then(
                        if (!isDraggingThis) {
                            Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = 450f
                                )
                            )
                        } else Modifier
                    )
                    .onGloballyPositioned { coordinates ->
                        if (!isEmptySlot) {
                            val w = coordinates.size.width.toFloat()
                            val h = coordinates.size.height.toFloat()
                            if (w > 10f) itemWidthPx = w
                            if (h > 10f) itemHeightPx = h
                        }
                    }
                    .zIndex(if (isDraggingThis) 100f else 1f)
                    .graphicsLayer {
                        if (isDraggingThis) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = dragScale
                            scaleY = dragScale
                            rotationZ = dragRotation
                            shadowElevation = dragElevation.toPx()
                            shape = RoundedCornerShape(16.dp)
                            clip = false
                        }
                    }
                    .then(
                        if (!isEmptySlot) {
                            Modifier.pointerInput(config.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        draggingId = config.id
                                        dragOffset = Offset.Zero
                                        onDragActiveChanged(true)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount

                                        val stepX = itemWidthPx + spacingPx
                                        val stepY = itemHeightPx + spacingPx

                                        if (stepX > 10f && stepY > 10f) {
                                            val colDelta = (dragOffset.x / stepX).roundToInt()
                                            val rowDelta = (dragOffset.y / stepY).roundToInt()

                                            val currentIndex = activeList.indexOfFirst { it.id == draggingId }
                                            if (currentIndex >= 0 && (colDelta != 0 || rowDelta != 0)) {
                                                val currentRow = currentIndex / cols
                                                val currentCol = currentIndex % cols
                                                val maxRow = (activeList.size - 1) / cols

                                                val targetCol = (currentCol + colDelta).coerceIn(0, cols - 1)
                                                val targetRow = (currentRow + rowDelta).coerceIn(0, maxRow)

                                                val potentialIndex = targetRow * cols + targetCol
                                                val targetIndex = potentialIndex.coerceIn(0, activeList.size - 1)

                                                if (targetIndex != currentIndex) {
                                                    val actualColDelta = (targetIndex % cols) - currentCol
                                                    val actualRowDelta = (targetIndex / cols) - currentRow

                                                    val updated = activeList.toMutableList().apply {
                                                        val item = removeAt(currentIndex)
                                                        add(targetIndex, item)
                                                    }
                                                    activeList = updated
                                                    dragOffset = Offset(
                                                        dragOffset.x - actualColDelta * stepX,
                                                        dragOffset.y - actualRowDelta * stepY
                                                    )
                                                    if (hapticEnabled) {
                                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (hapticEnabled) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        }
                                        draggingId = null
                                        dragOffset = Offset.Zero
                                        onDragActiveChanged(false)
                                        onReorderButtons(activeList)
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = Offset.Zero
                                        onDragActiveChanged(false)
                                        onReorderButtons(activeList)
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                // Main Sound Card
                SoundCard(
                    config = config,
                    isEditMode = isEditMode && !isEmptySlot,
                    isPressed = isPressedThis,
                    isDragging = isDraggingThis,
                    hapticEnabled = hapticEnabled,
                    isLandscape = isLandscape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (isEmptySlot) {
                                onEmptySlotClick(config)
                            } else if (isEditMode) {
                                onSoundLongPress(config)
                            } else {
                                if (hapticEnabled) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                                onSoundTap(config)
                            }
                        }
                )

                // Dedicated, Elevated Edit Mode Badges with Large 44dp Touch Targets
                if (isEditMode && !isEmptySlot) {
                    // Top-Left: Delete Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(44.dp)
                            .zIndex(60f)
                            .clickable(
                                interactionSource = remember(config.id) { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 22.dp)
                            ) {
                                if (hapticEnabled) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                }
                                activeList = activeList.filterNot { it.id == config.id }
                                onDeleteButton(config.id)
                                onSoundRemoved(config.label)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = StatusRed,
                            border = BorderStroke(1.5.dp, CanvasBlack),
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete ${config.label}",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Top-Right: Edit Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(44.dp)
                            .zIndex(60f)
                            .clickable(
                                interactionSource = remember(config.id) { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 22.dp)
                            ) {
                                if (hapticEnabled) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                                onSoundLongPress(config)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = InkWhite,
                            border = BorderStroke(1.5.dp, CanvasBlack),
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit ${config.label}",
                                    tint = CanvasBlack,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
