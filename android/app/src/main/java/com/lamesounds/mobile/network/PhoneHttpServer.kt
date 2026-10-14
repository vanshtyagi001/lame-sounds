package com.lamesounds.mobile.network

import com.lamesounds.mobile.models.SoundLibraryItem
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

@Serializable
data class PhoneSoundExport(
    val id: String,
    val title: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationSeconds: Double,
    val category: String = "Phone"
)

class PhoneHttpServer(
    private val getSounds: () -> List<SoundLibraryItem>
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    var port: Int = 50055
        private set

    val isRunning: Boolean
        get() = serverSocket != null && !serverSocket!!.isClosed

    fun start(preferredPort: Int = 50055) {
        stop()
        serverJob = scope.launch {
            try {
                // Try preferred port or fallback to free port
                serverSocket = try {
                    ServerSocket(preferredPort)
                } catch (e: Exception) {
                    ServerSocket(0)
                }
                port = serverSocket!!.localPort

                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        launch { handleClient(client) }
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { s ->
            s.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val output = s.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val rawUrl = parts[1]
            val path = rawUrl.split("?")[0].trimEnd('/')

            // Consume remaining headers
            var headerLine: String?
            while (!reader.readLine().also { headerLine = it }.isNullOrEmpty()) {
                // read all headers
            }

            if (method == "GET" && (path == "/api/phone/sounds" || path == "/api/sounds")) {
                val sounds = getSounds().filter { it.isCustom && it.filePath != null && File(it.filePath).exists() }
                val exportList = sounds.map {
                    val file = File(it.filePath!!)
                    PhoneSoundExport(
                        id = it.id,
                        title = it.title,
                        fileName = file.name,
                        fileSizeBytes = if (it.fileSizeBytes > 0) it.fileSizeBytes else file.length(),
                        durationSeconds = it.durationSeconds,
                        category = it.category
                    )
                }
                val body = json.encodeToString(exportList)
                sendResponse(output, 200, "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))
            } else if (method == "GET" && path.startsWith("/api/phone/sounds/") && path.endsWith("/file")) {
                val segs = path.split("/")
                val soundId = if (segs.size >= 5) segs[4] else null
                val item = getSounds().firstOrNull { it.id == soundId }

                if (item?.filePath != null) {
                    val file = File(item.filePath)
                    if (file.exists()) {
                        val ext = file.extension.lowercase()
                        val mime = when (ext) {
                            "wav" -> "audio/wav"
                            "ogg" -> "audio/ogg"
                            "flac" -> "audio/flac"
                            "aac" -> "audio/aac"
                            "m4a" -> "audio/mp4"
                            else -> "audio/mpeg"
                        }
                        val bytes = file.readBytes()
                        sendResponse(output, 200, mime, bytes)
                        return@withContext
                    }
                }
                sendResponse(output, 404, "application/json; charset=utf-8", "{\"error\":\"File not found\"}".toByteArray(Charsets.UTF_8))
            } else {
                sendResponse(output, 404, "application/json; charset=utf-8", "{\"error\":\"Not found\"}".toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, body: ByteArray) {
        val statusText = when (statusCode) {
            200 -> "OK"
            404 -> "Not Found"
            else -> "Error"
        }
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) { }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }
}
