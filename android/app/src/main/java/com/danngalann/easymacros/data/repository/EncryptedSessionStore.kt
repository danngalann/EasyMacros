package com.danngalann.easymacros.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore("secure_session")

@Singleton
class EncryptedSessionStore @Inject constructor(@ApplicationContext private val context: Context) : SessionStore {
    override suspend fun read(): Session? {
        val preferences = context.sessionDataStore.data.first()
        val encoded = preferences[SESSION] ?: return null
        return runCatching { decrypt(encoded).split("\n", limit = 4).let { Session(it[0], it[1], it[2], it[3].toLong()) } }
            .getOrElse { context.sessionDataStore.edit { it.remove(SESSION) }; null }
    }

    override suspend fun write(session: Session) {
        val encoded = encrypt(listOf(session.accessToken, session.refreshToken, session.sessionId, session.expiresAtMillis).joinToString("\n"))
        context.sessionDataStore.edit { it[SESSION] = encoded }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.remove(SESSION) }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()))
    }

    private fun decrypt(value: String): String {
        val payload = Base64.getDecoder().decode(value)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, payload.copyOfRange(0, IV_LENGTH))) }
        return cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size)).decodeToString()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private companion object {
        val SESSION: Preferences.Key<String> = stringPreferencesKey("session")
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "easymacros_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}