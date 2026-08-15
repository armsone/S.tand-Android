package com.armsone.stand.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun CatalogProfile(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f),
        content = content,
    )
}

internal fun captureCatalogScreenshot(
    activity: ComponentActivity,
    directory: File,
    id: String,
) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    SystemClock.sleep(500)
    instrumentation.waitForIdleSync()
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
        .copy(Bitmap.Config.ARGB_8888, true)
    val systemBars = ViewCompat.getRootWindowInsets(activity.window.decorView)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
    val masks = JSONArray()
    if (systemBars != null) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.BLACK }
        fun mask(x: Int, y: Int, width: Int, height: Int, edge: String) {
            if (width <= 0 || height <= 0) return
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), paint)
            masks.put(JSONObject().apply {
                put("owner", "os")
                put("type", "systemBars")
                put("edge", edge)
                put("boundsPixels", JSONArray(listOf(x, y, width, height)))
                put("treatment", "blackoutAtCapture")
            })
        }
        mask(0, 0, bitmap.width, systemBars.top, "top")
        mask(0, bitmap.height - systemBars.bottom, bitmap.width, systemBars.bottom, "bottom")
        mask(0, 0, systemBars.left, bitmap.height, "left")
        mask(bitmap.width - systemBars.right, 0, systemBars.right, bitmap.height, "right")
    }
    directory.mkdirs()
    File(directory, "$id.png").outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    val left = systemBars?.left ?: 0
    val top = systemBars?.top ?: 0
    val right = systemBars?.right ?: 0
    val bottom = systemBars?.bottom ?: 0
    File(directory, "$id.meta.json").writeText(
        JSONObject().apply {
            put("pixelWidth", bitmap.width)
            put("pixelHeight", bitmap.height)
            put(
                "appBoundsPixels",
                JSONArray(listOf(left, top, bitmap.width - left - right, bitmap.height - top - bottom)),
            )
            put("osMasks", masks)
        }.toString(),
    )
}
