package xyz.notarobot.linky

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinksActivity : AppCompatActivity() {

    private val adapter = LinkAdapter()
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false
    private var searchJob: Job? = null
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isConfigured(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        ThemeHelper.apply(this)
        setContentView(R.layout.activity_links)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val recycler = findViewById<RecyclerView>(R.id.recyclerView)
        val progress = findViewById<LinearProgressIndicator>(R.id.progress)
        val tvEmpty = findViewById<View>(R.id.tvEmpty)
        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Pagination + shrink FAB au scroll
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 8) fab.shrink() else if (dy < -8) fab.extend()
                val lm = rv.layoutManager as LinearLayoutManager
                if (!isLoading && currentPage < totalPages &&
                    lm.findLastVisibleItemPosition() >= adapter.itemCount - 5
                ) {
                    loadPage(currentPage + 1, append = true)
                }
            }
        })

        // Recherche avec debounce
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(400)
                    currentQuery = s?.toString()?.trim() ?: ""
                    resetAndLoad()
                }
            }
        })
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(etSearch.windowToken, 0)
                true
            } else false
        }

        fab.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        fun updateEmpty(count: Int) {
            tvEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = updateEmpty(adapter.itemCount)
            override fun onItemRangeInserted(p: Int, c: Int) = updateEmpty(adapter.itemCount)
        })

        // Stocker progress/empty pour loadPage
        this.progressView = progress
        this.emptyView = tvEmpty

        loadPage(1, append = false)
        checkForUpdate(recycler)
    }

    private fun checkForUpdate(anchor: View) {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
            if (info != null && info.hasUpdate) {
                Snackbar.make(anchor, "Mise à jour disponible (${info.remoteCommit})", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Installer") { UpdateChecker.openDownload(this@LinksActivity) }
                    .show()
            }
        }
    }

    private lateinit var progressView: View
    private lateinit var emptyView: View

    override fun onResume() {
        super.onResume()
        if (ThemeHelper.needsRecreate(this)) recreate()
    }

    private fun resetAndLoad() {
        currentPage = 1
        totalPages = 1
        adapter.submitList(emptyList())
        loadPage(1, append = false)
    }

    private fun loadPage(page: Int, append: Boolean) {
        if (isLoading) return
        isLoading = true
        progressView.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = ApiClient.fetchLinks(
                serverUrl = Prefs.serverUrl(this@LinksActivity),
                apiKey = Prefs.apiKey(this@LinksActivity),
                page = page,
                q = currentQuery,
            )
            progressView.visibility = View.GONE
            isLoading = false
            currentPage = result.page
            totalPages = result.totalPages

            val newList = if (append) (adapter.currentList + result.links) else result.links
            adapter.submitList(newList)
            emptyView.visibility = if (newList.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
