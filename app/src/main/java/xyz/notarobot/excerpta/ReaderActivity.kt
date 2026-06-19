package xyz.notarobot.excerpta

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LINK_ID = "link_id"
        const val EXTRA_FALLBACK_TITLE = "fallback_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isConfigured(this)) {
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, true)
        ThemeHelper.apply(this)
        setContentView(R.layout.activity_reader)
        val lightStatusBar = Prefs.theme(this) in listOf("light", "nord", "solarized")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightStatusBar

        val linkId = intent.getIntExtra(EXTRA_LINK_ID, -1)
        val fallbackTitle = intent.getStringExtra(EXTRA_FALLBACK_TITLE) ?: getString(R.string.app_name)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val progress = findViewById<LinearProgressIndicator>(R.id.progress)
        val tvError = findViewById<TextView>(R.id.tvError)
        val webView = findViewById<WebView>(R.id.webView)

        toolbar.title = fallbackTitle
        toolbar.setNavigationOnClickListener { finish() }

        webView.settings.apply {
            javaScriptEnabled = false
            loadsImagesAutomatically = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        if (linkId <= 0) {
            finish()
            return
        }

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val reader = ApiClient.fetchReader(
                Prefs.serverUrl(this@ReaderActivity),
                Prefs.apiKey(this@ReaderActivity),
                linkId,
            )
            progress.visibility = View.GONE
            if (reader == null || reader.html.isBlank()) {
                tvError.visibility = View.VISIBLE
                tvError.text = "Vue lecteur indisponible pour ce lien."
                return@launch
            }
            if (reader.title.isNotBlank()) toolbar.title = reader.title
            webView.loadDataWithBaseURL(null, wrapHtml(reader.html), "text/html", "utf-8", null)
        }
    }

    /** Enveloppe le fragment HTML de l'article dans une page stylée selon le thème courant. */
    private fun wrapHtml(bodyHtml: String): String {
        val bg = colorHex(com.google.android.material.R.attr.colorSurface)
        val fg = colorHex(com.google.android.material.R.attr.colorOnSurface)
        val muted = colorHex(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val accent = colorHex(com.google.android.material.R.attr.colorPrimary)
        return """
            <!DOCTYPE html><html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              :root { color-scheme: light dark; }
              body { background:$bg; color:$fg; margin:0; padding:18px;
                     font-family:-apple-system, Roboto, sans-serif; font-size:17px; line-height:1.65;
                     word-wrap:break-word; overflow-wrap:break-word; }
              h1,h2,h3 { line-height:1.3; }
              h1 { font-size:1.5em; }
              a { color:$accent; }
              img,video,figure { max-width:100%; height:auto; }
              figcaption,small { color:$muted; }
              pre,code { white-space:pre-wrap; word-wrap:break-word;
                         background:rgba(127,127,127,0.12); border-radius:6px; }
              pre { padding:12px; overflow-x:auto; }
              blockquote { border-left:3px solid $muted; margin-left:0; padding-left:14px; color:$muted; }
            </style></head><body>$bodyHtml</body></html>
        """.trimIndent()
    }

    private fun colorHex(attr: Int): String {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return String.format("#%06X", 0xFFFFFF and tv.data)
    }
}
