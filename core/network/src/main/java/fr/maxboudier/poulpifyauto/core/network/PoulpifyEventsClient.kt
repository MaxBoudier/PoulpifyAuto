package fr.maxboudier.poulpifyauto.core.network

import fr.maxboudier.poulpifyauto.core.network.dto.SseSnapshotDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

sealed interface SseEvent {
    data class Snapshot(val data: SseSnapshotDto) : SseEvent
    data object Connected : SseEvent
    data class Disconnected(val willRetryInMs: Long) : SseEvent
}

/**
 * Flux d'événements temps réel du serveur, avec reconnexion automatique et
 * backoff exponentiel intégrés : le consommateur n'a qu'à collecter le flux,
 * qui ne se termine jamais tant qu'il est actif.
 *
 * Remplace le sondage 1s/2s/3s que faisait l'ancienne app sur trois routes
 * différentes en boucle.
 */
class PoulpifyEventsClient(
    private val url: String,
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun connect(): Flow<SseEvent> = callbackFlow {
        var backoffMs = MIN_BACKOFF_MS

        val loop = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val closedSignal = CompletableDeferred<Unit>()

                val listener = object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        backoffMs = MIN_BACKOFF_MS
                        trySend(SseEvent.Connected)
                    }

                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        val snapshot = runCatching {
                            json.decodeFromString(SseSnapshotDto.serializer(), data)
                        }.getOrNull()
                        if (snapshot != null) trySend(SseEvent.Snapshot(snapshot))
                    }

                    override fun onClosed(eventSource: EventSource) {
                        closedSignal.complete(Unit)
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        closedSignal.complete(Unit)
                    }
                }

                val source = EventSources.createFactory(client)
                    .newEventSource(Request.Builder().url(url).build(), listener)

                closedSignal.await()
                source.cancel()

                trySend(SseEvent.Disconnected(backoffMs))
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }

        awaitClose { loop.cancel() }
    }

    companion object {
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }
}
