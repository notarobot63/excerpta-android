package xyz.notarobot.linky

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

object ThemeHelper {

    val themes = listOf("light", "dark", "dracula", "nord", "nord-dark", "catppuccin", "gruvbox", "solarized", "rosepine")
    val labels = listOf("Light", "Dark", "Dracula", "Nord", "Nord Dark", "Catppuccin", "Gruvbox", "Solarized", "Rosé Pine")

    private var lastApplied = ""

    fun needsRecreate(ctx: Context): Boolean =
        lastApplied.isNotEmpty() && lastApplied != Prefs.theme(ctx)

    fun apply(activity: AppCompatActivity) {
        val theme = Prefs.theme(activity)
        lastApplied = theme
        activity.setTheme(fullResId(theme))
    }

    fun applyDialog(activity: AppCompatActivity) {
        val theme = Prefs.theme(activity)
        lastApplied = theme
        activity.setTheme(dialogResId(theme))
    }

    private fun fullResId(theme: String) = when (theme) {
        "dark"        -> R.style.Theme_Linky_Dark
        "dracula"     -> R.style.Theme_Linky_Dracula
        "nord"        -> R.style.Theme_Linky_Nord
        "nord-dark"   -> R.style.Theme_Linky_NordDark
        "catppuccin"  -> R.style.Theme_Linky_Catppuccin
        "gruvbox"     -> R.style.Theme_Linky_Gruvbox
        "solarized"   -> R.style.Theme_Linky_Solarized
        "rosepine"    -> R.style.Theme_Linky_RosePine
        else          -> R.style.Theme_Linky
    }

    private fun dialogResId(theme: String) = when (theme) {
        "dark"        -> R.style.Theme_Linky_Dark_Dialog
        "dracula"     -> R.style.Theme_Linky_Dracula_Dialog
        "nord"        -> R.style.Theme_Linky_Nord_Dialog
        "nord-dark"   -> R.style.Theme_Linky_NordDark_Dialog
        "catppuccin"  -> R.style.Theme_Linky_Catppuccin_Dialog
        "gruvbox"     -> R.style.Theme_Linky_Gruvbox_Dialog
        "solarized"   -> R.style.Theme_Linky_Solarized_Dialog
        "rosepine"    -> R.style.Theme_Linky_RosePine_Dialog
        else          -> R.style.Theme_Linky_Dialog
    }
}
