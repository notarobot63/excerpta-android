package xyz.notarobot.linky

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, getString(R.string.configure_first), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_share)

        val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: ""
        val sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim() ?: ""

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etTags = findViewById<EditText>(R.id.etTags)
        val etNote = findViewById<EditText>(R.id.etNote)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val progress = findViewById<ProgressBar>(R.id.progress)

        etUrl.setText(sharedUrl)
        etTitle.setText(sharedTitle)

        btnCancel.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isBlank()) {
                etUrl.error = getString(R.string.url_required)
                return@setOnClickListener
            }
            val title = etTitle.text.toString().trim()
            val note = etNote.text.toString().trim()
            val tags = etTags.text.toString()
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }

            btnSave.isEnabled = false
            btnCancel.isEnabled = false
            progress.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = ApiClient.addLink(
                    serverUrl = Prefs.serverUrl(this@ShareActivity),
                    apiKey = Prefs.apiKey(this@ShareActivity),
                    url = url,
                    title = title,
                    tags = tags,
                    note = note,
                )
                progress.visibility = View.GONE
                if (result.success) {
                    Toast.makeText(this@ShareActivity, result.message, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ShareActivity, result.message, Toast.LENGTH_LONG).show()
                    btnSave.isEnabled = true
                    btnCancel.isEnabled = true
                }
            }
        }
    }
}
