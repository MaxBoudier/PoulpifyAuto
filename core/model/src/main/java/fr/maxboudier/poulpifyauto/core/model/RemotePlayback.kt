package fr.maxboudier.poulpifyauto.core.model

/**
 * État de lecture poussé par Spotify App Remote, traduit en type de domaine.
 *
 * Aucune classe du SDK Spotify ne franchit la frontière de `:core:spotify` :
 * le reste de l'app ne dépend donc pas de l'AAR, et le coordinateur reste
 * testable en JVM simple sans le SDK.
 */
data class RemotePlayback(
    val trackUri: String,
    val trackName: String,
    val artists: List<String>,
    val albumName: String?,
    val durationMs: Long,
    val positionMs: Long,
    val isPaused: Boolean,
    val shuffling: Boolean,
    val repeatMode: RepeatMode,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canSeek: Boolean,
    /**
     * Référence d'image opaque de Spotify. Inutilisable en HTTP : elle ne sert
     * qu'à redemander la pochette à `SpotifyRemoteController.albumArt`, qui la
     * retraduit en `ImageUri`.
     */
    val imageUriRaw: String?,
)
