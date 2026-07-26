package xyz.notarobot.excerpta

import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            try {
                val json = JSONObject(result.contents)
                val server = json.optString("server", "")
                val key = json.optString("key", "")
                val serverUri = try { android.net.Uri.parse(server) } catch (_: Exception) { null }
                val serverValid = serverUri?.scheme in listOf("http", "https") && !serverUri?.host.isNullOrBlank()
                if (server.isNotBlank() && key.isNotBlank() && serverValid) {
                    findViewById<EditText>(R.id.etServer).setText(server)
                    findViewById<EditText>(R.id.etApiKey).setText(key)
                    Prefs.save(this, server, key)
                    startActivity(
                        Intent(this, LinksActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                } else {
                    findViewById<TextView>(R.id.tvStatus).text = getString(R.string.qr_invalid)
                }
            } catch (e: Exception) {
                findViewById<TextView>(R.id.tvStatus).text = getString(R.string.qr_invalid)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeHelper.apply(this)
        setContentView(R.layout.activity_main)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        val lightStatusBar = Prefs.theme(this) in listOf("light", "nord", "solarized")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightStatusBar

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        val etServer = findViewById<EditText>(R.id.etServer)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val btnScan = findViewById<Button>(R.id.btnScan)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        etServer.setText(Prefs.serverUrl(this))
        etApiKey.setText(Prefs.apiKey(this))

        val actvTheme = findViewById<AutoCompleteTextView>(R.id.actvTheme)
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ThemeHelper.labels)
        actvTheme.setAdapter(themeAdapter)
        val currentThemeIdx = ThemeHelper.themes.indexOf(Prefs.theme(this))
        actvTheme.setText(ThemeHelper.labels.getOrNull(currentThemeIdx) ?: ThemeHelper.labels[0], false)
        actvTheme.setOnItemClickListener { _, _, position, _ ->
            Prefs.saveTheme(this, ThemeHelper.themes[position])
        }

        btnScan.setOnClickListener {
            val options = ScanOptions().apply {
                setPrompt(getString(R.string.scan_prompt))
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
            scanLauncher.launch(options)
        }

        btnSave.setOnClickListener {
            Prefs.save(this, etServer.text.toString(), etApiKey.text.toString())
            // Retour à LinksActivity (CLEAR_TOP la réutilise si elle est dans la pile,
            // ou en crée une nouvelle si c'est le premier lancement)
            startActivity(
                Intent(this, LinksActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }

        btnTest.setOnClickListener {
            val server = etServer.text.toString().trimEnd('/')
            val key = etApiKey.text.toString().trim()
            if (server.isBlank() || key.isBlank()) {
                tvStatus.text = getString(R.string.fill_both_fields)
                return@setOnClickListener
            }
            tvStatus.text = getString(R.string.testing)
            btnTest.isEnabled = false
            lifecycleScope.launch {
                val result = ApiClient.ping(server, key)
                tvStatus.text = result.text(this@MainActivity)
                btnTest.isEnabled = true
            }
        }

        etApiKey.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnSave.performClick()
                true
            } else false
        }
    }
}
