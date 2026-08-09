package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState

class QueueScreen(carContext: CarContext) : PoulpifyScreen(carContext) {

    override fun stateKey(state: PoulpifyUiState): Any =
        state.queue.map { it.uri to it.addedBy } to state.queueLocked

    override fun onGetTemplate(): Template {
        val ui = state
        // La limite vient du host, pas d'un `take(5)` en dur comme avant :
        // elle varie selon la voiture et se resserre en roulant.
        val limit = listContentLimit()

        val list = ItemList.Builder().apply {
            if (ui.queue.isEmpty()) {
                setNoItemsMessage("La file est vide. Ajoute un son depuis tes playlists.")
            } else {
                ui.queue.take(limit).forEach { track ->
                    addItem(
                        Row.Builder()
                            // Un titre "surprise" reste masque jusqu'a ce qu'il passe,
                            // comme sur le site.
                            .setTitle(if (track.isInked) "🐙 Titre surprise" else track.name)
                            .addText(
                                if (track.isInked) {
                                    "Ajouté par ${track.addedBy ?: "quelqu'un"}"
                                } else {
                                    buildString {
                                        append(track.artistLabel)
                                        if (track.addedViaPoulpify && track.addedBy != null) {
                                            append(" • ${track.addedByEmoji ?: ""}${track.addedBy}")
                                        }
                                    }
                                }
                            )
                            .build()
                    )
                }
            }
        }.build()

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(
                        if (ui.queueLocked) "À suivre (verrouillée)" else "À suivre"
                    )
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(list)
            .build()
    }
}
