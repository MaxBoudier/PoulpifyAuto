package fr.maxboudier.poulpifyauto.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrement AES/GCM adossé au Keystore Android pour le mot de passe hôte.
 *
 * On n'utilise pas `androidx.security:security-crypto` : la bibliothèque est
 * dépréciée et tire Tink pour un seul secret. Ici la clé ne quitte jamais le
 * Keystore matériel, et seul le chiffré transite par DataStore.
 */
internal object KeystoreCrypto {

    private const val KEY_ALIAS = "poulpify_host_secret"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** Renvoie `iv:chiffré`, tous deux en Base64. */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$iv:$payload"
    }

    /**
     * Renvoie null si le chiffré est illisible — cas réel après une
     * restauration de sauvegarde sur un autre appareil : la clé Keystore
     * n'a pas suivi. L'app redemandera simplement le mot de passe.
     */
    fun decrypt(stored: String): String? = runCatching {
        val (ivPart, payloadPart) = stored.split(":", limit = 2).let { it[0] to it[1] }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(ivPart, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(payloadPart, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
}
