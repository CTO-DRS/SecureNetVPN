package com.securenet.vpn.guide

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.QRCodeWriter

/**
 * v1.8.8 — QR codes for the detail page: every source's official URL is
 * rendered as a scannable code so the page can be handed to another device
 * (point the phone/tablet camera at it instead of retyping the link).
 *
 * The work is split so the interesting part stays unit-testable on the JVM:
 * [matrix] is pure ZXing (no Android classes) and is covered by
 * [GuideDetailQrTest] — including a breadth pass over the whole catalog, so
 * a malformed catalog URL breaks the build, not just the screen. [bitmap]
 * is the thin Android-side rasterizer and runs on-device only.
 */
object GuideDetailQr {

    /** White background / dark modules — guaranteed contrast on any theme. */
    private const val DARK = 0xFF000000.toInt()
    private const val LIGHT = 0xFFFFFFFF.toInt()

    /**
     * Encodes [content] into a square [size]×[size] module matrix, or null
     * when the content cannot be encoded (blank, oversized). Uses medium
     * error correction (~15% recoverable damage) and a hairline built-in
     * margin — any leftover scale remainder becomes extra quiet zone, and
     * the on-screen white padding adds more, so codes scan reliably.
     */
    fun matrix(content: String, size: Int = 512): BitMatrix? {
        if (content.isBlank() || size <= 0) return null
        return try {
            QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size,
                mapOf(
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
                )
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** Rasterizes [matrix] into an ARGB bitmap (white bg, dark modules). */
    fun bitmap(matrix: BitMatrix): Bitmap {
        val size = matrix.width
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) DARK else LIGHT
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Convenience: encode + rasterize in one call, null when unencodable. */
    fun bitmap(content: String, size: Int = 512): Bitmap? =
        matrix(content, size)?.let(::bitmap)
}
