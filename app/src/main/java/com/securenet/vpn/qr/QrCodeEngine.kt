package com.securenet.vpn.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.QRCodeWriter

/**
 * v1.5 — QR payload encoder for WireGuard profile exchange.
 *
 * The heavy lifting is ZXing core (pure Java, JVM-testable): we produce the
 * raw [com.google.zxing.common.BitMatrix] here and the Android layer
 * (`QrShareDialogFragment`) paints it into a Bitmap. Kept split so the
 * encoding/validation logic is testable without an emulator.
 *
 * Error correction level M survives a ~15% scuff while keeping the modules
 * small enough for a wireguard profile (~300-700 chars) to scan off a phone.
 */
object QrCodeEngine {

    /** QR max byte capacity at ECC-M / version 40 is 2 331 bytes; keep margin. */
    const val MAX_PAYLOAD_CHARS = 2_200

    sealed class EncodeResult {
        data class Success(val matrix: com.google.zxing.common.BitMatrix) : EncodeResult()
        data class TooLarge(val chars: Int) : EncodeResult()
    }

    fun encode(payload: String): EncodeResult {
        val text = payload.trim()
        if (text.length > MAX_PAYLOAD_CHARS) {
            return EncodeResult.TooLarge(text.length)
        }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0, hints
        )
        return EncodeResult.Success(matrix)
    }
}
