package fr.maxboudier.poulpifyauto.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ArtistDto(
    val name: String,
)

@Serializable
data class AlbumDto(
    val name: String? = null,
    val images: List<ImageDto>? = null,
)

@Serializable
data class TrackDto(
    val id: String? = null,
    val uri: String,
    val name: String,
    val artists: List<ArtistDto>? = null,
    val album: AlbumDto? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
    val addedViaPoulpify: Boolean = false,
    val addedBy: String? = null,
    val addedByEmoji: String? = null,
    val addedByHost: Boolean = false,
    val isInked: Boolean = false,
)

@Serializable
data class DeviceDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("volume_percent") val volumePercent: Int? = null,
)

@Serializable
data class UserDto(
    val name: String,
    val emoji: String,
)
