package xyz.notarobot.excerpta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ShareActivity : AppCompatActivity() {

    private fun isValidUrl(url: String): Boolean = try {
        val uri = android.net.Uri.parse(url)
        uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) { false }

    /**
     * Extrait l'URL d'un texte partage.
     *
     * La plupart des applis (YouTube, X, navigateurs mobiles) n'envoient pas
     * une URL nue dans EXTRA_TEXT mais « Un titre quelconque https://… ».
     * Exiger que la chaine entiere soit une URL laissait le champ vide dans
     * ces cas, le plus frequent en pratique.
     */
    private fun extractUrl(raw: String): String {
        if (isValidUrl(raw)) return raw
        return Regex("""https?://\S+""")
            .findAll(raw)
            .map { it.value.trimEnd('.', ',', ')', ']', '»', '"', '\'') }
            .firstOrNull { isValidUrl(it) }
            ?: ""
    }

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
        val sharedUrl = extractUrl(raw)
        // On ne dérive pas de titre du texte restant : il arrive tronqué ou
        // ponctué de travers, et un champ non vide empêcherait fetchMeta de
        // renseigner le vrai titre côté serveur.
        val sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim() ?: ""

        val etUrl = findViewById<TextInputEditText>(R.id.etUrl)
        val etTitle = findViewById<TextInputEditText>(R.id.etTitle)
        val etDescription = findViewById<TextInputEditText>(R.id.etDescription)
        val etTags = findViewById<MaterialAutoCompleteTextView>(R.id.etTags)
        val etNote = findViewById<TextInputEditText>(R.id.etNote)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)
        val progress = findViewById<ProgressBar>(R.id.progress)
        etUrl.setText(sharedUrl)
        etTitle.setText(sharedTitle)

        val tagsEnabled = Prefs.tagsEnabled(this)
        findViewById<View>(R.id.tilTags).visibility = if (tagsEnabled) View.VISIBLE else View.GONE

        // Les applis tierces ne renseignent quasiment jamais EXTRA_SUBJECT/description :
        // on va chercher titre + extrait côté serveur, sans écraser une saisie déjà présente.
        if (sharedUrl.isNotBlank()) {
            progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                val meta = ApiClient.fetchMeta(
                    Prefs.serverUrl(this@ShareActivity),
                    Prefs.apiKey(this@ShareActivity),
                    sharedUrl,
                )
                if (meta != null) {
                    if (etTitle.text.isNullOrBlank() && meta.title.isNotBlank()) {
                        etTitle.setText(meta.title)
                    }
                    if (etDescription.text.isNullOrBlank() && meta.description.isNotBlank()) {
                        etDescription.setText(meta.description)
                    }
                }
                progress.visibility = View.GONE
            }
        }

        if (tagsEnabled) lifecycleScope.launch {
            val fetchedTags = ApiClient.fetchTags(
                Prefs.serverUrl(this@ShareActivity),
                Prefs.apiKey(this@ShareActivity),
            )
            if (fetchedTags.isNotEmpty()) {
                val names = ArrayList(fetchedTags.map { it.name })
                val tagAdapter = ArrayAdapter(
                    this@ShareActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    names,
                )
                etTags.setAdapter(tagAdapter)
                etTags.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val text = s?.toString() ?: return
                        val lastComma = text.lastIndexOf(',')
                        val token = if (lastComma >= 0) text.substring(lastComma + 1).trimStart() else text
                        tagAdapter.filter.filter(token)
                    }
                })
                etTags.onItemClickListener = android.widget.AdapterView.OnItemClickListener { parent, _, pos, _ ->
                    val selected = parent.getItemAtPosition(pos) as String
                    val current = etTags.text?.toString() ?: ""
                    val lastComma = current.lastIndexOf(',')
                    val prefix = if (lastComma >= 0) "${current.substring(0, lastComma + 1)} " else ""
                    etTags.setText("$prefix$selected, ")
                    etTags.setSelection(etTags.text?.length ?: 0)
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
            val description = etDescription.text.toString().trim()
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
                    description = description,
                    note = note,
                )
                progress.visibility = View.GONE
                when {
                    result.success -> {
                        Toast.makeText(this@ShareActivity, result.text(this@ShareActivity), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    result.isNetworkError -> {
                        val queued = PendingQueue.enqueue(
                            this@ShareActivity,
                            PendingQueue.PendingLink(
                                url = url,
                                title = title,
                                tags = tags,
                                description = description,
                                note = note,
                                folderId = null,
                                isPublic = false,
                            ),
                        )
                        val msg = if (queued) getString(R.string.queued_offline)
                                  else getString(R.string.queue_full)
                        Toast.makeText(this@ShareActivity, msg, Toast.LENGTH_LONG).show()
                        if (queued) {
                            finish()
                        } else {
                            btnSave.isEnabled = true
                            btnCancel.isEnabled = true
                        }
                    }
                    else -> {
                        Toast.makeText(this@ShareActivity, result.text(this@ShareActivity), Toast.LENGTH_LONG).show()
                        btnSave.isEnabled = true
                        btnCancel.isEnabled = true
                    }
                }
            }
        }
    }
}
