package xyz.notarobot.linky

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ShareActivity : AppCompatActivity() {

    private fun isValidUrl(url: String): Boolean = try {
        val uri = android.net.Uri.parse(url)
        uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) { false }

    override fun onStart() {
        super.onStart()
        val h = (resources.displayMetrics.heightPixels * 0.85).toInt()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyDialog(this)

        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, getString(R.string.configure_first), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_share)

        val raw = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: ""
        val sharedUrl = if (isValidUrl(raw)) raw else ""
        val sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim() ?: ""

        val etUrl = findViewById<TextInputEditText>(R.id.etUrl)
        val etTitle = findViewById<TextInputEditText>(R.id.etTitle)
        val etTags = findViewById<TextInputEditText>(R.id.etTags)
        val etNote = findViewById<TextInputEditText>(R.id.etNote)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupSuggestions)
        val suggestionsSection = findViewById<View>(R.id.suggestionsSection)

        etUrl.setText(sharedUrl)
        etTitle.setText(sharedTitle)

        // Charger les tags suggérés
        lifecycleScope.launch {
            val tags = ApiClient.fetchTags(
                Prefs.serverUrl(this@ShareActivity),
                Prefs.apiKey(this@ShareActivity),
            )
            if (tags.isNotEmpty()) {
                suggestionsSection.visibility = View.VISIBLE
                tags.forEach { tagInfo ->
                    val tagName = tagInfo.name
                    val chip = Chip(chipGroup.context).apply {
                        text = tagName
                        isCheckable = true
                    }
                    chip.setOnCheckedChangeListener { _, checked ->
                        val current = etTags.text?.toString()?.trim() ?: ""
                        val existing = current.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val updated = if (checked) {
                            (existing + tagName).distinct()
                        } else {
                            existing.filter { it != tagName }
                        }
                        etTags.setText(updated.joinToString(", "))
                    }
                    chipGroup.addView(chip)
                }
            }
        }

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
