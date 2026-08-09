package fr.maxboudier.poulpifyauto.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ce que le coordinateur attend de la configuration persistée, sans rien
 * savoir de DataStore ni du Keystore.
 */
interface ConfigSource {
    val config: Flow<AppConfig>
    suspend fun current(): AppConfig
}

/**
 * Configuration nécessaire à l'ouverture d'une session hôte.
 * Vit dans `:core:model` pour que le coordinateur reste testable en JVM pure.
 */
data class AppConfig(
    val serverUrl: String,
    val hostPassword: String?,
    val driverName: String,
    val driverEmoji: String,
    val autoStartOnCarConnect: Boolean,
    val disableServerAutoDisconnect: Boolean,
    val tapAddsToQueue: Boolean,
) {
    val isComplete: Boolean get() = serverUrl.isNotBlank() && !hostPassword.isNullOrBlank()
}

/**
 * Ce que le coordinateur attend du contrôle Spotify local.
 *
 * L'interface existe pour deux raisons : garder les classes du SDK Spotify
 * confinées à `:core:spotify`, et pouvoir tester la logique de session sans
 * l'AAR ni un téléphone.
 */
interface SpotifyRemote {
    val state: StateFlow<RemoteState>
    val playback: StateFlow<RemotePlayback?>
    val isConnected: Boolean

    fun connect()
    fun disconnect()

    suspend fun resume(): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun skipNext(): Result<Unit>
    suspend fun skipPrevious(): Result<Unit>
    suspend fun seekTo(positionMs: Long): Result<Unit>
    suspend fun setShuffle(enabled: Boolean): Result<Unit>
    suspend fun setRepeat(mode: RepeatMode): Result<Unit>
    suspend fun play(uri: String): Result<Unit>
    suspend fun queue(uri: String): Result<Unit>
}
