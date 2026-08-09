package fr.maxboudier.poulpifyauto.core.network

import fr.maxboudier.poulpifyauto.core.network.dto.DevicesResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.HeartbeatRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.HeartbeatResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.LoginRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.LoginResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.PlayerStateDto
import fr.maxboudier.poulpifyauto.core.network.dto.PlaylistTracksResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.PlaylistsResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.QueueAddRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.QueueResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.RecentlyPlayedResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.SavedTracksResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.SearchResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.StatusResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.ToggleLockResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.TopTracksResponseDto
import fr.maxboudier.poulpifyauto.core.network.dto.TransferPlaybackRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.VoteSkipRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.VoteSkipResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Miroir 1-1 des routes de `server/index.js`. Le jeton hôte est passé en
 * en-tête `Authorization: Bearer <token>` par [fr.maxboudier.poulpifyauto.core.network.AuthInterceptor],
 * jamais construit à la main ici.
 */
interface PoulpifyApi {

    @GET("api/status")
    suspend fun getStatus(): Response<StatusResponseDto>

    @POST("api/host/login")
    suspend fun loginHost(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @POST("api/host/logout")
    suspend fun logoutHost(): Response<Unit>

    @POST("api/host/spotify/disconnect")
    suspend fun disconnectSpotify(): Response<Unit>

    @POST("api/heartbeat")
    suspend fun heartbeat(@Body request: HeartbeatRequestDto): Response<HeartbeatResponseDto>

    @POST("api/toggle-lock")
    suspend fun toggleLock(): Response<ToggleLockResponseDto>

    @GET("api/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 10): Response<SearchResponseDto>

    @POST("api/queue")
    suspend fun addToQueue(@Body request: QueueAddRequestDto): Response<Unit>

    @GET("api/player")
    suspend fun getPlayer(): Response<PlayerStateDto>

    @GET("api/player-queue")
    suspend fun getPlayerQueue(): Response<QueueResponseDto>

    @POST("api/vote-skip")
    suspend fun voteSkip(@Body request: VoteSkipRequestDto): Response<VoteSkipResponseDto>

    @GET("api/me/playlists")
    suspend fun getPlaylists(): Response<PlaylistsResponseDto>

    @GET("api/me/tracks")
    suspend fun getLikedTracks(): Response<SavedTracksResponseDto>

    @GET("api/me/top-tracks")
    suspend fun getTopTracks(): Response<TopTracksResponseDto>

    @GET("api/me/recently-played")
    suspend fun getRecentlyPlayed(): Response<RecentlyPlayedResponseDto>

    @GET("api/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(@Path("id") id: String): Response<PlaylistTracksResponseDto>

    // --- Contrôle de lecture hôte (nouveau : cf. Phase 0 du plan) ---

    @PUT("api/host/player/play")
    suspend fun play(@Query("device_id") deviceId: String? = null): Response<Unit>

    @PUT("api/host/player/pause")
    suspend fun pause(@Query("device_id") deviceId: String? = null): Response<Unit>

    @POST("api/host/player/next")
    suspend fun skipNext(@Query("device_id") deviceId: String? = null): Response<Unit>

    @POST("api/host/player/previous")
    suspend fun skipPrevious(@Query("device_id") deviceId: String? = null): Response<Unit>

    @PUT("api/host/player/seek")
    suspend fun seek(@Query("position_ms") positionMs: Long): Response<Unit>

    @PUT("api/host/player/shuffle")
    suspend fun setShuffle(@Query("state") state: Boolean): Response<Unit>

    @PUT("api/host/player/repeat")
    suspend fun setRepeat(@Query("state") state: String): Response<Unit>

    @PUT("api/host/player/volume")
    suspend fun setVolume(@Query("volume_percent") volumePercent: Int): Response<Unit>

    @POST("api/host/skip")
    suspend fun hostSkip(): Response<Unit>

    @GET("api/host/devices")
    suspend fun getDevices(): Response<DevicesResponseDto>

    @PUT("api/host/transfer")
    suspend fun transferPlayback(@Body request: TransferPlaybackRequestDto): Response<Unit>
}
