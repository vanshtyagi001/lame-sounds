package com.lamesounds.mobile.network

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.*
import java.util.concurrent.TimeUnit

@Serializable
data class DiscoveredPc(
    val app: String = "",
    val machineName: String = "",
    val ip: String = "",
    val httpPort: Int = 50050,
    val udpPort: Int = 50051
)

class DiscoveryManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 64
        })
        .connectTimeout(2000, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Executes multi-strategy auto-discovery:
     * 1. Saved host from preferences
     * 2. Windows Mobile Hotspot default gateway (192.168.137.1)
     * 3. Wi-Fi DHCP default gateway
     * 4. UDP Broadcast probe & beacon listener (port 50052)
     * 5. Local subnet sweep
     */
    suspend fun discoverServer(savedHost: String? = null): DiscoveredPc? = withContext(Dispatchers.IO) {
        // 1. Try saved host first if provided
        if (!savedHost.isNullOrBlank() && testHttp(savedHost, 50050)) {
            return@withContext DiscoveredPc(ip = savedHost)
        }

        // 2. Try Windows Mobile Hotspot default IP (192.168.137.1)
        if (testHttp("192.168.137.1", 50050)) {
            return@withContext DiscoveredPc(ip = "192.168.137.1")
        }

        // 3. Try Wi-Fi DHCP Gateway
        val gatewayIp = getWifiGatewayIp()
        if (!gatewayIp.isNullOrBlank() && gatewayIp != "192.168.137.1" && testHttp(gatewayIp, 50050)) {
            return@withContext DiscoveredPc(ip = gatewayIp)
        }

        // 4. Try UDP Broadcast on port 50052
        val udpDiscovered = tryUdpDiscovery()
        if (udpDiscovered != null) {
            return@withContext udpDiscovered
        }

        // 5. Fast sweep of common local subnet IPs
        val subnet = getLocalSubnetPrefix()
        if (subnet != null) {
            val sweepResult = sweepSubnet(subnet)
            if (sweepResult != null) {
                return@withContext sweepResult
            }
        }

        null
    }

    private suspend fun tryUdpDiscovery(): DiscoveredPc? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("LameSoundsMulticast")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 1500
            }

            // Send probe
            val probe = """{"query":"whois_soundboard"}"""
            val probeBytes = probe.toByteArray(Charsets.UTF_8)
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(probeBytes, probeBytes.size, broadcastAddr, 50052)
            socket.send(packet)

            // Receive response
            val buf = ByteArray(1024)
            val respPacket = DatagramPacket(buf, buf.size)
            socket.receive(respPacket)

            val raw = String(respPacket.data, 0, respPacket.length, Charsets.UTF_8)
            val parsed = json.decodeFromString<DiscoveredPc>(raw)
            val resolvedIp = respPacket.address?.hostAddress?.takeIf { it.isNotBlank() && it != "127.0.0.1" }
                ?: parsed.ip.takeIf { it.isNotBlank() && it != "127.0.0.1" }
                ?: ""

            if (resolvedIp.isNotBlank()) {
                return@withContext parsed.copy(ip = resolvedIp)
            }
        } catch (e: Exception) {
            // UDP probe timed out or blocked
        } finally {
            socket?.close()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (e: Exception) { }
        }
        null
    }

    private suspend fun sweepSubnet(subnetPrefix: String): DiscoveredPc? = withContext(Dispatchers.IO) {
        val candidates = (1..254).map { "$subnetPrefix$it" }

        // Process in chunks of 25 to prevent resource exhaustion
        for (chunk in candidates.chunked(25)) {
            val result = coroutineScope {
                val deferreds = chunk.map { ip ->
                    async {
                        if (testHttp(ip, 50050)) DiscoveredPc(ip = ip) else null
                    }
                }
                deferreds.mapNotNull { it.await() }.firstOrNull()
            }
            if (result != null) {
                return@withContext result
            }
        }

        null
    }

    private fun testHttp(host: String, port: Int): Boolean {
        return try {
            val req = Request.Builder()
                .url("http://$host:$port/api/status")
                .get()
                .build()

            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private fun getWifiGatewayIp(): String? {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val dhcp: DhcpInfo = wm.dhcpInfo ?: return null
            val gatewayInt = dhcp.gateway
            if (gatewayInt == 0) return null
            return String.format(
                "%d.%d.%d.%d",
                gatewayInt and 0xff,
                gatewayInt shr 8 and 0xff,
                gatewayInt shr 16 and 0xff,
                gatewayInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun getLocalSubnetPrefix(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        val parts = host.split(".")
                        if (parts.size == 4) {
                            return "${parts[0]}.${parts[1]}.${parts[2]}."
                        }
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }
}
