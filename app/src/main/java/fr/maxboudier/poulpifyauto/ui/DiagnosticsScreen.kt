package fr.maxboudier.poulpifyauto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.maxboudier.poulpifyauto.core.model.AppConfig
import fr.maxboudier.poulpifyauto.core.model.ConnectionState
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.RemoteState

private data class Check(
    val label: String,
    val ok: Boolean,
    val detail: String,
    /** Ce qu'il faut faire quand ça ne va pas. */
    val remedy: String? = null,
)

/**
 * L'écran qui manquait le plus à l'ancienne version : chaque brique de la
 * chaîne, son état, et le geste correctif quand elle est en défaut. Sans lui,
 * une panne se manifeste par « rien ne marche » sans aucune piste.
 */
@Composable
fun DiagnosticsScreen(
    state: PoulpifyUiState,
    config: AppConfig?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checks = buildChecks(state, config)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Diagnostic", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = onRetry) { Text("Tout retester") }
            }
        }

        items(checks.size) { index ->
            val check = checks[index]
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (check.ok) "✅" else "⚠️")
                        Text(
                            check.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        check.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!check.ok && check.remedy != null) {
                        Text(
                            "→ ${check.remedy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun buildChecks(state: PoulpifyUiState, config: AppConfig?): List<Check> = listOf(
    Check(
        label = "Configuration",
        ok = config?.isComplete == true,
        detail = if (config?.isComplete == true) {
            "Serveur ${config.serverUrl} • conducteur ${config.driverEmoji} ${config.driverName}"
        } else {
            "URL du serveur ou mot de passe hôte manquant"
        },
        remedy = "Renseigne les deux dans l'onglet Réglages",
    ),
    Check(
        label = "Session hôte",
        ok = state.connection == ConnectionState.CONNECTED,
        detail = when (state.connection) {
            ConnectionState.CONNECTED -> "Session hôte ouverte sur le serveur"
            ConnectionState.AUTHENTICATING -> "Authentification en cours…"
            ConnectionState.RECONNECTING -> "Reconnexion en cours…"
            ConnectionState.DEGRADED -> "Session ouverte, mais Spotify n'est pas autorisé côté serveur"
            ConnectionState.DISCONNECTED -> "Aucune session hôte"
        },
        remedy = when (state.connection) {
            ConnectionState.DEGRADED ->
                "Ouvre ${config?.serverUrl ?: "le site"} dans un navigateur, connecte-toi en hôte " +
                    "et lance « Connexion à Spotify » une fois"
            ConnectionState.DISCONNECTED -> "Vérifie le mot de passe hôte et la connexion réseau"
            else -> null
        },
    ),
    Check(
        label = "Spotify App Remote",
        ok = state.remote == RemoteState.CONNECTED,
        detail = when (state.remote) {
            RemoteState.CONNECTED -> "Connecté à l'application Spotify du téléphone"
            RemoteState.CONNECTING -> "Connexion en cours…"
            RemoteState.NOT_INSTALLED -> "Application Spotify absente"
            RemoteState.UNAUTHORIZED -> "Spotify a refusé l'autorisation"
            RemoteState.DISCONNECTED -> "Non connecté"
        },
        remedy = when (state.remote) {
            RemoteState.NOT_INSTALLED -> "Installe Spotify depuis le Play Store"
            RemoteState.UNAUTHORIZED ->
                "Enregistre le package fr.maxboudier.poulpifyauto, l'empreinte SHA-1 de cette " +
                    "signature et l'URI poulpifyauto://callback dans le dashboard Spotify"
            RemoteState.DISCONNECTED -> "Ouvre Spotify et lance une lecture, puis retente"
            else -> null
        },
    ),
    Check(
        label = "Appareil Spotify actif",
        ok = state.nowPlaying?.deviceActive == true,
        detail = state.nowPlaying?.deviceName?.let { "Lecture sur $it" }
            ?: "Aucun appareil Spotify actif",
        remedy = "Lance une lecture dans Spotify : sans appareil actif, l'API refuse les ajouts à la file",
    ),
    Check(
        label = "Titre en cours",
        ok = state.nowPlaying?.track != null,
        detail = state.nowPlaying?.track?.let { "${it.name} — ${it.artistLabel}" }
            ?: "Rien en lecture",
        remedy = "Lance un son dans Spotify",
    ),
    Check(
        label = "File collaborative",
        ok = true,
        detail = "${state.queue.size} titre(s) en file • " +
            if (state.queueLocked) "verrouillée" else "ouverte aux passagers",
    ),
    Check(
        label = "Passagers connectés",
        ok = true,
        detail = if (state.passengers.isEmpty()) "Personne à bord"
        else state.passengers.joinToString(", ") { "${it.emoji} ${it.name}" },
    ),
) + listOfNotNull(
    state.lastError?.let {
        Check(
            label = "Dernière erreur",
            ok = false,
            detail = it.message,
            remedy = if (it.retryable) "Appuie sur « Tout retester »" else null,
        )
    }
)
