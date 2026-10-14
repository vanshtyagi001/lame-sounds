package com.lamesounds.mobile.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ConnectionState(
    val host: String = "192.168.1.100",
    val httpPort: Int = 50050,
    val udpPort: Int = 50051,
    val isConnected: Boolean = false,
    val latencyMs: Long = 0L,
    val speakerVolume: Float = 1.0f,
    val micVolume: Float = 1.0f,
    val isAutoDiscovering: Boolean = false,
    val autoConnectEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true
)

@Immutable
@Serializable
data class SoundboardProfile(
    val version: String = "1.0",
    val profileName: String = "Default Profile",
    val soundSets: List<SoundSet> = emptyList()
)
