package xyz.notarobot.linky

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object Prefs {
    private const val NAME = "linky_prefs"
    private const val NAME_LEGACY = "linky_prefs_plain"
    private const val KEY_SERVER = "server_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_THEME = "theme"

    private fun getPrefs(ctx: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                NAME, masterKeyAlias, ctx,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            ctx.getSharedPreferences(NAME_LEGACY, Context.MODE_PRIVATE)
        }
    }

    fun serverUrl(ctx: Context): String =
        getPrefs(ctx).getString(KEY_SERVER, "") ?: ""

    fun apiKey(ctx: Context): String =
        getPrefs(ctx).getString(KEY_API_KEY, "") ?: ""

    fun save(ctx: Context, serverUrl: String, apiKey: String) {
        getPrefs(ctx).edit()
            .putString(KEY_SERVER, serverUrl.trimEnd('/'))
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun isConfigured(ctx: Context) = serverUrl(ctx).isNotBlank() && apiKey(ctx).isNotBlank()

    fun theme(ctx: Context): String =
        getPrefs(ctx).getString(KEY_THEME, "light") ?: "light"

    fun saveTheme(ctx: Context, theme: String) {
        getPrefs(ctx).edit()
            .putString(KEY_THEME, theme)
            .apply()
    }
}
