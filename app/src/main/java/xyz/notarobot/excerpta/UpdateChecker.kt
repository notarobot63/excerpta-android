package xyz.notarobot.excerpta

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val RELEASES_URL = BuildConfig.RELEASES_URL
    private const val APK_URL = BuildConfig.APK_URL

    data class UpdateInfo(val remoteCommit: String, val hasUpdate: Boolean)

    fun check(): UpdateInfo? {
        if (RELEASES_URL.isEmpty()) return null
        return try {
            val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                // GitLab Releases API : le texte est dans "description" (Gitea utilisait "body")
                val releaseBody = json.optString("description", "")
                val remoteCommit = Regex("commit:([0-9a-f]+)").find(releaseBody)?.groupValues?.get(1)
                if (remoteCommit != null) {
                    val current = BuildConfig.GIT_COMMIT
                    UpdateInfo(remoteCommit, remoteCommit != current && current != "unknown")
                } else null
            } else null
        } catch (_: Exception) { null }
    }

    fun openDownload(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
