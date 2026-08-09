package fr.maxboudier.poulpifyauto.core.network.dto

import kotlinx.serialization.Serializable

/** Charge utile diffusée par `GET /api/events` (SSE). Remplace le sondage côté client. */
@Serializable
data class SseSnapshotDto(
    val at: Long = 0,
    val status: StatusResponseDto,
    val player: PlayerStateDto? = null,
    val queue: List<TrackDto> = emptyList(),
    val passengers: List<UserDto> = emptyList(),
    val votes: SseVotesDto = SseVotesDto(),
    val recentJoins: List<UserDto> = emptyList(),
)

@Serializable
data class SseVotesDto(
    val skipVotes: Int = 0,
    val requiredVotes: Int = 1,
)
