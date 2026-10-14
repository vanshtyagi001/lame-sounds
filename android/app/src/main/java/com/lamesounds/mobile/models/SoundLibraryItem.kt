package com.lamesounds.mobile.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SoundLibraryItem(
    val id: String,
    val title: String,
    val category: String = "All",
    val durationSeconds: Double = 0.0,
    val colorHex: String = "#6366F1",
    val gradientHex: String? = null,
    val imagePath: String? = null,
    val isCustom: Boolean = false,
    val source: String = "Built-in",
    val filePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val fileName: String = ""
)
