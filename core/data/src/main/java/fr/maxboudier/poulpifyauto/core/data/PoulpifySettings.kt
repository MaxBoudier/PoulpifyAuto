package fr.maxboudier.poulpifyauto.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.maxboudier.poulpifyauto.core.model.AppConfig
import fr.maxboudier.poulpifyauto.core.model.ConfigSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "poulpify_settings")

/**
 * Réglages persistés. Le mot de passe hôte n'est jamais stocké en clair et
 * n'est jamais compilé dans l'APK — contrairement à l'ancienne version qui
 * l'avait écrit en dur dans le code source.
 */
class PoulpifySettings(private val context: Context) : ConfigSource {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val HOST_PASSWORD = stringPreferencesKey("host_password_encrypted")
        val DRIVER_NAME = stringPreferencesKey("driver_name")
        val DRIVER_EMOJI = stringPreferencesKey("driver_emoji")
        val AUTO_START = booleanPreferencesKey("auto_start_on_car_connect")
        val DISABLE_AUTO_DISCONNECT = booleanPreferencesKey("disable_server_auto_disconnect")
        val TAP_ADDS_TO_QUEUE = booleanPreferencesKey("tap_adds_to_queue")
    }

    override val config: Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            serverUrl = prefs[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL,
            hostPassword = prefs[Keys.HOST_PASSWORD]?.let { KeystoreCrypto.decrypt(it) },
            driverName = prefs[Keys.DRIVER_NAME] ?: DEFAULT_DRIVER_NAME,
            driverEmoji = prefs[Keys.DRIVER_EMOJI] ?: DEFAULT_DRIVER_EMOJI,
            autoStartOnCarConnect = prefs[Keys.AUTO_START] ?: true,
            disableServerAutoDisconnect = prefs[Keys.DISABLE_AUTO_DISCONNECT] ?: true,
            tapAddsToQueue = prefs[Keys.TAP_ADDS_TO_QUEUE] ?: true,
        )
    }

    override suspend fun current(): AppConfig = config.first()

    suspend fun setServerUrl(url: String) = edit { it[Keys.SERVER_URL] = url.trim().removeSuffix("/") }

    suspend fun setHostPassword(password: String) = edit {
        it[Keys.HOST_PASSWORD] = KeystoreCrypto.encrypt(password)
    }

    suspend fun clearHostPassword() = edit { it.remove(Keys.HOST_PASSWORD) }

    suspend fun setDriverProfile(name: String, emoji: String) = edit {
        it[Keys.DRIVER_NAME] = name.ifBlank { DEFAULT_DRIVER_NAME }
        it[Keys.DRIVER_EMOJI] = emoji.ifBlank { DEFAULT_DRIVER_EMOJI }
    }

    suspend fun setAutoStartOnCarConnect(enabled: Boolean) = edit { it[Keys.AUTO_START] = enabled }

    suspend fun setDisableServerAutoDisconnect(enabled: Boolean) = edit {
        it[Keys.DISABLE_AUTO_DISCONNECT] = enabled
    }

    suspend fun setTapAddsToQueue(enabled: Boolean) = edit { it[Keys.TAP_ADDS_TO_QUEUE] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://poulpify.maxboudier.fr"
        const val DEFAULT_DRIVER_NAME = "Poulpi"
        const val DEFAULT_DRIVER_EMOJI = "🐙"
    }
}
