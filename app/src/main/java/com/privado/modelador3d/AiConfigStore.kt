package com.privado.modelador3d

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

data class AiConfig(val providerIndex: Int, val baseUrl: String, val model: String, val apiKey: String)

class AiConfigStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
    private val alias = "modelador3d_ai_key"

    private fun key() = (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(alias, null) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        })

    fun save(config: AiConfig) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = JSONObject().apply {
            put("provider", config.providerIndex); put("baseUrl", config.baseUrl)
            put("model", config.model); put("apiKey", config.apiKey)
        }.toString().toByteArray()
        prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(cipher.doFinal(json), Base64.NO_WRAP)).apply()
    }

    fun read(): AiConfig {
        return try {
            val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
            val data = Base64.decode(prefs.getString("data", ""), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(data)))
            AiConfig(json.optInt("provider", 0), json.optString("baseUrl"), json.optString("model"), json.optString("apiKey"))
        } catch (_: Exception) { AiConfig(0, "https://api.groq.com/openai/v1", "", "") }
    }
}
