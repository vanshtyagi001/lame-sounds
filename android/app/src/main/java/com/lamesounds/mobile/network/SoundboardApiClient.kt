package com.lamesounds.mobile.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class PcStatusResponse(
    val status: String = "",
    val machine: String = "",
    val ip: String = "",
    val httpPort: Int = 50050,
    val udpPort: Int = 50051,
    val soundsCount: Int = 0,
    val virtualCableDetected: Boolean = false
)

@Serializable
data class PcSoundClip(
    val id: String,
    val title: String,
    val fileName: String = "",
    val hotkey: String = "",
    val colorHex: String = "#6366F1",
    val gradientHex: String? = null,
    val hasImage: Boolean = false,
    val imagePath: String? = null,
    val durationSeconds: Double = 0.0,
    val fileSizeBytes: Long = 0L,
    val category: String = "General"
)

class SoundboardApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkStatusAndPing(host: String, port: Int): Pair<PcStatusResponse?, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url("http://$host:$port/api/status")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val parsed = json.decodeFromString<PcStatusResponse>(body)
                    Pair(parsed, elapsed)
                } else {
                    Pair(null, 0L)
                }
            }
        } catch (e: Exception) {
            Pair(null, 0L)
        }
    }

    suspend fun fetchPcSounds(host: String, port: Int): List<PcSoundClip> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://$host:$port/api/sounds")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    json.decodeFromString<List<PcSoundClip>>(body)
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun downloadAudioFile(host: String, port: Int, soundId: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://$host:$port/api/sounds/$soundId/file")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.byteStream()?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    destFile.exists() && destFile.length() > 0
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadImageFile(host: String, port: Int, soundId: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://$host:$port/api/sounds/$soundId/image")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.byteStream()?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    destFile.exists() && destFile.length() > 0
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun registerPhoneWithPc(host: String, port: Int, phoneIp: String, phonePort: Int, soundsCount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = """{"phoneIp":"$phoneIp","phonePort":$phonePort,"soundsCount":$soundsCount}"""
            val req = Request.Builder()
                .url("http://$host:$port/api/phone/register")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadAudioFileToPc(host: String, port: Int, file: File, title: String, category: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val fileBytes = file.readBytes()
            val base64 = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
            val escapedTitle = title.replace("\"", "\\\"")
            val escapedCategory = category.replace("\"", "\\\"")
            val escapedFileName = file.name.replace("\"", "\\\"")

            val payload = """{
                "title": "$escapedTitle",
                "fileName": "$escapedFileName",
                "category": "$escapedCategory",
                "fileSizeBytes": ${file.length()},
                "base64Audio": "$base64"
            }"""

            val req = Request.Builder()
                .url("http://$host:$port/api/sounds/upload")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                Pair(resp.isSuccessful, bodyStr)
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Upload error")
        }
    }

    suspend fun triggerHttp(host: String, port: Int, soundId: String, volume: Float = 1.0f): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = """{"soundId":"$soundId","volume":$volume}"""
            val req = Request.Builder()
                .url("http://$host:$port/api/sounds/trigger")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchVolume(host: String, port: Int): Pair<Float, Float>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://$host:$port/api/volume")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val root = json.parseToJsonElement(body)
                    val sObj = root as? kotlinx.serialization.json.JsonObject
                    val speaker = sObj?.get("speakerVolume")?.let {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()
                    } ?: 1.0f
                    val mic = sObj?.get("micVolume")?.let {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()
                    } ?: 1.0f
                    Pair(speaker, mic)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setVolume(host: String, port: Int, speakerVolume: Float, micVolume: Float): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = """{"speakerVolume":$speakerVolume,"micVolume":$micVolume}"""
            val req = Request.Builder()
                .url("http://$host:$port/api/volume")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun playSound(host: String, port: Int, soundId: String, volume: Float = 1.0f): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = """{"soundId":"$soundId","volume":$volume}"""
            val req = Request.Builder()
                .url("http://$host:$port/api/sounds/play")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun stopAll(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://$host:$port/api/sounds/stop")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
