package com.lamesounds.mobile.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lamesounds.mobile.models.ConnectionState
import com.lamesounds.mobile.ui.theme.*

@Composable
fun ConnectionTopBar(
    state: ConnectionState,
    onAutoConnectClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CanvasBlack,
        border = BorderStroke(1.dp, HairlineBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = if (isLandscape) 14.dp else 20.dp, vertical = if (isLandscape) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Logo with hardware status indicator & PC host info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular App Logo with LED status indicator
                Box(
                    modifier = Modifier.size(if (isLandscape) 30.dp else 34.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Image(
                        painter = painterResource(id = com.lamesounds.mobile.R.drawable.app_logo),
                        contentDescription = "LameSounds Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                    if (state.isAutoDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = InkWhite
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (state.isConnected) StatusGreen else StatusRed)
                                .border(1.5.dp, CanvasBlack, CircleShape)
                        )
                    }
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when {
                                state.isAutoDiscovering -> "DISCOVERING..."
                                state.isConnected -> "CONNECTED"
                                else -> "OFFLINE"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em,
                            color = when {
                                state.isAutoDiscovering -> InkWhite
                                state.isConnected -> StatusGreen
                                else -> StatusRed
                            }
                        )

                        if (state.isConnected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(SurfaceCard)
                                    .border(1.dp, HairlineBorder, RoundedCornerShape(9999.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${state.latencyMs} ms",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.02).em,
                                    color = when {
                                        state.latencyMs < 30 -> StatusGreen
                                        state.latencyMs < 80 -> StatusAmber
                                        else -> StatusRed
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = if (state.isAutoDiscovering) "Searching Wi-Fi / Hotspot" else state.host,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.01).em,
                        color = InkMuted
                    )
                }
            }

            // Right: Actions with isolated hardware-accelerated spin animation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onAutoConnectClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    AutoConnectRefreshIcon(isDiscovering = state.isAutoDiscovering)
                }

                IconButton(
                    onClick = onLibraryClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = "Audio Library",
                        tint = InkMuted
                    )
                }

                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = InkMuted
                    )
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = InkMuted
                    )
                }
            }
        }
    }
}

/**
 * Isolated icon composable that only starts and reads the infinite animation
 * when actively discovering. Uses graphicsLayer to avoid layout and recomposition invalidations.
 */
@Composable
private fun AutoConnectRefreshIcon(
    isDiscovering: Boolean,
    modifier: Modifier = Modifier
) {
    if (isDiscovering) {
        val infiniteTransition = rememberInfiniteTransition(label = "spin")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Scanning",
            tint = InkWhite,
            modifier = modifier.graphicsLayer { rotationZ = rotation }
        )
    } else {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Scan PC",
            tint = InkMuted,
            modifier = modifier
        )
    }
}
