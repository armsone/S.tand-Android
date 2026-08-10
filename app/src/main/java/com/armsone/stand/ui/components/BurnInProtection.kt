package com.armsone.stand.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

data class BurnInOffset(val xDp: Int, val yDp: Int)

object BurnInProtection {
    private const val IOS_REFERENCE_DATE_EPOCH_MILLIS = 978_307_200_000L
    private const val MINUTE_MILLIS = 60_000L
    private val path = listOf(
        BurnInOffset(0, 0),
        BurnInOffset(3, -2),
        BurnInOffset(5, 1),
        BurnInOffset(2, 3),
        BurnInOffset(-2, 3),
        BurnInOffset(-5, 1),
        BurnInOffset(-3, -2),
        BurnInOffset(0, -3),
    )

    fun offsetAt(epochMillis: Long): BurnInOffset {
        val minute = Math.floorDiv(epochMillis - IOS_REFERENCE_DATE_EPOCH_MILLIS, MINUTE_MILLIS)
        return path[Math.floorMod(minute, path.size.toLong()).toInt()]
    }

    fun millisUntilNextMinute(epochMillis: Long): Long {
        val elapsedInMinute = Math.floorMod(epochMillis, MINUTE_MILLIS)
        return MINUTE_MILLIS - elapsedInMinute
    }
}

@Composable
fun rememberBurnInOffset(): BurnInOffset {
    val offset by produceState(BurnInProtection.offsetAt(System.currentTimeMillis())) {
        while (true) {
            val now = System.currentTimeMillis()
            value = BurnInProtection.offsetAt(now)
            delay(BurnInProtection.millisUntilNextMinute(now))
        }
    }
    return offset
}
