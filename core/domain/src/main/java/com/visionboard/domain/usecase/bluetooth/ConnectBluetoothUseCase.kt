package com.visionboard.domain.usecase.bluetooth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

enum class BluetoothConnectionState {
    DISCONNECTED,
    SCANNING,
    PAIRING,
    CONNECTED,
    ERROR,
}

class ConnectBluetoothUseCase @Inject constructor() {

    operator fun invoke(): Flow<BluetoothConnectionState> = flow {
        emit(BluetoothConnectionState.SCANNING)
        delay(1500)

        emit(BluetoothConnectionState.PAIRING)
        delay(2000)

        emit(BluetoothConnectionState.CONNECTED)
    }
}
