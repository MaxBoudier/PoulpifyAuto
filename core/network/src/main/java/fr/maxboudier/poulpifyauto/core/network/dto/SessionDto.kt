package fr.maxboudier.poulpifyauto.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatusResponseDto(
    val authenticated: Boolean,
    val queueLocked: Boolean,
    val hostActive: Boolean,
    val autoDisconnectEnabled: Boolean,
    val spotifyDeviceActive: Boolean = false,
    val serverVersion: String? = null,
)

@Serializable
data class LoginRequestDto(
    val password: String,
    val force: Boolean = false,
    val autoDisconnectEnabled: Boolean? = null,
)

@Serializable
data class LoginResponseDto(
    val success: Boolean,
    val hostToken: String? = null,
    val spotifyAuthenticated: Boolean = false,
    val autoDisconnectEnabled: Boolean = true,
    val queueLocked: Boolean = false,
)

@Serializable
data class HeartbeatRequestDto(
    val username: String? = null,
    val emoji: String? = null,
    val hostToken: String? = null,
)

@Serializable
data class HeartbeatResponseDto(
    val activeUsers: List<UserDto> = emptyList(),
    val skipVotes: Int = 0,
    val requiredVotes: Int = 1,
    val hasVoted: Boolean = false,
    val recentJoins: List<UserDto>? = null,
)

@Serializable
data class VoteSkipRequestDto(
    val username: String,
)

@Serializable
data class VoteSkipResponseDto(
    val success: Boolean,
    val skipVotes: Int,
    val requiredVotes: Int,
)

@Serializable
data class ToggleLockResponseDto(
    val success: Boolean,
    val queueLocked: Boolean,
)

@Serializable
data class ErrorResponseDto(
    val error: String,
)
