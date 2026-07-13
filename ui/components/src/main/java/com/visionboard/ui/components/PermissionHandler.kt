package com.visionboard.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class PermissionState(
    val permission: String,
    val isGranted: Boolean,
    val shouldShowRationale: Boolean
)

@Composable
fun PermissionHandler(
    permissions: List<String>,
    onAllPermissionsGranted: () -> Unit,
    onPermissionDenied: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Map<String, PermissionState>) -> Unit
) {
    val context = LocalContext.current
    val permissionStates = remember { mutableStateMapOf<String, PermissionState>() }
    var showRationaleDialog by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    fun updatePermissionStates() {
        permissions.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED

            val shouldShowRationale = shouldShowRationale(context, permission)

            permissionStates[permission] = PermissionState(
                permission = permission,
                isGranted = isGranted,
                shouldShowRationale = shouldShowRationale
            )
        }
    }

    LaunchedEffect(Unit) {
        updatePermissionStates()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (permission, granted) ->
            permissionStates[permission] = PermissionState(
                permission = permission,
                isGranted = granted,
                shouldShowRationale = shouldShowRationale(context, permission)
            )
        }

        val deniedPermissions = permissionStates.values
            .filter { !it.isGranted }
            .map { it.permission }

        if (deniedPermissions.isEmpty()) {
            onAllPermissionsGranted()
        } else {
            val shouldShowRationale = deniedPermissions.any { permission ->
                permissionStates[permission]?.shouldShowRationale == true
            }

            if (shouldShowRationale) {
                showRationaleDialog = deniedPermissions.first()
            } else {
                onPermissionDenied(deniedPermissions)
            }
        }
    }

    val pendingPermissions = permissions.filter { permission ->
        permissionStates[permission]?.isGranted != true
    }

    if (pendingPermissions.isNotEmpty()) {
        LaunchedEffect(pendingPermissions) {
            launcher.launch(pendingPermissions.toTypedArray())
        }
    }

    showRationaleDialog?.let { permission ->
        PermissionRationaleDialog(
            permission = permission,
            onConfirm = {
                showRationaleDialog = null
                launcher.launch(arrayOf(permission))
            },
            onDismiss = {
                showRationaleDialog = null
                onPermissionDenied(listOf(permission))
            }
        )
    }

    if (showSettingsDialog) {
        SettingsRedirectDialog(
            onConfirm = {
                showSettingsDialog = false
                openAppSettings(context)
            },
            onDismiss = {
                showSettingsDialog = false
            }
        )
    }

    content(permissionStates)
}

@Composable
fun PermissionStatusList(
    permissions: Map<String, PermissionState>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        permissions.values.forEach { state ->
            PermissionStatusItem(
                permission = state.permission,
                isGranted = state.isGranted
            )
        }
    }
}

@Composable
private fun PermissionStatusItem(
    permission: String,
    isGranted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getPermissionIcon(permission),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getPermissionName(permission),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = getPermissionDescription(permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = if (isGranted) "Granted" else "Denied",
            modifier = Modifier.size(20.dp),
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun PermissionRationaleDialog(
    permission: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = getPermissionIcon(permission),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "${getPermissionName(permission)} Required")
        },
        text = {
            Text(
                text = getRationaleText(permission),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Deny")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun SettingsRedirectDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "Permission Required")
        },
        text = {
            Text(
                text = "Some permissions were permanently denied. Please enable them in the app settings.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun shouldShowRationale(context: Context, permission: String): Boolean {
    return try {
        val activity = context as? android.app.Activity
        activity?.shouldShowRequestPermissionRationale(permission) ?: false
    } catch (e: Exception) {
        false
    }
}

private fun openAppSettings(context: Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun getPermissionIcon(permission: String): ImageVector {
    return when (permission) {
        Manifest.permission.CAMERA -> Icons.Filled.CameraAlt
        Manifest.permission.BLUETOOTH_CONNECT -> Icons.Filled.Settings
        Manifest.permission.BLUETOOTH_ADVERTISE -> Icons.Filled.Settings
        else -> Icons.Filled.Settings
    }
}

private fun getPermissionName(permission: String): String {
    return when (permission) {
        Manifest.permission.CAMERA -> "Camera"
        Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth Connect"
        Manifest.permission.BLUETOOTH_ADVERTISE -> "Bluetooth Advertise"
        else -> permission.substringAfterLast(".")
    }
}

private fun getPermissionDescription(permission: String): String {
    return when (permission) {
        Manifest.permission.CAMERA -> "Required for camera access"
        Manifest.permission.BLUETOOTH_CONNECT -> "Required for device connection"
        Manifest.permission.BLUETOOTH_ADVERTISE -> "Required for device discoverability"
        else -> "Required for app functionality"
    }
}

private fun getRationaleText(permission: String): String {
    return when (permission) {
        Manifest.permission.CAMERA -> {
            "VisionBoard needs camera access to detect your keyboard and finger positions. " +
                "Without camera access, the virtual keyboard cannot function."
        }
        Manifest.permission.BLUETOOTH_CONNECT -> {
            "VisionBoard needs Bluetooth access to connect with external devices " +
                "and provide a seamless keyboard experience."
        }
        Manifest.permission.BLUETOOTH_ADVERTISE -> {
            "VisionBoard needs Bluetooth advertise permission to be discoverable " +
                "by other devices for pairing and connectivity."
        }
        else -> {
            "This permission is required for VisionBoard to function properly."
        }
    }
}
