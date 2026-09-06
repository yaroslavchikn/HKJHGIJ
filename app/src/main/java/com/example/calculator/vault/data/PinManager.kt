package com.example.calculator.vault.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinManager(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    fun hasPin(): Boolean {
        return prefs.getString(KEY_HASH, null) != null
    }

    fun setPin(pin: String) {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt, ITERATIONS)

        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_ITERATIONS, ITERATIONS)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return false

        val expected = prefs.getString(KEY_HASH, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return false

        val iterations = prefs.getInt(KEY_ITERATIONS, ITERATIONS)

        return try {
            val actual = hashPin(pin, salt, iterations)
            constantTimeEquals(expected, actual)
        } catch (t: Throwable) {
            false
        }
    }

    private fun hashPin(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }

        return result == 0
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val PREFS_NAME = "vault_secure_prefs"
        const val FALLBACK_PREFS_NAME = "vault_fallback_prefs"

        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_ITERATIONS = "iterations"

        const val ITERATIONS = 120_000
        const val SALT_SIZE = 16
        const val KEY_LENGTH_BITS = 256
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}
