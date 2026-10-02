package xyz.notarobot.excerpta

import java.net.MalformedURLException
import java.net.URL

object ServerUrl {
    /**
     * Forme canonique d'une URL de serveur saisie ou scannée : sans espaces ni
     * slash final, schéma http(s) et hôte obligatoires. Renvoie null sinon.
     *
     * « links.mondomaine.fr » sans schéma est la faute de saisie la plus
     * probable : enregistrée telle quelle, chaque appel API échouait, et
     * l'écran d'accueil plantait au lancement.
     */
    fun normalize(raw: String): String? {
        val candidate = raw.trim().trimEnd('/')
        val url = try {
            URL(candidate)
        } catch (_: MalformedURLException) {
            return null
        }
        if (url.protocol !in listOf("http", "https") || url.host.isNullOrBlank()) return null
        return candidate
    }
}
