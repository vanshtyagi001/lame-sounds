package com.lamesounds.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lamesounds.mobile.models.SoundButtonConfig
import com.lamesounds.mobile.models.SoundSet
import com.lamesounds.mobile.ui.theme.*

@Composable
fun SearchModal(
    soundSets: List<SoundSet>,
    onSoundTap: (SoundButtonConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    // Collect all sounds from all sets
    val allButtons = remember(soundSets) {
        soundSets.flatMap { set ->
            set.buttons.filter { it.soundId.isNotEmpty() || it.label.isNotBlank() }
        }
    }

    val filteredButtons = remember(searchQuery, allButtons) {
        if (searchQuery.isBlank()) allButtons
        else allButtons.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, HairlineBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Pill Search Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search voice lines...", fontSize = 13.sp, color = InkMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = InkWhite,
                            unfocusedTextColor = InkWhite,
                            focusedContainerColor = CanvasBlack,
                            unfocusedContainerColor = CanvasBlack,
                            focusedBorderColor = InkWhite,
                            unfocusedBorderColor = HairlineBorder
                        ),
                        shape = RoundedCornerShape(9999.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = InkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "${filteredButtons.size} CLIPS FOUND",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em,
                    color = InkFaint,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredButtons) { btn ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CanvasBlack)
                                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    onSoundTap(btn)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9999.dp))
                                        .background(InkWhite),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = CanvasBlack,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = btn.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.02).em,
                                    color = InkWhite
                                )
                            }
                            Text(
                                text = "TRIGGER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.01).em,
                                color = InkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
