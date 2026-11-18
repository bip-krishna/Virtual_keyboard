package com.visionboard.network.server

import android.util.Log
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.visionboard.network.protocol.ConnectionAck
import com.visionboard.network.protocol.ErrorMessage
import com.visionboard.network.protocol.HeartbeatMessage
import com.visionboard.network.protocol.KeyEventMessage
import com.visionboard.network.protocol.ProtocolConstants
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

data class ClientInfo(
    val id: String,
    val address: String,
    val connectedAt: Long,
    val lastHeartbeat: Long,
)

@Singleton
class KeyboardServer @Inject constructor() {

    companion object {
        private const val TAG = "KeyboardServer"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val wsServer = WebSocketServerImpl()
    private var serverScope: CoroutineScope? = null
    private var serverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var cleanupJob: Job? = null

    private val _state = MutableStateFlow(ServerState.STOPPED)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _clientCount = MutableStateFlow(0)
    val clientCount: StateFlow<Int> = _clientCount.asStateFlow()

    private val clientInfos = mutableMapOf<String, ClientInfo>()
    private val clientHeartbeatJobs = mutableMapOf<String, Job>()
    private val clientReadJobs = mutableMapOf<String, Job>()

    fun start(port: Int = ProtocolConstants.DEFAULT_PORT): Flow<ServerState> = flow {
        if (_state.value == ServerState.RUNNING) {
            emit(ServerState.RUNNING)
            return@flow
        }

        _state.value = ServerState.STARTING
        emit(ServerState.STARTING)

        try {
            wsServer.start(port)

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            serverScope = scope

            serverJob = scope.launch {
                acceptClientsLoop()
            }

            heartbeatJob = scope.launch {
                heartbeatLoop()
            }

            cleanupJob = scope.launch {
                cleanupLoop()
            }

            _state.value = ServerState.RUNNING
            emit(ServerState.RUNNING)
            Log.i(TAG, "Keyboard server started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            _state.value = ServerState.ERROR
            emit(ServerState.ERROR)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun stop() {
        Log.i(TAG, "Stopping keyboard server")

        serverJob?.cancelAndJoin()
        heartbeatJob?.cancelAndJoin()
        cleanupJob?.cancelAndJoin()
        serverScope?.cancel()
        serverScope = null

        synchronized(clientHeartbeatJobs) {
            clientHeartbeatJobs.values.forEach { it.cancel() }
            clientHeartbeatJobs.clear()
        }
        synchronized(clientReadJobs) {
            clientReadJobs.values.forEach { it.cancel() }
            clientReadJobs.clear()
        }

        wsServer.stop()
        synchronized(clientInfos) { clientInfos.clear() }
        _clientCount.value = 0
        _state.value = ServerState.STOPPED
    }

    fun broadcastKeyEvent(event: KeyEventMessage) {
        if (_state.value != ServerState.RUNNING) {
            Log.w(TAG, "Cannot broadcast: server not running")
            return
        }

        val scope = serverScope ?: return
        scope.launch {
            try {
                val message = json.encodeToString(event)
                val frame = WebSocketFrame(WebSocketOpcode.TEXT, message.toByteArray(Charsets.UTF_8))
                wsServer.broadcast(frame)
                Log.d(TAG, "Broadcast key event: keycode=${event.keycode}, down=${event.isDown}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast key event", e)
            }
        }
    }

    fun getConnectedClients(): List<ClientInfo> =
        synchronized(clientInfos) { clientInfos.values.toList() }

    private suspend fun acceptClientsLoop() {
        while (true) {
            val client = wsServer.acceptConnection() ?: continue
            handleNewClient(client)
        }
    }

    private fun handleNewClient(client: WebSocketClient) {
        val clientAddress = client.socket.remoteSocketAddress?.toString() ?: "unknown"
        val info = ClientInfo(
            id = client.id,
            address = clientAddress,
            connectedAt = System.currentTimeMillis(),
            lastHeartbeat = System.currentTimeMillis(),
        )
        synchronized(clientInfos) {
            if (clientInfos.size >= ProtocolConstants.MAX_CLIENTS) {
                val scope = serverScope ?: return
                scope.launch {
                    val errorMsg = json.encodeToString(
                        ErrorMessage(
                            code = ProtocolConstants.ERROR_SERVER_FULL,
                            message = "Server is full. Max ${ProtocolConstants.MAX_CLIENTS} clients."
                        )
                    )
                    wsServer.sendText(client, errorMsg)
                    val closeFrame = WebSocketFrame(WebSocketOpcode.CLOSE, ByteArray(0))
                    client.sendRaw(closeFrame)
                    client.close()
                }
                return
            }
            clientInfos[client.id] = info
        }
        _clientCount.value = wsServer.clientCount

        val scope = serverScope ?: return

        val ack = json.encodeToString(ConnectionAck(deviceId = client.id))
        scope.launch {
            wsServer.sendText(client, ack)
        }

        val heartbeatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val heartbeatJob = heartbeatScope.launch {
            while (true) {
                delay(ProtocolConstants.HEARTBEAT_INTERVAL_MS)
                try {
                    val hb = json.encodeToString(HeartbeatMessage())
                    wsServer.sendText(client, hb)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send heartbeat to ${client.id}", e)
                    break
                }
            }
        }
        synchronized(clientHeartbeatJobs) {
            clientHeartbeatJobs[client.id] = heartbeatJob
        }

        val readJob = scope.launch {
            readClientLoop(client)
        }
        synchronized(clientReadJobs) {
            clientReadJobs[client.id] = readJob
        }
    }

    private suspend fun readClientLoop(client: WebSocketClient) {
        try {
            while (client.isAlive && isActive) {
                val frame = wsServer.readFrame(client) ?: break
                handleClientMessage(client, frame)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client read loop error for ${client.id}", e)
        } finally {
            removeClient(client)
        }
    }

    private suspend fun handleClientMessage(client: WebSocketClient, frame: WebSocketFrame) {
        when (frame.opcode) {
            WebSocketOpcode.TEXT -> {
                val text = String(frame.payload, Charsets.UTF_8)
                Log.d(TAG, "Received from ${client.id}: $text")
                synchronized(clientInfos) {
                    val info = clientInfos[client.id]
                    if (info != null) {
                        clientInfos[client.id] = info.copy(lastHeartbeat = System.currentTimeMillis())
                    }
                }
            }
            WebSocketOpcode.BINARY -> {
                Log.d(TAG, "Binary message from ${client.id}: ${frame.payload.size} bytes")
            }
            else -> {}
        }
    }

    private fun removeClient(client: WebSocketClient) {
        client.isAlive = false
        wsServer.removeClient(client.id)

        synchronized(clientInfos) { clientInfos.remove(client.id) }
        synchronized(clientHeartbeatJobs) {
            clientHeartbeatJobs.remove(client.id)?.cancel()
        }
        synchronized(clientReadJobs) {
            clientReadJobs.remove(client.id)?.cancel()
        }

        client.close()
        _clientCount.value = wsServer.clientCount
        Log.i(TAG, "Client removed: ${client.id}")
    }

    private suspend fun heartbeatLoop() {
        while (true) {
            delay(ProtocolConstants.HEARTBEAT_TIMEOUT_MS)
            val now = System.currentTimeMillis()
            val snapshot = getConnectedClients()
            val timedOut = snapshot.filter {
                (now - it.lastHeartbeat) > ProtocolConstants.HEARTBEAT_TIMEOUT_MS
            }
            if (timedOut.isNotEmpty()) {
                Log.w(TAG, "Heartbeat timeout for ${timedOut.size} clients")
                timedOut.forEach { info ->
                    val client = wsServer.getClients().find { it.id == info.id }
                    if (client != null) {
                        removeClient(client)
                    }
                }
            }
        }
    }

    private suspend fun cleanupLoop() {
        while (true) {
            delay(5_000)
            val deadIds = wsServer.removeDeadClients()
            deadIds.forEach { id ->
                synchronized(clientInfos) { clientInfos.remove(id) }
                synchronized(clientHeartbeatJobs) {
                    clientHeartbeatJobs.remove(id)?.cancel()
                }
                synchronized(clientReadJobs) {
                    clientReadJobs.remove(id)?.cancel()
                }
            }
            if (deadIds.isNotEmpty()) {
                _clientCount.value = wsServer.clientCount
            }
        }
    }

    private val isActive: Boolean
        get() = serverScope?.isActive == true
}
