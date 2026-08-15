package com.armsone.stand.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.ui.graphics.vector.ImageVector

internal fun batteryIcon(level: Float?, isCharging: Boolean): ImageVector {
    if (isCharging) return Icons.Default.BatteryChargingFull
    return when {
        level == null -> Icons.Default.Battery0Bar
        level <= 0.08f -> Icons.Default.Battery0Bar
        level <= 0.20f -> Icons.Default.Battery1Bar
        level <= 0.34f -> Icons.Default.Battery2Bar
        level <= 0.48f -> Icons.Default.Battery3Bar
        level <= 0.62f -> Icons.Default.Battery4Bar
        level <= 0.76f -> Icons.Default.Battery5Bar
        level <= 0.90f -> Icons.Default.Battery6Bar
        else -> Icons.Default.BatteryFull
    }
}
