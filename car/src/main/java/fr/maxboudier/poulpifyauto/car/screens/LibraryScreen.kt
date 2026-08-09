package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState

/** Point d'entrée vers la bibliothèque de l'hôte. */
class LibraryScreen(carContext: CarContext) : PoulpifyScreen(carContext) {

    override fun stateKey(state: PoulpifyUiState): Any = listOf(
        state.playlists.size,
        state.likedTracks.size,
        state.topTracks.size,
        state.recentTracks.size,
    )

    override fun onGetTemplate(): Template {
        val ui = state

        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Mes playlists")
                    .addText("${ui.playlists.size} playlists")
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(PlaylistsScreen(carContext)) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Titres likés")
                    .addText("${ui.likedTracks.size} titres")
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(TrackListScreen(carContext, "Titres likés") { it.likedTracks })
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Top titres")
                    .addText("${ui.topTracks.size} titres")
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(TrackListScreen(carContext, "Top titres") { it.topTracks })
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Écoutés récemment")
                    .addText("${ui.recentTracks.size} titres")
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(TrackListScreen(carContext, "Écoutés récemment") { it.recentTracks })
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Ajouter un son")
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }
}
