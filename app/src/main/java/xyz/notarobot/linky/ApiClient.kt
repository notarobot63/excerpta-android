package xyz.notarobot.linky

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    data class Result(val success: Boolean, val message: String)

    suspend fun ping(serverUrl: String, apiKey: String): Result = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$serverUrl/api/v1/me").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val name = JSONObject(body).optString("name", "")
                Result(true, "Connecté en tant que $name")
            } else {
                Result(false, "Erreur $code — vérifiez l'URL et la clé API")
            }
        } catch (e: Exception) {
            Result(false, "Impossible de joindre le serveur : ${e.message}")
        }
    }

    suspend fun addLink(
        serverUrl: String,
        apiKey: String,
        url: String,
        title: String,
        tags: List<String>,
        note: String = "",
    ): Result = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("url", url)
                put("title", title)
                put("note", note)
                put("tags", JSONArray(tags))
            }.toString()

            val conn = URL("$serverUrl/api/v1/links").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.outputStream.use { it.write(body.toByteArray()) }

            when (val code = conn.responseCode) {
                201 -> Result(true, "Lien sauvegardé")
                401 -> Result(false, "Clé API invalide")
                400 -> Result(false, "URL invalide ou rejetée")
                else -> Result(false, "Erreur serveur ($code)")
            }
        } catch (e: Exception) {
            Result(false, "Erreur réseau : ${e.message}")
        }
    }
}
