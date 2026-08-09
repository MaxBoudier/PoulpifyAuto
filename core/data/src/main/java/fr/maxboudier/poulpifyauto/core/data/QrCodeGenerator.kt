package fr.maxboudier.poulpifyauto.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.io.ByteArrayOutputStream

/**
 * QR d'invitation aux couleurs de Poulpify, généré à la volée.
 *
 * On reproduit le style du QR de la webapp (modules arrondis roses, logo au
 * centre) plutôt que d'embarquer son PNG : ce fichier encode une URL figée et
 * deviendrait silencieusement faux dès que l'adresse du serveur change. Ici
 * l'apparence est la même, mais le contenu suit toujours la configuration.
 */
object QrCodeGenerator {

    private const val QUIET_ZONE_MODULES = 2
    private val PINK_LIGHT = Color.parseColor("#FF0084")
    private val PINK_DARK = Color.parseColor("#C9007A")

    /**
     * Renvoie null plutôt que de lever : l'écran voiture doit retomber sur
     * l'URL en texte, pas planter.
     */
    fun generate(content: String, sizePx: Int, logo: Bitmap? = null): Bitmap? = runCatching {
        // `Encoder` donne la grille brute des modules, contrairement à
        // `QRCodeWriter` qui rend déjà une image mise à l'échelle : impossible
        // d'y dessiner des modules arrondis.
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        // Correction haute : elle tolère le logo central, et le code reste
        // lisible de travers depuis la banquette arrière.
        val matrix = Encoder.encode(content, ErrorCorrectionLevel.H, hints).matrix
            ?: return@runCatching null

        val modules = matrix.width
        val totalModules = modules + QUIET_ZONE_MODULES * 2
        val moduleSize = sizePx.toFloat() / totalModules
        val origin = moduleSize * QUIET_ZONE_MODULES

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                PINK_LIGHT, PINK_DARK, Shader.TileMode.CLAMP,
            )
        }

        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (matrix.get(x, y).toInt() != 1) continue
                // Les trois motifs de reperage sont dessines a part, en formes
                // pleines : les rendre point par point les rend illisibles.
                if (isFinderPattern(x, y, modules)) continue
                val cx = origin + (x + 0.5f) * moduleSize
                val cy = origin + (y + 0.5f) * moduleSize
                canvas.drawCircle(cx, cy, moduleSize * 0.5f, paint)
            }
        }

        drawFinderPatterns(canvas, paint, origin, moduleSize, modules)
        logo?.let { drawCenterLogo(canvas, it, sizePx) }

        bitmap
    }.getOrNull()

    /** Zones 7×7 des trois coins, hors coin bas-droit qui n'en porte pas. */
    private fun isFinderPattern(x: Int, y: Int, modules: Int): Boolean {
        val inTopLeft = x < 7 && y < 7
        val inTopRight = x >= modules - 7 && y < 7
        val inBottomLeft = x < 7 && y >= modules - 7
        return inTopLeft || inTopRight || inBottomLeft
    }

    private fun drawFinderPatterns(
        canvas: Canvas,
        paint: Paint,
        origin: Float,
        moduleSize: Float,
        modules: Int,
    ) {
        val corners = listOf(0 to 0, modules - 7 to 0, 0 to modules - 7)
        corners.forEach { (mx, my) ->
            val left = origin + mx * moduleSize
            val top = origin + my * moduleSize
            val outer = RectF(left, top, left + 7 * moduleSize, top + 7 * moduleSize)

            // Anneau exterieur : trace epais d'un module, coins arrondis.
            val ringPaint = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = moduleSize
            }
            val inset = moduleSize / 2f
            canvas.drawRoundRect(
                RectF(outer.left + inset, outer.top + inset, outer.right - inset, outer.bottom - inset),
                moduleSize * 1.6f, moduleSize * 1.6f, ringPaint,
            )

            // Pastille centrale 3x3.
            val core = RectF(
                left + 2 * moduleSize, top + 2 * moduleSize,
                left + 5 * moduleSize, top + 5 * moduleSize,
            )
            canvas.drawRoundRect(core, moduleSize, moduleSize, paint)
        }
    }

    /**
     * Le logo masque quelques modules : la correction d'erreur de niveau H
     * (30 %) absorbe largement une pastille de cette taille.
     */
    private fun drawCenterLogo(canvas: Canvas, logo: Bitmap, sizePx: Int) {
        val diameter = sizePx * 0.22f
        val cx = sizePx / 2f
        val cy = sizePx / 2f

        // Disque blanc de garde, pour que le logo ne se confonde pas avec les
        // modules qui l'entourent.
        canvas.drawCircle(cx, cy, diameter / 2f + sizePx * 0.015f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        })

        val dest = RectF(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f)
        canvas.drawBitmap(
            logo,
            Rect(0, 0, logo.width, logo.height),
            dest,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }

    /** Charge le logo Poulpify, ou null s'il est indisponible. */
    fun loadLogo(context: Context): Bitmap? = runCatching {
        BitmapFactory.decodeResource(context.resources, R.drawable.poulpify_logo)
    }.getOrNull()

    /**
     * Même QR, encodé en PNG.
     *
     * La navigation média d'Android Auto charge les pochettes depuis un autre
     * processus : elle a besoin d'octets embarqués, une `Bitmap` en mémoire ne
     * lui servirait à rien.
     */
    fun generatePng(content: String, sizePx: Int, logo: Bitmap? = null): ByteArray? {
        val bitmap = generate(content, sizePx, logo) ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
