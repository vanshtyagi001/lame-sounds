package com.lamesounds.mobile.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lamesounds.mobile.ui.theme.*

@Composable
fun BottomVolumeBar(
    speakerVolume: Float,
    micVolume: Float,
    onSpeakerVolumeChange: (Float) -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onStopAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var localSpeakerVol by remember(speakerVolume) { mutableFloatStateOf(speakerVolume) }
    var localMicVol by remember(micVolume) { mutableFloatStateOf(micVolume) }

    var lastSpeakerVol by remember { mutableFloatStateOf(if (speakerVolume > 0f) speakerVolume else 1.0f) }
    var lastMicVol by remember { mutableFloatStateOf(if (micVolume > 0f) micVolume else 1.0f) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CanvasBlack,
        border = BorderStroke(1.dp, HairlineBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Monitor / Headphone Volume
            Column(modifier = Modifier.weight(1f)) {
                val isMuted = localSpeakerVol <= 0.001f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (isMuted) {
                                val restored = lastSpeakerVol.coerceAtLeast(0.15f)
                                localSpeakerVol = restored
                                onSpeakerVolumeChange(restored)
                            } else {
                                lastSpeakerVol = localSpeakerVol
                                localSpeakerVol = 0f
                                onSpeakerVolumeChange(0f)
                            }
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.Default.Headphones,
                            contentDescription = "Monitor",
                            tint = if (isMuted) StatusRed else InkMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "MONITOR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em,
                            color = if (isMuted) StatusRed else InkMuted
                        )
                    }
                    Text(
                        text = if (isMuted) "MUTED" else "${(localSpeakerVol * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em,
                        color = if (isMuted) StatusRed else InkWhite
                    )
                }
                Slider(
                    value = localSpeakerVol,
                    onValueChange = {
                        localSpeakerVol = it
                        if (it > 0f) lastSpeakerVol = it
                        onSpeakerVolumeChange(it)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = if (isMuted) StatusRed else InkWhite,
                        activeTrackColor = if (isMuted) StatusRed else InkWhite,
                        inactiveTrackColor = HairlineBorder
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            // 2. Emergency "STOP" Button
            Surface(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onStopAllClick()
                },
                shape = RoundedCornerShape(8.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.65f)),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop All Sounds",
                        tint = StatusRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 3. Mic / Broadcast Volume (CS2)
            Column(modifier = Modifier.weight(1f)) {
                val isMuted = localMicVol <= 0.001f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (isMuted) {
                                val restored = lastMicVol.coerceAtLeast(0.15f)
                                localMicVol = restored
                                onMicVolumeChange(restored)
                            } else {
                                lastMicVol = localMicVol
                                localMicVol = 0f
                                onMicVolumeChange(0f)
                            }
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Broadcast",
                            tint = if (isMuted) StatusRed else InkMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "BROADCAST",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em,
                            color = if (isMuted) StatusRed else InkMuted
                        )
                    }
                    Text(
                        text = if (isMuted) "MUTED" else "${(localMicVol * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em,
                        color = if (isMuted) StatusRed else InkWhite
                    )
                }
                Slider(
                    value = localMicVol,
                    onValueChange = {
                        localMicVol = it
                        if (it > 0f) lastMicVol = it
                        onMicVolumeChange(it)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = if (isMuted) StatusRed else InkWhite,
                        activeTrackColor = if (isMuted) StatusRed else InkWhite,
                        inactiveTrackColor = HairlineBorder
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

/**
 * Sleek vertical hardware-fader sidebar positioned on the right edge during landscape orientation.
 * Displays Speaker and Mic faders side-by-side for full vertical throw and smooth scrolling/dragging,
 * with a full-width tactile Stop Button anchored at the bottom.
 */
@Composable
fun RightVolumeSidebar(
    speakerVolume: Float,
    micVolume: Float,
    onSpeakerVolumeChange: (Float) -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onStopAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var localSpeakerVol by remember(speakerVolume) { mutableFloatStateOf(speakerVolume) }
    var localMicVol by remember(micVolume) { mutableFloatStateOf(micVolume) }

    var lastSpeakerVol by remember { mutableFloatStateOf(if (speakerVolume > 0f) speakerVolume else 1.0f) }
    var lastMicVol by remember { mutableFloatStateOf(if (micVolume > 0f) micVolume else 1.0f) }

    val isSpeakerMuted = localSpeakerVol <= 0.001f
    val isMicMuted = localMicVol <= 0.001f

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(116.dp),
        color = CanvasBlack,
        border = BorderStroke(1.dp, HairlineBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Side-by-side faders (Speaker on left, Mic on right)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. SPEAKER COLUMN
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Speaker Header (tap to mute)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (isSpeakerMuted) {
                                    val restored = lastSpeakerVol.coerceAtLeast(0.15f)
                                    localSpeakerVol = restored
                                    onSpeakerVolumeChange(restored)
                                } else {
                                    lastSpeakerVol = localSpeakerVol
                                    localSpeakerVol = 0f
                                    onSpeakerVolumeChange(0f)
                                }
                            }
                            .padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeakerMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speaker",
                            tint = if (isSpeakerMuted) StatusRed else InkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "SPEAKER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).em,
                            color = if (isSpeakerMuted) StatusRed else InkMuted
                        )
                        Text(
                            text = if (isSpeakerMuted) "MUTED" else "${(localSpeakerVol * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.02).em,
                            color = if (isSpeakerMuted) StatusRed else InkWhite
                        )
                    }

                    // Speaker Vertical Fader
                    VerticalFader(
                        volume = localSpeakerVol,
                        isMuted = isSpeakerMuted,
                        onVolumeChange = {
                            localSpeakerVol = it
                            if (it > 0f) lastSpeakerVol = it
                            onSpeakerVolumeChange(it)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                // 2. MIC COLUMN
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mic Header (tap to mute)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (isMicMuted) {
                                    val restored = lastMicVol.coerceAtLeast(0.15f)
                                    localMicVol = restored
                                    onMicVolumeChange(restored)
                                } else {
                                    lastMicVol = localMicVol
                                    localMicVol = 0f
                                    onMicVolumeChange(0f)
                                }
                            }
                            .padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = if (isMicMuted) StatusRed else InkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "MIC",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).em,
                            color = if (isMicMuted) StatusRed else InkMuted
                        )
                        Text(
                            text = if (isMicMuted) "MUTED" else "${(localMicVol * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.02).em,
                            color = if (isMicMuted) StatusRed else InkWhite
                        )
                    }

                    // Mic Vertical Fader
                    VerticalFader(
                        volume = localMicVol,
                        isMuted = isMicMuted,
                        onVolumeChange = {
                            localMicVol = it
                            if (it > 0f) lastMicVol = it
                            onMicVolumeChange(it)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. STOP BUTTON AT BOTTOM
            Surface(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onStopAllClick()
                },
                shape = RoundedCornerShape(8.dp),
                color = StatusRed.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop All Audio",
                        tint = StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "STOP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.em,
                        color = StatusRed
                    )
                }
            }
        }
    }
}

/**
 * Tactile, zero-latency vertical fader simulating mixing console hardware.
 */
@Composable
private fun VerticalFader(
    volume: Float,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var componentHeightPx by remember { mutableFloatStateOf(1f) }
    val view = LocalView.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CanvasBlack)
            .onGloballyPositioned { coordinates ->
                componentHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val newVol = (1f - (offset.y / componentHeightPx)).coerceIn(0f, 1f)
                    onVolumeChange(newVol)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount / componentHeightPx
                    val newVol = (volume + delta).coerceIn(0f, 1f)
                    onVolumeChange(newVol)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Track channel
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceDark)
                .border(
                    1.dp,
                    if (isMuted) StatusRed.copy(alpha = 0.5f) else HairlineBorder,
                    RoundedCornerShape(6.dp)
                )
        ) {
            // Active Volume Fill from bottom
            val fillRatio = volume.coerceIn(0f, 1f)
            if (fillRatio > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillRatio)
                        .align(Alignment.BottomCenter)
                        .background(if (isMuted) StatusRed else InkWhite)
                )
            }

            // Tactile thumb line marker at top edge of the filled level
            if (fillRatio > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillRatio)
                        .align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter)
                            .background(CanvasBlack)
                    )
                }
            }
        }
    }
}
