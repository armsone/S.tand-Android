package com.armsone.stand.boyiso

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object BoyisoQrCode {
    fun create(value: String, sizePixels: Int = 768): Bitmap {
        require(value.isNotBlank()) { "QR value must not be blank" }
        val matrix = QRCodeWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            sizePixels,
            sizePixels,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
        val dark = Color(0xFF17211F).toArgb()
        val light = Color(0xFFF7F3EA).toArgb()
        val pixels = IntArray(sizePixels * sizePixels)
        for (y in 0 until sizePixels) {
            for (x in 0 until sizePixels) {
                pixels[y * sizePixels + x] = if (matrix[x, y]) dark else light
            }
        }
        return Bitmap.createBitmap(pixels, sizePixels, sizePixels, Bitmap.Config.ARGB_8888)
    }
}
