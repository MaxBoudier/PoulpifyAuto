package fr.maxboudier.poulpifyauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.Track
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    state: PoulpifyUiState,
    positionProvider: () -> Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { NowPlayingCard(state, positionProvider, onPlayPause, onNext, onPrevious) }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "À suivre (${state.queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onToggleLock) {
                    Icon(
                        if (state.queueLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (state.queueLocked) "Déverrouiller la file"
                        else "Verrouiller la file",
                        tint = if (state.queueLocked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.queue.isEmpty()) {
            item {
                Text(
                    "La file est vide. Ajoute des sons depuis l'onglet Bibliothèque.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // La file peut contenir le meme titre plusieurs fois (deux
            // passagers qui ajoutent la meme chanson, ou le meme deux fois) :
            // l'URI seule n'est pas une cle unique, LazyColumn plante sinon.
            itemsIndexed(state.queue, key = { index, track -> "$index:${track.uri}" }) { _, track ->
                QueueRow(track)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        item {
            Text(
                "Passagers (${state.passengers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(
                if (state.passengers.isEmpty()) "Seul à bord 🐙"
                else state.passengers.joinToString("   ") { "${it.emoji} ${it.name}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NowPlayingCard(
    state: PoulpifyUiState,
    positionProvider: () -> Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val playback = state.nowPlaying
    val track = playback?.track

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (track?.imageUrl != null) {
                    AsyncImage(
                        model = track.imageUrl,
                        contentDescription = track.albumName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("🐙", style = MaterialTheme.typography.displayLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                track?.name ?: "Aucune lecture",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artistLabel ?: "Lance un son sur Spotify",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track?.addedViaPoulpify == true && track.addedBy != null) {
                Text(
                    "Ajouté par ${track.addedByEmoji ?: ""}${track.addedBy}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            // La progression est sondee localement : elle ne transite pas par
            // l'etat global, qui n'aurait aucune raison d'etre reemis 2 fois
            // par seconde pour ca.
            var position by remember(track?.uri) { mutableLongStateOf(positionProvider()) }
            LaunchedEffect(track?.uri, playback?.isPlaying) {
                while (true) {
                    position = positionProvider()
                    delay(500)
                }
            }
            val duration = playback?.durationMs ?: 0L
            LinearProgressIndicator(
                progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = playback?.canSkipPrevious != false) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Précédent")
                }
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                    Icon(
                        if (playback?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playback?.isPlaying == true) "Pause" else "Lecture",
                    )
                }
                IconButton(onClick = onNext, enabled = playback?.canSkipNext != false) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Suivant")
                }
            }

            if (state.votes.current > 0) {
                Text(
                    "Votes pour passer : ${state.votes.current}/${state.votes.required}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QueueRow(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (track.isInked) {
                Text("🐙")
            } else if (track.imageUrl != null) {
                AsyncImage(
                    model = track.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (track.isInked) "Titre surprise" else track.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(if (track.isInked) "🐙 Surprise" else track.artistLabel)
                    if (track.addedViaPoulpify && track.addedBy != null) {
                        append(" • ${track.addedByEmoji ?: ""}${track.addedBy}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
