package com.lamesounds.mobile.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@Serializable
data class UdpPacketPayload(
    val type: String = "play",
    val soundId: String = "",
    val volume: Float = 1.0f,
    val speakerVolume: Float? = null,
    val micVolume: Float? = null
)

class UdpTriggerClient {
    private var socket: DatagramSocket? = null
    private val json = Json { ignoreUnknownKeys = true }

    init {
        try {
            socket = DatagramSocket()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun play(host: String, port: Int, soundId: String, volume: Float = 1.0f) {
        send(host, port, UdpPacketPayload(type = "play", soundId = soundId, volume = volume))
    }

    suspend fun stop(host: String, port: Int, soundId: String) {
        send(host, port, UdpPacketPayload(type = "stop", soundId = soundId))
    }

    suspend fun stopAll(host: String, port: Int) {
        send(host, port, UdpPacketPayload(type = "stop_all"))
    }

    suspend fun setVolume(host: String, port: Int, speakerVolume: Float, micVolume: Float) {
        send(host, port, UdpPacketPayload(type = "volume", speakerVolume = speakerVolume, micVolume = micVolume))
    }

    private suspend fun send(host: String, port: Int, payload: UdpPacketPayload) = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(host)
            val jsonStr = json.encodeToString(payload)
            val bytes = jsonStr.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, address, port)
            socket?.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }
}
