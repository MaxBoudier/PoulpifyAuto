package fr.maxboudier.poulpifyauto.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.RepeatMode
import fr.maxboudier.poulpifyauto.core.session.SessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * `Player` media3 qui ne joue aucun son : il reflète l'état de Spotify et
 * relaie les commandes de transport. C'est une télécommande.
 *
 * Conséquence assumée : ce lecteur **ne demande jamais le focus audio**.
 * C'est l'application Spotify qui produit le son et le détient ; Poulpify
 * n'apparaît que comme une seconde source média dans le sélecteur d'Android
 * Auto, où elle apporte le contexte social (qui a ajouté quoi, votes, verrou).
 *
 * [SimpleBasePlayer] est l'outil prévu exactement pour ce cas : on ne décrit
 * qu'un état et des gestionnaires de commandes, la classe de base se charge de
 * toute la mécanique d'écouteurs et de cohérence exigée par l'interface Player.
 */
@UnstableApi
class SpotifyProxyPlayer(
    looper: Looper,
    private val coordinator: SessionCoordinator,
) : SimpleBasePlayer(looper) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val availableCommands: Player.Commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_MEDIA_ITEM,
        )
        .build()

    /** À appeler à chaque changement d'état amont pour réévaluer [getState]. */
    fun onUpstreamStateChanged() {
        invalidateState()
    }

    override fun getState(): State {
        val ui: PoulpifyUiState = coordinator.state.value
        val playback = ui.nowPlaying
        val track = playback?.track

        val builder = State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaybackState(if (track != null) Player.STATE_READY else Player.STATE_IDLE)
            .setPlayWhenReady(
                playback?.isPlaying == true,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setShuffleModeEnabled(playback?.shuffling == true)
            .setRepeatMode(
                when (playback?.repeatMode) {
                    RepeatMode.TRACK -> Player.REPEAT_MODE_ONE
                    RepeatMode.CONTEXT -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
            )
            .setIsLoading(false)

        if (track != null) {
            // Le bouton « Queue » d'Android Auto affiche la timeline du lecteur.
            // Ne publier que le titre courant, comme avant, la laissait vide :
            // on expose donc le titre en cours suivi de toute la file Poulpify.
            val playlist = buildList {
                add(mediaItemData(track, seekable = playback.canSeek, index = 0))
                ui.queue.forEachIndexed { position, queued ->
                    add(mediaItemData(queued, seekable = false, index = position + 1))
                }
            }
            builder.setPlaylist(playlist)
            builder.setCurrentMediaItemIndex(0)
            // Fournisseur plutot que valeur figee : la position est interpolee
            // a la demande, sans forcer une invalidation d'etat deux fois par
            // seconde (que le host Android Auto limiterait de toute facon).
            builder.setContentPositionMs(PositionSupplier { coordinator.currentPositionMs() })
        }

        return builder.build()
    }

    /**
     * L'identifiant inclut la position : un même titre peut figurer deux fois
     * dans la file, et media3 exige des `uid` distincts.
     */
    private fun mediaItemData(
        track: fr.maxboudier.poulpifyauto.core.model.Track,
        seekable: Boolean,
        index: Int,
    ): MediaItemData {
        val metadata = track.toMediaMetadata()
        return MediaItemData.Builder("$index:${track.uri}")
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaId(track.uri)
                    .setUri(track.uri)
                    .setMediaMetadata(metadata)
                    .build()
            )
            .setMediaMetadata(metadata)
            .setDurationUs(if (track.durationMs > 0) track.durationMs * 1000 else C.TIME_UNSET)
            .setIsSeekable(seekable)
            .setIsDynamic(false)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        dispatch { if (playWhenReady) coordinator.play() else coordinator.pause() }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> = dispatch { coordinator.pause() }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> =
        dispatch { coordinator.setShuffle(shuffleModeEnabled) }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> = dispatch {
        coordinator.setRepeat(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.TRACK
                Player.REPEAT_MODE_ALL -> RepeatMode.CONTEXT
                else -> RepeatMode.OFF
            }
        )
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> = when (seekCommand) {
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        -> dispatch { coordinator.hostSkip() }

        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        -> dispatch { coordinator.skipPrevious() }

        else -> dispatch {
            if (positionMs == C.TIME_UNSET) Result.success(Unit)
            else coordinator.seekTo(positionMs)
        }
    }

    /**
     * media3 attend un futur résolu immédiatement pour que l'UI réagisse sans
     * attendre l'aller-retour ; l'action réelle part en parallèle et le
     * prochain état poussé par App Remote corrigera l'affichage si elle échoue.
     */
    private fun dispatch(action: suspend () -> Result<Unit>): ListenableFuture<*> {
        scope.launch {
            action()
            invalidateState()
        }
        return Futures.immediateVoidFuture()
    }
}

/**
 * Réutilise la fabrique commune : sans cela, un titre encré présent dans la
 * file laisserait fuiter sa pochette par le bouton « Queue », alors même
 * qu'elle est masquée dans la navigation.
 */
private fun fr.maxboudier.poulpifyauto.core.model.Track.toMediaMetadata(): MediaMetadata =
    trackMetadata(listOfNotNull(artistLabel, credit()).joinToString(" • "))
