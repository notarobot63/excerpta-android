package xyz.notarobot.excerpta

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object Prefs {
    private const val NAME = "excerpta_prefs"
    private const val NAME_LEGACY = "excerpta_prefs_plain"
    private const val KEY_SERVER = "server_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_THEME = "theme"

    @Volatile private var _prefs: SharedPreferences? = null

    private fun getPrefs(ctx: Context): SharedPreferences {
        _prefs?.let { return it }
        return synchronized(this) {
            _prefs ?: try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    NAME, masterKeyAlias, ctx.applicationContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ).also { _prefs = it }
            } catch (e: Exception) {
                android.util.Log.w("Prefs", "EncryptedSharedPreferences indisponible, fallback non chiffré", e)
                ctx.applicationContext.getSharedPreferences(NAME_LEGACY, Context.MODE_PRIVATE)
                    .also { _prefs = it }
            }
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
