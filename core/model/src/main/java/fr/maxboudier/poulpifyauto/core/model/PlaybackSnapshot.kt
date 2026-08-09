package fr.maxboudier.poulpifyauto.core.model

enum class RepeatMode { OFF, TRACK, CONTEXT }

/** État de lecture fusionné, quelle que soit la source (App Remote ou serveur). */
data class PlaybackSnapshot(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val shuffling: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val deviceName: String? = null,
    val deviceActive: Boolean = false,
    val canSkipNext: Boolean = true,
    val canSkipPrevious: Boolean = true,
    val canSeek: Boolean = true,
)
