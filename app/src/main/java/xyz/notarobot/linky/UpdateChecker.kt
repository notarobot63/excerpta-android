package xyz.notarobot.linky

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val RELEASES_URL =
        "https://git.notarobot.xyz/api/v1/repos/Thomas/excerpta-android/releases/tags/latest"
    private const val APK_URL =
        "https://git.notarobot.xyz/Thomas/excerpta-android/releases/download/latest/excerpta-android.apk"

    data class UpdateInfo(val remoteCommit: String, val hasUpdate: Boolean)

    fun check(): UpdateInfo? = try {
        val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val releaseBody = json.optString("body", "")
            val remoteCommit = Regex("commit:([0-9a-f]+)").find(releaseBody)?.groupValues?.get(1)
            if (remoteCommit != null) {
                val current = BuildConfig.GIT_COMMIT
                UpdateInfo(remoteCommit, remoteCommit != current && current != "unknown")
            } else null
        } else null
    } catch (_: Exception) { null }

    fun openDownload(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
