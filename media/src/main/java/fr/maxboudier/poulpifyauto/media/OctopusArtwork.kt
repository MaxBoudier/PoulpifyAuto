package fr.maxboudier.poulpifyauto.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Pochette de substitution pour les titres « encrés ».
 *
 * Un titre surprise doit le rester : afficher sa vraie pochette dans la file
 * permettait de deviner le morceau avant qu'il ne passe, ce qui vide la
 * surprise de son intérêt.
 *
 * On passe par `artworkData` (des octets embarqués) plutôt que par une URI de
 * ressource : Android Auto charge les pochettes depuis un autre processus, où
 * une `android.resource://` de notre paquet ne se résout pas.
 */
internal object OctopusArtwork {

    private const val SIZE_PX = 320

    /** Encodée une seule fois : la file peut contenir plusieurs titres encrés. */
    val pngBytes: ByteArray by lazy { render() }

    private fun render(): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#1E1E22"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SIZE_PX * 0.55f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        // Centrage vertical sur la hauteur reelle du glyphe, pas sur la ligne
        // de base : l'emoji serait sinon visiblement trop bas.
        val metrics = paint.fontMetrics
        val baseline = SIZE_PX / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText("🐙", SIZE_PX / 2f, baseline, paint)

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
