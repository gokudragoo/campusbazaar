package app.campusbazaar.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.campusbazaar.data.api.CampusDto
import app.campusbazaar.data.api.UserDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Everything needed to resume a signed-in session after process death or a reboot. */
@Serializable
data class Session(
    val apiToken: String,
    val user: UserDto,
    val campus: CampusDto? = null,
    val nexconnAppKey: String,
    val nexconnUserId: String,
    val nexconnToken: String,
)

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Encrypted session storage: the session JSON is sealed with AES-256-GCM using a non-exportable
 * Android Keystore key, then stored in DataStore. Excluded from backups (see backup_rules.xml).
 */
class SessionStore(private val context: Context, private val json: Json) {
    private val key = stringPreferencesKey("sealed_session")

    suspend fun load(): Session? {
        val sealed = context.sessionDataStore.data.first()[key] ?: return null
        return runCatching { json.decodeFromString(Session.serializer(), KeystoreCipher.open(sealed)) }.getOrNull()
    }

    suspend fun save(session: Session) {
        val sealed = KeystoreCipher.seal(json.encodeToString(Session.serializer(), session))
        context.sessionDataStore.edit { it[key] = sealed }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(key) }
    }
}

internal object KeystoreCipher {
    private const val ALIAS = "campusbazaar_session_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    fun open(sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
