package xyz.notarobot.linky

import android.os.Bundle
import android.view.inputmethod.EditorInfo
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
                if (server.isNotBlank() && key.isNotBlank()) {
                    findViewById<EditText>(R.id.etServer).setText(server)
                    findViewById<EditText>(R.id.etApiKey).setText(key)
                    Prefs.save(this, server, key)
                    findViewById<TextView>(R.id.tvStatus).text = getString(R.string.settings_saved)
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
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        val etServer = findViewById<EditText>(R.id.etServer)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val btnScan = findViewById<Button>(R.id.btnScan)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        etServer.setText(Prefs.serverUrl(this))
        etApiKey.setText(Prefs.apiKey(this))

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
            tvStatus.text = getString(R.string.settings_saved)
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
                tvStatus.text = result.message
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
