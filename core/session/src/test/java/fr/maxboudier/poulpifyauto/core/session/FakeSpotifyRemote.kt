package fr.maxboudier.poulpifyauto.core.session

import fr.maxboudier.poulpifyauto.core.model.AppConfig
import fr.maxboudier.poulpifyauto.core.model.ConfigSource
import fr.maxboudier.poulpifyauto.core.model.RemotePlayback
import fr.maxboudier.poulpifyauto.core.model.RemoteState
import fr.maxboudier.poulpifyauto.core.model.RepeatMode
import fr.maxboudier.poulpifyauto.core.model.SpotifyRemote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Spotify absent ou présent, sans SDK ni téléphone. */
class FakeSpotifyRemote(
    initialState: RemoteState = RemoteState.DISCONNECTED,
) : SpotifyRemote {

    override val state = MutableStateFlow(initialState)
    override val playback = MutableStateFlow<RemotePlayback?>(null)
    override val isConnected: Boolean get() = state.value == RemoteState.CONNECTED

    var connectCalls = 0
        private set
    val commands = mutableListOf<String>()
    var failCommands = false

    override fun connect() {
        connectCalls++
    }

    override fun disconnect() {
        state.value = RemoteState.DISCONNECTED
    }

    private fun record(name: String): Result<Unit> {
        commands += name
        return if (failCommands) Result.failure(IllegalStateException("remote KO"))
        else Result.success(Unit)
    }

    override suspend fun resume() = record("resume")
    override suspend fun pause() = record("pause")
    override suspend fun skipNext() = record("skipNext")
    override suspend fun skipPrevious() = record("skipPrevious")
    override suspend fun seekTo(positionMs: Long) = record("seekTo:$positionMs")
    override suspend fun setShuffle(enabled: Boolean) = record("shuffle:$enabled")
    override suspend fun setRepeat(mode: RepeatMode) = record("repeat:$mode")
    override suspend fun play(uri: String) = record("play:$uri")
    override suspend fun queue(uri: String) = record("queue:$uri")
}

class FakeConfigSource(private var value: AppConfig) : ConfigSource {
    override val config: Flow<AppConfig> = MutableStateFlow(value)
    override suspend fun current(): AppConfig = value

    companion object {
        fun of(serverUrl: String, password: String? = "secret") = FakeConfigSource(
            AppConfig(
                serverUrl = serverUrl,
                hostPassword = password,
                driverName = "Poulpi",
                driverEmoji = "🐙",
                autoStartOnCarConnect = true,
                disableServerAutoDisconnect = true,
                tapAddsToQueue = true,
            )
        )
    }
}
