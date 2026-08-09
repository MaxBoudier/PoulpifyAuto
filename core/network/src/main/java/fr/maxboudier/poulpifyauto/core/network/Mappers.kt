package fr.maxboudier.poulpifyauto.core.network

import fr.maxboudier.poulpifyauto.core.model.Passenger
import fr.maxboudier.poulpifyauto.core.model.PlaybackSnapshot
import fr.maxboudier.poulpifyauto.core.model.Playlist
import fr.maxboudier.poulpifyauto.core.model.RepeatMode
import fr.maxboudier.poulpifyauto.core.model.Track
import fr.maxboudier.poulpifyauto.core.model.Votes
import fr.maxboudier.poulpifyauto.core.network.dto.PlayerStateDto
import fr.maxboudier.poulpifyauto.core.network.dto.PlaylistDto
import fr.maxboudier.poulpifyauto.core.network.dto.SseSnapshotDto
import fr.maxboudier.poulpifyauto.core.network.dto.TrackDto
import fr.maxboudier.poulpifyauto.core.network.dto.UserDto

fun TrackDto.toDomain(): Track = Track(
    id = id ?: uri.substringAfterLast(':'),
    uri = uri,
    name = name,
    artists = artists?.map { it.name } ?: emptyList(),
    albumName = album?.name,
    imageUrl = album?.images?.firstOrNull()?.url,
    durationMs = durationMs,
    addedViaPoulpify = addedViaPoulpify,
    addedBy = addedBy,
    addedByEmoji = addedByEmoji,
    addedByHost = addedByHost,
    isInked = isInked,
)

fun UserDto.toDomain(): Passenger = Passenger(name, emoji)

fun PlaylistDto.toDomain(): Playlist = Playlist(
    id = id,
    uri = uri,
    name = name,
    imageUrl = images?.firstOrNull()?.url,
)

fun PlayerStateDto.toDomain(): PlaybackSnapshot = PlaybackSnapshot(
    track = item?.toDomain(),
    isPlaying = isPlaying,
    progressMs = progressMs,
    durationMs = item?.durationMs ?: 0,
    shuffling = shuffleState,
    repeatMode = when (repeatState) {
        "track" -> RepeatMode.TRACK
        "context" -> RepeatMode.CONTEXT
        else -> RepeatMode.OFF
    },
    deviceName = device?.name,
    deviceActive = device?.isActive ?: false,
)

fun SseSnapshotDto.toVotes(): Votes = Votes(
    current = votes.skipVotes,
    required = votes.requiredVotes,
)
