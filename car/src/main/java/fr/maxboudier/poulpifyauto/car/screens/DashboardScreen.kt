package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.maxboudier.poulpifyauto.core.model.ConnectionState
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.RemoteState

/**
 * Tableau de bord hôte : ce que le conducteur voit en arrivant sur l'écran
 * voiture. Ce qui joue, qui l'a mis, l'état des votes, et les commandes
 * réservées à l'hôte.
 */
class DashboardScreen(carContext: CarContext) : PoulpifyScreen(carContext) {

    override fun stateKey(state: PoulpifyUiState): Any = listOf(
        state.connection,
        state.remote,
        state.nowPlaying?.track?.uri,
        state.nowPlaying?.isPlaying,
        state.queue.size,
        state.passengers.size,
        state.votes,
        state.queueLocked,
        state.lastError?.at,
    )

    override fun onGetTemplate(): Template {
        val ui = state
        val playback = ui.nowPlaying
        val track = playback?.track

        val list = ItemList.Builder().apply {
            // Titre en cours
            addItem(
                Row.Builder()
                    .setTitle(track?.name ?: "Aucune lecture en cours")
                    .addText(
                        when {
                            track == null -> "Lance un son sur Spotify pour démarrer"
                            track.addedViaPoulpify && track.addedBy != null ->
                                "${track.artistLabel} • ajouté par ${track.addedByEmoji ?: ""}${track.addedBy}"
                            else -> track.artistLabel
                        }
                    )
                    .addText(statusLine(ui))
                    .setOnClickListener { runAction { coordinator.togglePlayPause() } }
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle("À suivre")
                    .addText(
                        if (ui.queue.isEmpty()) "File vide"
                        else "${ui.queue.size} titre(s)" + if (ui.queueLocked) " • verrouillée" else ""
                    )
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(QueueScreen(carContext)) }
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle("Ajouter un son")
                    .addText("Playlists, likés, top, récents")
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(LibraryScreen(carContext)) }
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle("Passagers")
                    .addText(
                        if (ui.passengers.isEmpty()) "Seul à bord 🐙"
                        else ui.passengers.joinToString(", ") { "${it.emoji} ${it.name}" }
                    )
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(PassengersScreen(carContext)) }
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle("Inviter un passager")
                    .addText("Afficher le QR code de la session")
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(InviteScreen(carContext)) }
                    .build()
            )
        }.build()

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Poulpify 🐙")
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .setSingleList(list)
            .setActionStrip(actionStrip(ui))
            .build()
    }

    private fun actionStrip(ui: PoulpifyUiState): ActionStrip = ActionStrip.Builder()
        .addAction(
            Action.Builder()
                .setTitle(if (ui.nowPlaying?.isPlaying == true) "Pause" else "Lecture")
                .setOnClickListener { runAction { coordinator.togglePlayPause() } }
                .build()
        )
        .addAction(
            Action.Builder()
                .setTitle("Suivant")
                .setOnClickListener { runAction("Titre passé") { coordinator.hostSkip() } }
                .build()
        )
        .addAction(
            // L'hote vote comme un passager plutot que d'imposer le saut : le
            // decompte s'affiche directement sur le bouton.
            Action.Builder()
                .setTitle("Voter ${ui.votes.current}/${ui.votes.required}")
                .setOnClickListener {
                    runAction("Vote enregistré") { coordinator.voteSkip() }
                }
                .build()
        )
        .addAction(
            Action.Builder()
                .setTitle(if (ui.queueLocked) "Déverrouiller" else "Verrouiller")
                .setOnClickListener {
                    runAction(
                        if (ui.queueLocked) "File ouverte aux passagers" else "File verrouillée"
                    ) { coordinator.toggleQueueLock() }
                }
                .build()
        )
        .build()

    /**
     * Une ligne d'état toujours visible. C'est le remède direct au « rien ne
     * marche sans qu'on sache pourquoi » : chaque cause a son message.
     */
    private fun statusLine(ui: PoulpifyUiState): String {
        ui.lastError?.let { return "⚠️ ${it.message}" }
        return when {
            ui.connection == ConnectionState.DISCONNECTED ->
                "⚠️ Session hôte fermée — vérifie la configuration sur le téléphone"
            ui.connection == ConnectionState.AUTHENTICATING -> "Connexion à la session hôte…"
            ui.connection == ConnectionState.RECONNECTING -> "Reconnexion en cours…"
            ui.connection == ConnectionState.DEGRADED ->
                "⚠️ Spotify non autorisé côté serveur — à faire une fois depuis le téléphone"
            ui.remote == RemoteState.NOT_INSTALLED -> "⚠️ Application Spotify absente du téléphone"
            ui.remote == RemoteState.UNAUTHORIZED -> "⚠️ Spotify n'a pas autorisé Poulpify"
            ui.remote != RemoteState.CONNECTED -> "Connexion à Spotify…"
            ui.nowPlaying?.deviceActive == false -> "Aucun appareil Spotify actif"
            else -> "Votes pour passer : ${ui.votes.current}/${ui.votes.required}"
        }
    }
}
