package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.Playlist
import fr.maxboudier.poulpifyauto.core.model.Track
import kotlinx.coroutines.launch

class PlaylistsScreen(carContext: CarContext) : PoulpifyScreen(carContext) {

    override fun stateKey(state: PoulpifyUiState): Any = state.playlists.map { it.id }

    override fun onGetTemplate(): Template {
        val playlists = state.playlists
        val limit = listContentLimit()

        val list = ItemList.Builder().apply {
            if (playlists.isEmpty()) {
                setNoItemsMessage("Aucune playlist trouvée.")
            } else {
                playlists.take(limit).forEach { playlist ->
                    addItem(
                        Row.Builder()
                            .setTitle(playlist.name)
                            .setBrowsable(true)
                            .setOnClickListener {
                                screenManager.push(PlaylistTracksScreen(carContext, playlist))
                            }
                            .build()
                    )
                }
            }
        }.build()

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Mes playlists")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(list)
            .build()
    }
}

/**
 * Titres d'une playlist. La première ligne lance la playlist entière ; les
 * suivantes ajoutent un titre à la file collaborative.
 */
class PlaylistTracksScreen(
    carContext: CarContext,
    private val playlist: Playlist,
) : PoulpifyScreen(carContext) {

    private var tracks: List<Track> = emptyList()
    private var loading = true

    init {
        lifecycleScope.launch {
            tracks = coordinator.playlistTracks(playlist.id)
            loading = false
            invalidate()
        }
    }

    // Cet ecran a son propre chargement : l'etat global ne le concerne pas.
    override fun stateKey(state: PoulpifyUiState): Any = Unit

    override fun onGetTemplate(): Template {
        if (loading) {
            return ListTemplate.Builder()
                .setHeader(
                    Header.Builder()
                        .setTitle(playlist.name)
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .setLoading(true)
                .build()
        }

        val limit = listContentLimit()
        val list = ItemList.Builder().apply {
            if (tracks.isEmpty()) {
                setNoItemsMessage("Playlist vide.")
            } else {
                addItem(
                    Row.Builder()
                        .setTitle("▶ Jouer cette playlist maintenant")
                        .addText("Remplace la lecture en cours")
                        .setOnClickListener {
                            runAction("Lecture de ${playlist.name}") {
                                coordinator.playNow(playlist.uri)
                            }
                        }
                        .build()
                )
                tracks.take((limit - 1).coerceAtLeast(1)).forEach { track ->
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
                    .setTitle(playlist.name)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(list)
            .build()
    }
}
