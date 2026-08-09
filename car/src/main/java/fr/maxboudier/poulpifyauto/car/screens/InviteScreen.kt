package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.maxboudier.poulpifyauto.car.QrCodeGenerator
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState

/**
 * QR code d'invitation, généré à la volée depuis l'URL réellement configurée.
 * L'ancienne version affichait un PNG figé dans les ressources, qui devenait
 * faux dès que l'URL du serveur changeait.
 */
class InviteScreen(carContext: CarContext) : PoulpifyScreen(carContext) {

    override fun stateKey(state: PoulpifyUiState): Any = state.shareUrl ?: ""

    override fun onGetTemplate(): Template {
        val url = state.shareUrl
            ?: return MessageTemplate.Builder("Aucune session configurée sur le téléphone.")
                .setHeader(
                    Header.Builder()
                        .setTitle("Inviter un passager")
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .build()

        val bitmap = QrCodeGenerator.generate(url, QR_SIZE_PX)
            ?: return MessageTemplate.Builder(url)
                .setHeader(
                    Header.Builder()
                        .setTitle("Inviter un passager")
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Scanne pour rejoindre 🐙")
                    .addText(url)
                    .build()
            )
            .setImage(CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build())
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(
                Header.Builder()
                    .setTitle("Inviter un passager")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }

    companion object {
        /**
         * Android Auto transporte les images par binder, plafonné à 1 Mo pour
         * l'ensemble de la transaction : au-delà, l'écran plante au lieu de
         * s'afficher. 480 px laisse une marge confortable.
         */
        private const val QR_SIZE_PX = 480
    }
}
