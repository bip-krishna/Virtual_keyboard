package com.visionboard.di

import com.visionboard.network.protocol.ConnectionManager
import com.visionboard.network.server.KeyboardServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideKeyboardServer(): KeyboardServer {
        return KeyboardServer()
    }

    @Provides
    @Singleton
    fun provideConnectionManager(
        keyboardServer: KeyboardServer,
    ): ConnectionManager {
        return ConnectionManager(keyboardServer)
    }
}
