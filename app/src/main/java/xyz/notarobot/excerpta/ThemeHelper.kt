package xyz.notarobot.excerpta

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
        // Ne pas écrire lastApplied : les activités dialog (ShareActivity) sont
        // transitoires et ne doivent pas masquer un changement de thème en attente
        // pour LinksActivity.needsRecreate().
        activity.setTheme(dialogResId(theme))
    }

    private fun fullResId(theme: String) = when (theme) {
        "dark"        -> R.style.Theme_Excerpta_Dark
        "dracula"     -> R.style.Theme_Excerpta_Dracula
        "nord"        -> R.style.Theme_Excerpta_Nord
        "nord-dark"   -> R.style.Theme_Excerpta_NordDark
        "catppuccin"  -> R.style.Theme_Excerpta_Catppuccin
        "gruvbox"     -> R.style.Theme_Excerpta_Gruvbox
        "solarized"   -> R.style.Theme_Excerpta_Solarized
        "rosepine"    -> R.style.Theme_Excerpta_RosePine
        else          -> R.style.Theme_Excerpta
    }

    private fun dialogResId(theme: String) = when (theme) {
        "dark"        -> R.style.Theme_Excerpta_Dark_Dialog
        "dracula"     -> R.style.Theme_Excerpta_Dracula_Dialog
        "nord"        -> R.style.Theme_Excerpta_Nord_Dialog
        "nord-dark"   -> R.style.Theme_Excerpta_NordDark_Dialog
        "catppuccin"  -> R.style.Theme_Excerpta_Catppuccin_Dialog
        "gruvbox"     -> R.style.Theme_Excerpta_Gruvbox_Dialog
        "solarized"   -> R.style.Theme_Excerpta_Solarized_Dialog
        "rosepine"    -> R.style.Theme_Excerpta_RosePine_Dialog
        else          -> R.style.Theme_Excerpta_Dialog
    }
}
