package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.maxboudier.poulpifyauto.core.data.QrCodeGenerator
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState

/**
 * QR code d'invitation, généré à la volée depuis l'URL réellement configurée.
 *
 * `GridTemplate` à un seul élément en `ITEM_SIZE_LARGE` plutôt qu'un
 * `PaneTemplate` : c'est le plus grand rendu qu'autorise la bibliothèque de
 * templates. La taille finale reste décidée par le système de la voiture et non
 * par la résolution du bitmap — l'écran du téléphone demeure la surface la plus
 * sûre pour faire scanner un passager.
 */
class InviteScreen(carContext: CarContext) : PoulpifyScreen(carContext) {

    override fun stateKey(state: PoulpifyUiState): Any = state.shareUrl ?: ""

    override fun onGetTemplate(): Template {
        val url = state.shareUrl ?: return message("Aucune session configurée sur le téléphone.")

        val bitmap = QrCodeGenerator.generate(url, QR_SIZE_PX, QrCodeGenerator.loadLogo(carContext))
            ?: return message(url)

        val item = GridItem.Builder()
            .setTitle("Scanne pour rejoindre 🐙")
            .setText(url)
            .setImage(
                CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build(),
                GridItem.IMAGE_TYPE_LARGE,
            )
            .build()

        return GridTemplate.Builder()
            .setTitle("Inviter un passager")
            .setHeaderAction(Action.BACK)
            // Pas de `setItemSize` : l'API est marquee experimentale et peut
            // etre refusee par un autoradio ancien. `IMAGE_TYPE_LARGE` sur
            // l'element suffit a obtenir un rendu genereux, en API stable.
            .setSingleList(ItemList.Builder().addItem(item).build())
            .build()
    }

    /** Repli lisible : au pire, le passager saisit l'adresse à la main. */
    private fun message(text: String): Template = MessageTemplate.Builder(text)
        .setTitle("Inviter un passager")
        .setHeaderAction(Action.BACK)
        .build()

    companion object {
        /**
         * Android Auto transporte les images par binder, plafonné à 1 Mo pour
         * l'ensemble de la transaction : au-delà, l'écran plante au lieu de
         * s'afficher. 480 px en ARGB_8888 pèse ~920 Ko, on reste en dessous.
         */
        private const val QR_SIZE_PX = 480
    }
}
