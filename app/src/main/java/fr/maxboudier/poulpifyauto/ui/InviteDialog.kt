package fr.maxboudier.poulpifyauto.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.maxboudier.poulpifyauto.core.data.QrCodeGenerator

/**
 * QR d'invitation en grand, sur le téléphone.
 *
 * L'écran de la voiture ne peut pas afficher un QR scannable : c'est le
 * template Android Auto qui impose la taille de rendu, pas la résolution de
 * l'image. Le téléphone est la seule surface où un passager peut réellement
 * scanner — et c'est de toute façon le geste naturel.
 */
@Composable
fun InviteDialog(
    shareUrl: String?,
    onDismiss: () -> Unit,
) {
    val url = shareUrl ?: return
    val context = LocalContext.current
    // Genere une fois par URL : l'encodage est purement CPU.
    val qr = remember(url) {
        QrCodeGenerator.generate(url, QR_SIZE_PX, QrCodeGenerator.loadLogo(context))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
        title = {
            Text("Inviter un passager 🐙", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "QR code de la session",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            // Fond blanc explicite : en theme sombre, un QR
                            // rendu sur fond noir n'est pas lisible.
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                    )
                }
                Text(
                    url,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Fais scanner ce code à tes passagers pour qu'ils ajoutent des sons.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
}

private const val QR_SIZE_PX = 720
