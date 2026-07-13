package com.visionboard.network.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "WebSocketServer"
private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
private const val MAX_FRAME_PAYLOAD = 16 * 1024 * 1024

enum class WebSocketOpcode(val value: Int) {
    CONTINUATION(0x0),
    TEXT(0x1),
    BINARY(0x2),
    CLOSE(0x8),
    PING(0x9),
    PONG(0xA);

    companion object {
        fun fromValue(value: Int): WebSocketOpcode? =
            entries.firstOrNull { it.value == value }
    }
}

data class WebSocketFrame(
    val opcode: WebSocketOpcode,
    val payload: ByteArray,
    val isFinal: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebSocketFrame) return false
        return opcode == other.opcode &&
                payload.contentEquals(other.payload) &&
                isFinal == other.isFinal
    }

    override fun hashCode(): Int {
        var result = opcode.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + isFinal.hashCode()
        return result
    }
}

data class WebSocketClient(
    val id: String = UUID.randomUUID().toString(),
    val socket: Socket,
    val inputStream: InputStream,
    val outputStream: OutputStream,
    var isAlive: Boolean = true,
    var lastPongTime: Long = System.currentTimeMillis(),
) {
    private val writeLock = Any()

    suspend fun sendRaw(frame: WebSocketFrame) = withContext(Dispatchers.IO) {
        synchronized(writeLock) {
            try {
                writeFrame(frame)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send frame to ${socket.remoteSocketAddress}", e)
                isAlive = false
            }
        }
    }

    private fun writeFrame(frame: WebSocketFrame) {
        val output = outputStream
        val payloadSize = frame.payload.size

        var firstByte = 0x80.toByte().toInt() or (frame.opcode.value and 0x0F)
        if (!frame.isFinal) {
            firstByte = firstByte and 0x7F
        }
        output.write(firstByte)

        if (payloadSize < 126) {
            output.write(payloadSize)
        } else if (payloadSize <= 0xFFFF) {
            output.write(126)
            output.write((payloadSize shr 8) and 0xFF)
            output.write(payloadSize and 0xFF)
        } else {
            output.write(127)
            val sizeBytes = ByteArray(8)
            var temp = payloadSize.toLong()
            for (i in 7 downTo 0) {
                sizeBytes[i] = (temp and 0xFF).toByte()
                temp = temp shr 8
            }
            output.write(sizeBytes)
        }

        output.write(frame.payload)
        output.flush()
    }

    fun close() {
        try {
            isAlive = false
            inputStream.close()
            outputStream.close()
            socket.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing client $id", e)
        }
    }
}

class WebSocketServerImpl {

    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap<String, WebSocketClient>()
    private val clientsLock = Any()

    val clientCount: Int get() = clients.size

    fun getClients(): List<WebSocketClient> =
        synchronized(clientsLock) { clients.values.toList() }

    suspend fun start(port: Int) = withContext(Dispatchers.IO) {
        serverSocket = ServerSocket(port).apply {
            reuseAddress = true
            soTimeout = 0
        }
        Log.i(TAG, "WebSocket server started on port $port")
    }

    fun stop() {
        synchronized(clientsLock) {
            clients.values.forEach { it.close() }
            clients.clear()
        }
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        Log.i(TAG, "WebSocket server stopped")
    }

    suspend fun acceptConnection(): WebSocketClient? = withContext(Dispatchers.IO) {
        try {
            val socket = serverSocket?.accept() ?: return@withContext null
            socket.soTimeout = 30_000
            socket.tcpNoDelay = true
            socket.keepAlive = true

            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()

            val client = WebSocketClient(
                socket = socket,
                inputStream = inputStream,
                outputStream = outputStream,
            )

            val handshakeSuccess = performHandshake(inputStream, outputStream)
            if (!handshakeSuccess) {
                Log.w(TAG, "WebSocket handshake failed for ${socket.remoteSocketAddress}")
                client.close()
                return@withContext null
            }

            client.lastPongTime = System.currentTimeMillis()
            synchronized(clientsLock) {
                clients[client.id] = client
            }
            Log.i(TAG, "Client connected: ${client.id} from ${socket.remoteSocketAddress}")
            client
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting connection", e)
            null
        }
    }

    fun removeClient(clientId: String) {
        synchronized(clientsLock) {
            clients.remove(clientId)
        }
    }

    private fun performHandshake(input: InputStream, output: OutputStream): Boolean {
        val reader = input.bufferedReader()
        val requestLine = reader.readLine() ?: return false

        if (!requestLine.startsWith("GET") || !requestLine.contains("/")) {
            return false
        }

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break

            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }

