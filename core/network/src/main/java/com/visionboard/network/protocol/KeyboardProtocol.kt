package com.visionboard.network.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeyEventMessage(
    @EncodeDefault val type: String = "key",
    val keycode: Int,
    val isDown: Boolean,
    val modifiers: List<Int> = emptyList(),
)

@Serializable
data class ConnectionAck(
    @EncodeDefault val type: String = "ack",
    val deviceId: String,
    val version: Int = ProtocolConstants.CURRENT_VERSION,
)

@Serializable
data class HeartbeatMessage(
    @EncodeDefault val type: String = "heartbeat",
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class ErrorMessage(
    @EncodeDefault val type: String = "error",
    val code: Int,
    val message: String,
)

@Serializable
data class DisconnectMessage(
    @EncodeDefault val type: String = "disconnect",
    val reason: String = "client_request",
)

object ProtocolConstants {
    const val CURRENT_VERSION = 1
    const val MIN_SUPPORTED_VERSION = 1

    const val TYPE_KEY_EVENT = "key"
    const val TYPE_ACK = "ack"
    const val TYPE_HEARTBEAT = "heartbeat"
    const val TYPE_ERROR = "error"
    const val TYPE_DISCONNECT = "disconnect"

    const val HEARTBEAT_INTERVAL_MS = 15_000L
    const val HEARTBEAT_TIMEOUT_MS = 45_000L
    const val DEFAULT_PORT = 8765

    const val ERROR_UNSUPPORTED_VERSION = 1001
    const val ERROR_INVALID_MESSAGE = 1002
    const val ERROR_SERVER_FULL = 1003
    const val ERROR_RATE_LIMITED = 1004

    const val MAX_CLIENTS = 8
}
