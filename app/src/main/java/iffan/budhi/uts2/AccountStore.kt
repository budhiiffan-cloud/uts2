package iffan.budhi.uts2

import android.content.Context
import org.json.JSONObject
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AccountStore(context: Context) {
    private val appContext = context.applicationContext
    companion object { @Volatile var generation = 0; private set }
    private val preferences = context.applicationContext.getSharedPreferences("tamafarm_session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("tamafarm_account", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("tamafarm_account", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun token(server: String): String? {
        if (preferences.getString("server", null) != server) return null
        val saved = preferences.getString("token", null) ?: return null
        return runCatching {
            val parts = saved.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse { clear(); null }
    }
    fun user(): JSONObject? = preferences.getString("user", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun save(server: String, token: String, user: JSONObject) {
        generation++
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        preferences.edit().clear().putString("server", server).putString("token", encrypted).putString("user", user.toString()).apply()
    }
    fun clear() {
        generation++
        preferences.edit().clear().apply()
        if (com.google.firebase.FirebaseApp.getApps(appContext).isNotEmpty()) com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        appContext.getSystemService(android.app.NotificationManager::class.java).cancelAll()
    }
}

internal object AccountValidation {
    fun phone(value: String) = Regex("^[+0-9 ()-]{6,20}$").matches(value) && value.count { it.isDigit() } >= 6
    fun password(value: String) = value.length >= 6
}
