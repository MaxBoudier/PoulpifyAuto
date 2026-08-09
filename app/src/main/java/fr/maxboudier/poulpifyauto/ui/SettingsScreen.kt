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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.maxboudier.poulpifyauto.core.model.AppConfig

@Composable
fun SettingsScreen(
    config: AppConfig?,
    onSaveServerUrl: (String) -> Unit,
    onSavePassword: (String) -> Unit,
    onSaveProfile: (String, String) -> Unit,
    onAutoStartChanged: (Boolean) -> Unit,
    onDisableAutoDisconnectChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var driverEmoji by remember { mutableStateOf("") }

    LaunchedEffect(config) {
        config ?: return@LaunchedEffect
        if (serverUrl.isEmpty()) serverUrl = config.serverUrl
        if (driverName.isEmpty()) driverName = config.driverName
        if (driverEmoji.isEmpty()) driverEmoji = config.driverEmoji
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Serveur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("URL du serveur Poulpify") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = {
                    Text(
                        if (config?.hostPassword.isNullOrBlank()) "Mot de passe hôte"
                        else "Mot de passe hôte (enregistré)"
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                supportingText = {
                    Text("Chiffré par le Keystore Android, jamais stocké en clair ni compilé dans l'APK.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    onSaveServerUrl(serverUrl)
                    if (password.isNotBlank()) onSavePassword(password)
                    password = ""
                    onRetry()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer et se connecter") }
        }

        item { HorizontalDivider() }

        item {
            Text("Profil conducteur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = driverEmoji,
                    onValueChange = { driverEmoji = it.take(2) },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.weight(0.3f),
                )
                OutlinedTextField(
                    value = driverName,
                    onValueChange = { driverName = it },
                    label = { Text("Nom affiché aux passagers") },
                    singleLine = true,
                    modifier = Modifier.weight(0.7f),
                )
            }
        }
        item {
            Button(
                onClick = { onSaveProfile(driverName, driverEmoji) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer le profil") }
        }

        item { HorizontalDivider() }

        item {
            SettingSwitch(
                title = "Démarrage automatique",
                subtitle = "Ouvrir la session hôte dès que le téléphone se branche à Android Auto",
                checked = config?.autoStartOnCarConnect ?: true,
                onCheckedChange = onAutoStartChanged,
            )
        }
        item {
            SettingSwitch(
                title = "Garder la session ouverte",
                subtitle = "Empêche le serveur de fermer la session hôte pendant une coupure réseau",
                checked = config?.disableServerAutoDisconnect ?: true,
                onCheckedChange = onDisableAutoDisconnectChanged,
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(end = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
