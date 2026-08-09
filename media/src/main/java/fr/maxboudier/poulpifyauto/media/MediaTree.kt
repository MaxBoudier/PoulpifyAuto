package fr.maxboudier.poulpifyauto.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import fr.maxboudier.poulpifyauto.core.model.Playlist
import fr.maxboudier.poulpifyauto.core.model.Track

/**
 * Identifiants de l'arborescence parcourue sur l'écran de la voiture.
 *
 * Le préfixe encode l'action : un appui sur `queue:<uri>` ajoute à la file
 * Poulpify (le titre garde l'attribution vue par les passagers), un appui sur
 * `play:<uri>` lance la lecture tout de suite.
 */
object MediaId {
    const val ROOT = "poulpify_root"

    const val NODE_QUEUE = "node_queue"
    const val NODE_PLAYLISTS = "node_playlists"
    const val NODE_LIKED = "node_liked"
    const val NODE_TOP = "node_top"
    const val NODE_RECENT = "node_recent"
    const val NODE_PASSENGERS = "node_passengers"
    const val NODE_INVITE = "node_invite"

    const val PREFIX_PLAYLIST = "playlist:"
    const val PREFIX_QUEUE_ADD = "queue:"
    const val PREFIX_PLAY_NOW = "play:"
    const val PREFIX_INFO = "info:"

    fun playlist(id: String) = "$PREFIX_PLAYLIST$id"
    fun queueAdd(uri: String) = "$PREFIX_QUEUE_ADD$uri"
    fun playNow(uri: String) = "$PREFIX_PLAY_NOW$uri"

    fun isPlaylist(id: String) = id.startsWith(PREFIX_PLAYLIST)
    fun isQueueAdd(id: String) = id.startsWith(PREFIX_QUEUE_ADD)
    fun isPlayNow(id: String) = id.startsWith(PREFIX_PLAY_NOW)

    fun payload(id: String) = id.substringAfter(':')
}

internal fun browsableNode(
    id: String,
    title: String,
    subtitle: String? = null,
): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
    )
    .build()

/** Ligne non actionnable (liste des passagers, message d'état). */
internal fun infoItem(id: String, title: String, subtitle: String? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId("${MediaId.PREFIX_INFO}$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(false)
                .setIsPlayable(false)
                .build()
        )
        .build()

internal fun Track.toMediaItem(mediaId: String, subtitleOverride: String? = null): MediaItem {
    val subtitle = subtitleOverride ?: buildString {
        append(artistLabel)
        if (addedViaPoulpify && addedBy != null) {
            append(" • ")
            addedByEmoji?.let { append("$it ") }
            append(addedBy)
        }
    }
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(trackMetadata(subtitle))
        .build()
}

/**
 * Un titre « encré » ne doit rien laisser filtrer : ni son nom, ni son artiste,
 * ni sa pochette. Afficher la vraie image permettait de reconnaître le morceau
 * avant qu'il ne passe.
 */
internal fun Track.trackMetadata(subtitle: String): MediaMetadata =
    MediaMetadata.Builder()
        .setTitle(if (isInked) "🐙 Titre surprise" else name)
        .setSubtitle(if (isInked) "Ajouté par ${addedBy ?: "quelqu'un"}" else subtitle)
        .setArtist(if (isInked) "Surprise" else artistLabel)
        .setAlbumTitle(if (isInked) null else albumName)
        .apply {
            if (isInked) {
                setArtworkData(OctopusArtwork.pngBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } else {
                setArtworkUri(imageUrl?.let { Uri.parse(it) })
            }
        }
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .build()

internal fun Playlist.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(MediaId.playlist(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(imageUrl?.let { Uri.parse(it) })
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .build()
    )
    .build()
