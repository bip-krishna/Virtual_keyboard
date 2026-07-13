package com.visionboard.di

import android.content.Context
import com.visionboard.bluetooth.handler.BluetoothHidHandler
import com.visionboard.bluetooth.hid.HidKeyTranslator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideHidKeyTranslator(): HidKeyTranslator {
        return HidKeyTranslator()
    }

    @Provides
    @Singleton
    fun provideBluetoothHidHandler(
        @ApplicationContext context: Context,
        keyTranslator: HidKeyTranslator,
    ): BluetoothHidHandler {
        return BluetoothHidHandler(context, keyTranslator)
    }
}
