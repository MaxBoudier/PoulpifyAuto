package fr.maxboudier.poulpifyauto.core.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {

    /**
     * Génère un QR à partir de l'URL de session. Renvoie null plutôt que de
     * lever : l'écran voiture doit retomber sur l'URL en texte, pas planter.
     */
    fun generate(content: String, sizePx: Int): Bitmap? = runCatching {
        val hints = mapOf(
            // Correction haute : le QR est lu de travers, à bout de bras,
            // depuis la banquette arrière.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }.getOrNull()

    /**
     * Même QR, encodé en PNG.
     *
     * La navigation média d'Android Auto charge les pochettes depuis un autre
     * processus : elle a besoin d'octets embarqués, une `Bitmap` en mémoire ne
     * lui servirait à rien.
     */
    fun generatePng(content: String, sizePx: Int): ByteArray? {
        val bitmap = generate(content, sizePx) ?: return null
        return java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
