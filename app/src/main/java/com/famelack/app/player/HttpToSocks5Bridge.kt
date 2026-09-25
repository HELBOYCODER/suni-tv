package com.famelack.app.player

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Local HTTP proxy that forwards all traffic through a SOCKS5 port (V2RayNG 10808,
 * FCAE 1819, ...). WebView/Chromium only understands HTTP proxies, so SOCKS-only
 * tunnels (e.g. YouTube channels) need this bridge to get through the filter.
 */
object HttpToSocks5Bridge {
    private const val TAG = "HttpToSocks5Bridge"

    @Volatile private var server: ServerSocket? = null
    @Volatile private var boundSocksPort = -1
    @Volatile var port = -1; private set

    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "suni-http2socks").apply { isDaemon = true } }

    @Synchronized
    fun ensureStarted(socksPort: Int): Int {
        server?.let { if (!it.isClosed && boundSocksPort == socksPort) return port }
        stop()
        if (!isTcpOpen(socksPort)) return -1
        return try {
            val s = ServerSocket()
            s.bind(InetSocketAddress("127.0.0.1", 0))
            server = s
            boundSocksPort = socksPort
            port = s.localPort
            pool.execute {
                while (!s.isClosed) {
                    try {
                        val client = s.accept()
                        pool.execute { handle(client, socksPort) }
                    } catch (_: Exception) { break }
                }
            }
            Log.i(TAG, "HTTP->SOCKS5 bridge listening on :$port -> 127.0.0.1:$socksPort")
            port
        } catch (e: Exception) {
            Log.e(TAG, "bridge start failed", e)
            -1
        }
    }

    @Synchronized
    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
        port = -1
        boundSocksPort = -1
    }

    fun isTcpOpen(port: Int, timeoutMs: Int = 300): Boolean =
        try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), timeoutMs) }; true } catch (_: Exception) { false }

    private fun socksSocket(socksPort: Int): Socket =
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))

    private fun readLine(inp: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = inp.read()
            if (c == -1) return if (b.size() == 0) null else b.toString("ISO-8859-1")
            if (c == '\n'.code) break
            if (c != '\r'.code) b.write(c)
        }
        return b.toString("ISO-8859-1")
    }

    private fun handle(client: Socket, socksPort: Int) {
        client.use {
            try {
                val reqLine = readLine(it.getInputStream()) ?: return
                val parts = reqLine.split(" ")
                if (parts.size < 2) return
                if (parts[0].equals("CONNECT", true)) {
                    // TLS tunnel (https://...): relay raw bytes both ways.
                    val hostPort = parts[1]
                    val host = hostPort.substringBefore(":")
                    val portNum = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443
                    val upstream = socksSocket(socksPort)
                    upstream.connect(InetSocketAddress(host, portNum), 15_000)
                    it.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    it.getOutputStream().flush()
                    val pump = Executors.newFixedThreadPool(2)
                    pump.execute { runCatching { upstream.getInputStream().copyTo(it.getOutputStream()) }; runCatching { it.close() } }
                    pump.execute { runCatching { it.getInputStream().copyTo(upstream.getOutputStream()) }; runCatching { upstream.close() } }
                    pump.shutdown()
                    return
                }
                // Plain HTTP: read headers, re-issue through SOCKS.
                var line = readLine(it.getInputStream())
                var hostHeader = ""
                while (!line.isNullOrBlank()) {
                    if (line.startsWith("Host:", true)) hostHeader = line.substringAfter(":").trim()
                    line = readLine(it.getInputStream())
                }
                val raw = parts[1]
                val target = if (raw.startsWith("http://")) raw.substring(7) else "$hostHeader$raw"
                val host = target.substringBefore(":").substringBefore("/")
                val portCandidate = target.substringAfter(":", "")
                val portNum = portCandidate.takeWhile { c -> c.isDigit() }
                    .toIntOrNull() ?: 80
                val rest = target.removePrefix(host).removePrefix(":$portNum")
                val path = if (rest.startsWith("/")) rest else "/$rest"
                if (host.isBlank()) return
                val upstream = socksSocket(socksPort)
                upstream.connect(InetSocketAddress(host, portNum), 15_000)
                val req = StringBuilder()
                req.append("${parts[0]} $path HTTP/1.1\r\n")
                req.append("Host: $host\r\n")
                req.append("Accept-Encoding: identity\r\nConnection: close\r\n\r\n")
                upstream.getOutputStream().write(req.toString().toByteArray())
                upstream.getOutputStream().flush()
                upstream.getInputStream().copyTo(it.getOutputStream())
                upstream.close()
            } catch (e: Exception) {
                Log.d(TAG, "bridge request failed: ${e.message}")
            }
        }
    }
}
