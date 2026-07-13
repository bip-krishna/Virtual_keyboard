package com.visionboard.network.protocol

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.visionboard.network.server.ClientInfo
import com.visionboard.network.server.KeyboardServer
import com.visionboard.network.server.ServerState
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {
    OFFLINE,
    STARTING,
    WAITING,
    CONNECTED,
    ERROR,
}

@Singleton
class ConnectionManager @Inject constructor(
    private val keyboardServer: KeyboardServer,
) {

    companion object {
        private const val TAG = "ConnectionManager"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var managerScope: CoroutineScope? = null
    private var stateObservationJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ClientInfo>>(emptyList())
    val connectedClients: StateFlow<List<ClientInfo>> = _connectedClients.asStateFlow()

    fun startServer(): Flow<ConnectionState> = flow {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.STARTING
        ) {
            emit(_connectionState.value)
            return@flow
        }

        _connectionState.value = ConnectionState.STARTING
        emit(ConnectionState.STARTING)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        managerScope = scope

        try {
            keyboardServer.start().collect { serverState ->
                when (serverState) {
                    ServerState.RUNNING -> {
                        _connectionState.value = ConnectionState.WAITING
                        emit(ConnectionState.WAITING)
                        startClientObservation(scope)
                        Log.i(TAG, "Server running, waiting for connections")
                    }
                    ServerState.ERROR -> {
                        _connectionState.value = ConnectionState.ERROR
                        emit(ConnectionState.ERROR)
                    }
                    ServerState.STOPPED -> {
                        _connectionState.value = ConnectionState.OFFLINE
                        emit(ConnectionState.OFFLINE)
                    }
                    ServerState.STARTING -> {
                        emit(ConnectionState.STARTING)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server failed", e)
            _connectionState.value = ConnectionState.ERROR
            emit(ConnectionState.ERROR)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun stopServer() {
        stateObservationJob?.cancelAndJoin()
        stateObservationJob = null

        keyboardServer.stop()

        _connectionState.value = ConnectionState.OFFLINE
        _connectedClients.value = emptyList()

        managerScope = null
        Log.i(TAG, "Server stopped")
    }

    fun getConnectionInfo(): ConnectionState = _connectionState.value

    fun observeClients(): Flow<List<ClientInfo>> = flow {
        while (true) {
            kotlinx.coroutines.delay(500)
            val clients = keyboardServer.getConnectedClients()
            _connectedClients.value = clients

            if (clients.isNotEmpty() && _connectionState.value == ConnectionState.WAITING) {
                _connectionState.value = ConnectionState.CONNECTED
            } else if (clients.isEmpty() && _connectionState.value == ConnectionState.CONNECTED) {
                _connectionState.value = ConnectionState.WAITING
            }

            emit(clients)
        }
    }.flowOn(Dispatchers.IO)

    fun sendKeyPress(keycode: Int, isDown: Boolean, modifiers: List<Int> = emptyList()) {
        if (_connectionState.value != ConnectionState.CONNECTED &&
            _connectionState.value != ConnectionState.WAITING
        ) {
            Log.w(TAG, "Cannot send key press: not connected (state=${_connectionState.value})")
            return
        }

        val event = KeyEventMessage(
            keycode = keycode,
            isDown = isDown,
            modifiers = modifiers,
        )

        keyboardServer.broadcastKeyEvent(event)
        Log.d(TAG, "Sent key press: keycode=$keycode, down=$isDown, modifiers=$modifiers")
    }

    private fun startClientObservation(scope: CoroutineScope) {
        stateObservationJob?.cancel()
        stateObservationJob = scope.launch {
            observeClients().collect { clients ->
                _connectedClients.value = clients
            }
        }
    }
}
