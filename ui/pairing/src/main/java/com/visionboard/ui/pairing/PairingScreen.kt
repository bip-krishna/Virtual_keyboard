package com.visionboard.ui.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.visionboard.ui.theme.DeepBlueLight
import com.visionboard.ui.theme.ErrorRed
import com.visionboard.ui.theme.KeyPress
import com.visionboard.ui.theme.OnSurfaceDark
import com.visionboard.ui.theme.SurfaceDark
import com.visionboard.ui.theme.SurfaceDarkContainer
import com.visionboard.ui.theme.TealLight

@Composable
fun PairingScreen(
    onPairingComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionQuality by viewModel.connectionQuality.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(horizontal = 24.dp),
    ) {
        when (val state = uiState) {
            is PairingUiState.Idle -> IdleContent(
                onStartScanning = viewModel::startScanning,
                onSkip = {
                    viewModel.skipPairing()
                    onSkip()
                },
            )
            is PairingUiState.Scanning -> ScanningContent(
                devices = state.devices,
                onStopScanning = viewModel::stopScanning,
                onConnect = viewModel::connectToDevice,
                onSkip = {
                    viewModel.skipPairing()
                    onSkip()
                },
            )
            is PairingUiState.Pairing -> PairingContent(
                device = state.device,
            )
            is PairingUiState.Connected -> ConnectedContent(
                device = state.device,
                quality = connectionQuality,
                onDisconnect = viewModel::disconnect,
                onContinue = onPairingComplete,
            )
            is PairingUiState.Error -> ErrorContent(
                message = state.message,
                onRetry = {
                    viewModel.disconnect()
                    viewModel.startScanning()
                },
                onSkip = {
                    viewModel.skipPairing()
                    onSkip()
                },
            )
        }
    }
}

@Composable
private fun IdleContent(
    onStartScanning: () -> Unit,
    onSkip: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bluetoothPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DeepBlueLight.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                    )
                )
                .scale(pulseScale),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = "Bluetooth",
                modifier = Modifier
                    .size(72.dp)
                    .alpha(pulseAlpha),
                tint = DeepBlueLight,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Connect Your Keyboard",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Pair your keyboard via Bluetooth to start typing. Make sure your keyboard is discoverable.",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = OnSurfaceDark.copy(alpha = 0.7f),
                lineHeight = 24.sp,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStartScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepBlueLight,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Start Scanning",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Skip for now",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScanningContent(
    devices: List<DiscoveredDevice>,
    onStopScanning: () -> Unit,
    onConnect: (DiscoveredDevice) -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Discovering Devices",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark,
                ),
            )

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(KeyPress),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Found ${devices.size} device${if (devices.size != 1) "s" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceDark.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = DeepBlueLight,
            trackColor = SurfaceDarkContainer,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = OnSurfaceDark.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching for nearby devices...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDark.copy(alpha = 0.4f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices, key = { it.address }) { device ->
                    DeviceCard(
                        device = device,
                        onConnect = { onConnect(device) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(
            onClick = onStopScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "Stop Scanning",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "Skip for now",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
    onConnect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDarkContainer,
        ),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DeepBlueLight.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Computer,
                    contentDescription = null,
                    tint = DeepBlueLight,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceDark,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDark.copy(alpha = 0.5f),
                )
            }

            if (device.isBonded) {
                Icon(
                    imageVector = Icons.Filled.BluetoothConnected,
                    contentDescription = "Paired",
                    tint = KeyPress,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = "Connect",
                    tint = OnSurfaceDark.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun PairingContent(
    device: DiscoveredDevice,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = DeepBlueLight,
            strokeWidth = 4.dp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Pairing",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark,
            ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Connecting to ${device.name}...",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceDark.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDarkContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Pairing PIN",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnSurfaceDark.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Check your keyboard for the pairing code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDark.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    device: DiscoveredDevice,
    quality: ConnectionQuality,
    onDisconnect: () -> Unit,
    onContinue: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "successPulse")
    val checkScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "checkScale",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            KeyPress.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                    )
                )
                .scale(checkScale),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Connected",
                modifier = Modifier.size(72.dp),
                tint = KeyPress,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Connected",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = device.name,
            style = MaterialTheme.typography.titleLarge.copy(
                color = DeepBlueLight,
                fontWeight = FontWeight.SemiBold,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        ConnectionQualityIndicator(quality = quality)

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KeyPress,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothConnected,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothDisabled,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Disconnect",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ConnectionQualityIndicator(
    quality: ConnectionQuality,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDarkContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothConnected,
                contentDescription = "Signal",
                tint = when {
                    quality.signalBars >= 3 -> KeyPress
                    quality.signalBars >= 1 -> AmberLight
                    else -> ErrorRed
                },
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Connection Quality",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDark,
                )
                Text(
                    text = when {
                        quality.signalBars >= 3 -> "Excellent"
                        quality.signalBars >= 1 -> "Fair"
                        else -> "Poor"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDark.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { bar ->
                    val isActive = bar < quality.signalBars
                    val barColor by animateColorAsState(
                        targetValue = when {
                            isActive && quality.signalBars >= 3 -> KeyPress
                            isActive && quality.signalBars >= 1 -> TealLight
                            isActive -> ErrorRed
                            else -> OnSurfaceDark.copy(alpha = 0.15f)
                        },
                        label = "barColor",
                    )
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(8.dp + bar * 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = "Error",
            modifier = Modifier.size(80.dp),
            tint = ErrorRed,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connection Error",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceDark.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepBlueLight,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Skip for now",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private val AmberLight = Color(0xFFFFA000)
