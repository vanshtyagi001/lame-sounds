package com.lamesounds.mobile.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

@Immutable
@Serializable
data class SoundButtonConfig(
    val id: String = UUID.randomUUID().toString(),
    val soundId: String = "",
    val label: String = "Sound",
    val colorHex: String = "#6366F1",
    val gradientHex: String? = "#4F46E5",
    val imagePath: String? = null,
    val shape: ButtonShape = ButtonShape.ROUNDED_RECTANGLE,
    val cornerRadius: Int = 16,
    val textPosition: TextPosition = TextPosition.BOTTOM,
    val fontSizeSp: Int = 13,
    val isBold: Boolean = true,
    val textColorHex: String = "#FFFFFF",
    val icon: String = "volume",
    val row: Int = 0,
    val column: Int = 0
)
