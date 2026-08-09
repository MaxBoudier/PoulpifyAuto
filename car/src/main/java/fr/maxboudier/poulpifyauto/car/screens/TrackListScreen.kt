package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.Track

/**
 * Liste de titres. Un appui ajoute à la file collaborative via le serveur,
 * ce qui préserve l'attribution « ajouté par » vue par les passagers — un
 * ajout direct par App Remote la perdrait.
 */
class TrackListScreen(
    carContext: CarContext,
    private val title: String,
    private val selector: (PoulpifyUiState) -> List<Track>,
) : PoulpifyScreen(carContext) {

    override fun stateKey(state: PoulpifyUiState): Any = selector(state).map { it.uri }

    override fun onGetTemplate(): Template {
        val tracks = selector(state)
        val limit = listContentLimit()

        val list = ItemList.Builder().apply {
            if (tracks.isEmpty()) {
                setNoItemsMessage("Rien à afficher pour le moment.")
            } else {
                tracks.take(limit).forEach { track ->
                    addItem(
                        Row.Builder()
                            .setTitle(track.name)
                            .addText(track.artistLabel)
                            .setOnClickListener {
                                runAction("Ajouté à la file : ${track.name}") {
                                    coordinator.addToQueue(track)
                                }
                            }
                            .build()
                    )
                }
            }
        }.build()

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(list)
            .build()
    }
}
