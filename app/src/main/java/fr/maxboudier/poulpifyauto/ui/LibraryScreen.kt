package fr.maxboudier.poulpifyauto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.Track

private enum class LibraryTab(val label: String) {
    SEARCH("Recherche"), LIKED("Likés"), TOP("Top"), RECENT("Récents")
}

@Composable
fun LibraryScreen(
    state: PoulpifyUiState,
    searchResults: List<Track>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onAddToQueue: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(LibraryTab.SEARCH) }
    var query by remember { mutableStateOf("") }

    val tracks = when (tab) {
        LibraryTab.SEARCH -> searchResults
        LibraryTab.LIKED -> state.likedTracks
        LibraryTab.TOP -> state.topTracks
        LibraryTab.RECENT -> state.recentTracks
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryTab.entries.forEach { entry ->
                    FilterChip(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        label = { Text(entry.label) },
                    )
                }
            }
        }

        if (tab == LibraryTab.SEARCH) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Chercher un titre") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { onSearch(query) }) {
                            Icon(Icons.Default.Search, contentDescription = "Chercher")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (searching && tab == LibraryTab.SEARCH) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Text(
                    when (tab) {
                        LibraryTab.SEARCH -> "Cherche un titre pour l'ajouter à la file."
                        else -> "Rien à afficher."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(tracks, key = { it.uri }) { track ->
                TrackRow(track, onAdd = { onAddToQueue(track) })
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = "Ajouter à la file")
        }
    }
}
