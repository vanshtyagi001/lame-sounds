package com.lamesounds.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
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
import com.lamesounds.mobile.models.ButtonShape
import com.lamesounds.mobile.models.SoundButtonConfig
import com.lamesounds.mobile.models.TextPosition
import com.lamesounds.mobile.ui.theme.*

@Composable
fun EditButtonDialog(
    button: SoundButtonConfig,
    onBrowseLibrary: () -> Unit = {},
    onSave: (SoundButtonConfig) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(button.label) }
    var soundId by remember { mutableStateOf(button.soundId) }
    var selectedShape by remember { mutableStateOf(button.shape) }
    var selectedTextPosition by remember { mutableStateOf(button.textPosition) }
    var selectedColor by remember { mutableStateOf(button.colorHex) }

    val presetColors = listOf(
        "#EF4444", "#F59E0B", "#10B981", "#3B82F6",
        "#6366F1", "#8B5CF6", "#EC4899", "#14B8A6"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, HairlineBorder),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "CUSTOMIZE BUTTON",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.03).em,
                    color = InkWhite
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Label Field
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("BUTTON LABEL", fontSize = 10.sp, letterSpacing = (-0.01).em) },
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

                Spacer(modifier = Modifier.height(12.dp))

                // Sound ID Field
                OutlinedTextField(
                    value = soundId,
                    onValueChange = { soundId = it },
                    label = { Text("SOUND ID / CLIP KEY", fontSize = 10.sp, letterSpacing = (-0.01).em) },
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

                Button(
                    onClick = onBrowseLibrary,
                    shape = RoundedCornerShape(9999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceCard,
                        contentColor = InkWhite
                    ),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CHOOSE FROM AUDIO LIBRARY", fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Shape Selector: Rectangle, Rounded Rect, Circle, Hexagon
                Text(
                    text = "GEOMETRY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em,
                    color = InkFaint
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ButtonShape.values().forEach { shape ->
                        val isSelected = selectedShape == shape
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9999.dp))
                                .background(if (isSelected) InkWhite else SurfaceCard)
                                .border(1.dp, if (isSelected) InkWhite else HairlineBorder, RoundedCornerShape(9999.dp))
                                .clickable { selectedShape = shape }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (shape) {
                                    ButtonShape.RECTANGLE -> "Rect"
                                    ButtonShape.ROUNDED_RECTANGLE -> "Round"
                                    ButtonShape.CIRCLE -> "Circle"
                                    ButtonShape.HEXAGON -> "Hex"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.02).em,
                                color = if (isSelected) CanvasBlack else InkMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Text Position: Top, Center, Bottom
                Text(
                    text = "LABEL POSITION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em,
                    color = InkFaint
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextPosition.values().forEach { pos ->
                        val isSelected = selectedTextPosition == pos
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9999.dp))
                                .background(if (isSelected) InkWhite else SurfaceCard)
                                .border(1.dp, if (isSelected) InkWhite else HairlineBorder, RoundedCornerShape(9999.dp))
                                .clickable { selectedTextPosition = pos }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pos.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.02).em,
                                color = if (isSelected) CanvasBlack else InkMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Color Palette
                Text(
                    text = "CARTRIDGE ACCENT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em,
                    color = InkFaint
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetColors.forEach { hex ->
                        val color = Color(hex.removePrefix("#").toLong(16) or 0xFF000000)
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(2.dp, InkWhite, CircleShape)
                                    else Modifier.border(1.dp, HairlineBorder, CircleShape)
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDelete,
                        shape = RoundedCornerShape(9999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceCard,
                            contentColor = StatusRed
                        ),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DELETE", fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em)
                    }

                    Button(
                        onClick = {
                            onSave(
                                button.copy(
                                    label = label,
                                    soundId = soundId,
                                    shape = selectedShape,
                                    textPosition = selectedTextPosition,
                                    colorHex = selectedColor,
                                    gradientHex = selectedColor
                                )
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(9999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkWhite,
                            contentColor = CanvasBlack
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SAVE", fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em)
                    }
                }
            }
        }
    }
}
