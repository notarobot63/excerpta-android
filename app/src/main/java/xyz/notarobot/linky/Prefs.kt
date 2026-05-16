package xyz.notarobot.linky

import android.content.Context

object Prefs {
    private const val NAME = "linky_prefs"
    private const val KEY_SERVER = "server_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_THEME = "theme"

    fun serverUrl(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_SERVER, "") ?: ""

    fun apiKey(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_API_KEY, "") ?: ""

    fun save(ctx: Context, serverUrl: String, apiKey: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SERVER, serverUrl.trimEnd('/'))
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun isConfigured(ctx: Context) = serverUrl(ctx).isNotBlank() && apiKey(ctx).isNotBlank()

    fun theme(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_THEME, "light") ?: "light"

    fun saveTheme(ctx: Context, theme: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_THEME, theme)
            .apply()
    }
}
