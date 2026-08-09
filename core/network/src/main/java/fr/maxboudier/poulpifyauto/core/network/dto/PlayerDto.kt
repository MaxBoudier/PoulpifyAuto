package fr.maxboudier.poulpifyauto.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerStateDto(
    val device: DeviceDto? = null,
    @SerialName("shuffle_state") val shuffleState: Boolean = false,
    @SerialName("repeat_state") val repeatState: String = "off",
    @SerialName("progress_ms") val progressMs: Long = 0,
    val item: TrackDto? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
)

@Serializable
data class QueueResponseDto(
    val queue: List<TrackDto> = emptyList(),
    @SerialName("currently_playing") val currentlyPlaying: TrackDto? = null,
)

@Serializable
data class DevicesResponseDto(
    val devices: List<DeviceDto> = emptyList(),
)

@Serializable
data class QueueAddRequestDto(
    val uri: String,
    val username: String? = null,
    val emoji: String? = null,
    val isInked: Boolean = false,
)

@Serializable
data class TransferPlaybackRequestDto(
    @SerialName("device_id") val deviceId: String,
    val play: Boolean = true,
)

@Serializable
data class SearchResponseDto(
    val tracks: SearchTracksDto? = null,
)

@Serializable
data class SearchTracksDto(
    val items: List<TrackDto> = emptyList(),
)

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String,
    val uri: String,
    val images: List<ImageDto>? = null,
)

@Serializable
data class PlaylistsResponseDto(
    val items: List<PlaylistDto> = emptyList(),
)

@Serializable
data class SavedTrackDto(
    val track: TrackDto? = null,
)

@Serializable
data class SavedTracksResponseDto(
    val items: List<SavedTrackDto>? = emptyList(),
)

@Serializable
data class TopTracksResponseDto(
    val items: List<TrackDto> = emptyList(),
)

@Serializable
data class PlayHistoryItemDto(
    val track: TrackDto,
)

@Serializable
data class RecentlyPlayedResponseDto(
    val items: List<PlayHistoryItemDto> = emptyList(),
)

@Serializable
data class PlaylistTrackItemDto(
    val track: TrackDto? = null,
)

@Serializable
data class PlaylistTracksResponseDto(
    val items: List<PlaylistTrackItemDto> = emptyList(),
)