        val webSocketKey = headers["sec-websocket-key"] ?: return false
        val version = headers["sec-websocket-version"]
        if (version != "13") {
            val response = buildString {
                append("HTTP/1.1 400 Bad Request\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("\r\n")
            }
            output.write(response.toByteArray())
            output.flush()
            return false
        }

        val acceptKey = computeAcceptKey(webSocketKey)

        val response = buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: $acceptKey\r\n")
            append("\r\n")
        }
        output.write(response.toByteArray())
        output.flush()
        return true
    }

    private fun computeAcceptKey(clientKey: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val combined = "$clientKey$WEBSOCKET_GUID"
        val hash = sha1.digest(combined.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    suspend fun readFrame(client: WebSocketClient): WebSocketFrame? = withContext(Dispatchers.IO) {
        try {
            if (!client.isAlive || client.socket.isClosed) {
                return@withContext null
            }

            val input = client.inputStream

            val firstByte = input.read()
            if (firstByte == -1) return@withContext null

            val secondByte = input.read()
            if (secondByte == -1) return@withContext null

            val isFinal = (firstByte and 0x80) != 0
            val opcodeValue = firstByte and 0x0F
            val opcode = WebSocketOpcode.fromValue(opcodeValue) ?: return@withContext null

            val isMasked = (secondByte and 0x80) != 0
            var payloadLength = (secondByte and 0x7F).toLong()

            when (payloadLength) {
                126 -> {
                    val b1 = input.read()
                    val b2 = input.read()
                    if (b1 == -1 || b2 == -1) return@withContext null
                    payloadLength = ((b1 shl 8) or b2).toLong()
                }
                127 -> {
                    val sizeBytes = ByteArray(8)
                    var bytesRead = 0
                    while (bytesRead < 8) {
                        val read = input.read(sizeBytes, bytesRead, 8 - bytesRead)
                        if (read == -1) return@withContext null
                        bytesRead += read
                    }
                    payloadLength = 0
                    for (i in 0..7) {
                        payloadLength = (payloadLength shl 8) or (sizeBytes[i].toInt() and 0xFF).toLong()
                    }
                }
            }

            if (payloadLength > MAX_FRAME_PAYLOAD) {
                Log.w(TAG, "Frame too large: $payloadLength bytes from ${client.id}")
                return@withContext null
            }

            val mask = if (isMasked) {
                val maskBytes = ByteArray(4)
                var bytesRead = 0
                while (bytesRead < 4) {
                    val read = input.read(maskBytes, bytesRead, 4 - bytesRead)
                    if (read == -1) return@withContext null
                    bytesRead += read
                }
                maskBytes
            } else {
                null
            }

            val payload = ByteArray(payloadLength.toInt())
            if (payloadLength > 0) {
                var totalRead = 0
                while (totalRead < payload.size) {
                    val read = input.read(payload, totalRead, payload.size - totalRead)
                    if (read == -1) return@withContext null
                    totalRead += read
                }
            }

            if (isMasked && mask != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }

            when (opcode) {
                WebSocketOpcode.PING -> {
                    client.sendRaw(WebSocketFrame(WebSocketOpcode.PONG, payload))
                    readFrame(client)
                }
                WebSocketOpcode.PONG -> {
                    client.lastPongTime = System.currentTimeMillis()
                    readFrame(client)
                }
                WebSocketOpcode.CLOSE -> {
                    val closePayload = if (payload.size >= 2) {
                        val statusCode = ((payload[0].toInt() and 0xFF) shl 8) or
                                (payload[1].toInt() and 0xFF)
                        val reason = if (payload.size > 2) {
                            String(payload, 2, payload.size - 2, Charsets.UTF_8)
                        } else ""
                        Log.i(TAG, "Close frame from ${client.id}: $statusCode $reason")
                        if (statusCode == 0) ByteArray(0) else payload.copyOfRange(0, 2)
                    } else {
                        ByteArray(0)
                    }
                    try {
                        client.sendRaw(WebSocketFrame(WebSocketOpcode.CLOSE, closePayload))
                    } catch (_: Exception) {}
                    client.isAlive = false
                    null
                }
                WebSocketOpcode.CONTINUATION -> {
                    Log.w(TAG, "Unexpected continuation frame from ${client.id}")
                    readFrame(client)
                }
                else -> WebSocketFrame(opcode, payload, isFinal)
            }
        } catch (e: java.io.EOFException) {
            Log.i(TAG, "Client ${client.id} disconnected (EOF)")
            client.isAlive = false
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading frame from ${client.id}", e)
            client.isAlive = false
            null
        }
    }

    suspend fun sendFrame(client: WebSocketClient, frame: WebSocketFrame) {
        client.sendRaw(frame)
    }

    suspend fun sendText(client: WebSocketClient, text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        client.sendRaw(WebSocketFrame(WebSocketOpcode.TEXT, payload))
    }

    suspend fun broadcast(frame: WebSocketFrame) {
        val snapshot = getClients()
        snapshot.filter { it.isAlive }.forEach { client ->
            sendFrame(client, frame)
        }
    }

    suspend fun broadcastText(text: String) {
        val snapshot = getClients()
        snapshot.filter { it.isAlive }.forEach { client ->
            sendText(client, text)
        }
    }

    fun removeDeadClients(): List<String> {
        val deadClients = mutableListOf<String>()
        synchronized(clientsLock) {
            val iterator = clients.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!entry.value.isAlive || entry.value.socket.isClosed) {
                    entry.value.close()
                    iterator.remove()
                    deadClients.add(entry.key)
                }
            }
        }
        return deadClients
    }
}

class OkHttpWebSocketClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build(),
) {
    fun connect(
        url: String,
        onMessage: (String) -> Unit,
        onOpen: (WebSocket) -> Unit,
        onFailure: (Throwable) -> Unit,
        onClosed: (Int, String) -> Unit,
    ): WebSocket {
        val request = Request.Builder()
            .url(url)
            .build()

        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpen(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onClosed(code, reason)
            }
        })
    }
}
