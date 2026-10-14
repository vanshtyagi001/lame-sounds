package com.lamesounds.mobile.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

@Immutable
@Serializable
data class SoundSet(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Sound Set",
    val rows: Int = 3,
    val columns: Int = 3,
    val backgroundColorHex: String = "#0B0F19",
    val backgroundGradientHex: String? = "#131B2E",
    val buttons: List<SoundButtonConfig> = emptyList()
) {
    val totalSlots: Int get() = rows * columns
}
