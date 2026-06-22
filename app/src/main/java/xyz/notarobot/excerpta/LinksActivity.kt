package xyz.notarobot.excerpta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    private var currentTag: String? = null
    private var currentGroupId: Int? = null
    private var currentGroupName: String? = null

    private lateinit var progressView: View
    private lateinit var emptyView: View
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerNav: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var chipScrollView: View
    private lateinit var chipGroup: ChipGroup

    private var selectedNavView: View? = null
    private var lastGroups: List<ApiClient.GroupItem> = emptyList()
    private var lastTags: List<ApiClient.TagInfo> = emptyList()
    private var listNeedsRefresh = false
    private val cacheFile by lazy { File(cacheDir, "links_cache.json") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isConfigured(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeHelper.apply(this)
        setContentView(R.layout.activity_links)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        val lightStatusBar = Prefs.theme(this) in listOf("light", "nord", "solarized")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightStatusBar

        drawerLayout = findViewById(R.id.drawerLayout)
        drawerNav = findViewById(R.id.drawerNav)
        toolbar = findViewById(R.id.toolbar)

        // DrawerLayout peint lui-même la status bar en colorPrimary (bleu) quand
        // fitsSystemWindows=true — on force la couleur du fond du thème
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        drawerLayout.setStatusBarBackgroundColor(tv.data)
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        chipScrollView = findViewById(R.id.chipScrollView)
        chipGroup = findViewById(R.id.chipGroup)
        val progress = findViewById<LinearProgressIndicator>(R.id.progress)
        val tvEmpty = findViewById<View>(R.id.tvEmpty)
        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)

        progressView = progress
        emptyView = tvEmpty

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        // Fermer le drawer avec le bouton retour
        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { resetAndLoad() }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (!isLoading && currentPage < totalPages &&
                    lm.findLastVisibleItemPosition() >= adapter.itemCount - 5
                ) {
                    loadPage(currentPage + 1, append = true)
                }
            }
        })

        adapter.onLongClick = { item -> showLinkMenu(item) }

        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener {
            listNeedsRefresh = true
            startActivity(Intent(this, ShareActivity::class.java))
        }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    if (q.isNotEmpty()) delay(300)
                    currentQuery = q
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

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = updateEmpty()
            override fun onItemRangeInserted(p: Int, c: Int) = updateEmpty()
        })

        loadPage(1, append = false)
        buildDrawer()
        checkForUpdate(recyclerView)
        if (!Prefs.isEncrypted(this)) {
            Snackbar.make(recyclerView, "⚠️ Stockage non chiffré (KeyStore indisponible)", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ThemeHelper.needsRecreate(this)) recreate()
        else {
            flushPendingQueue()
            if (listNeedsRefresh) {
                listNeedsRefresh = false
                resetAndLoad()
            }
        }
    }

    private fun flushPendingQueue() {
        if (PendingQueue.isEmpty(this)) return
        val pending = PendingQueue.load(this)
        lifecycleScope.launch {
            // On garde en file les seuls échecs réseau (à retenter plus tard).
            // Les échecs métier (URL rejetée, clé invalide…) sont retirés de la
            // file mais signalés à l'utilisateur : on ne les perd pas en silence.
            val failed = mutableListOf<PendingQueue.PendingLink>()
            var sent = 0
            var rejected = 0
            for (link in pending) {
                val result = ApiClient.addLink(
                    serverUrl = Prefs.serverUrl(this@LinksActivity),
                    apiKey = Prefs.apiKey(this@LinksActivity),
                    url = link.url,
                    title = link.title,
                    tags = link.tags,
                    note = link.note,
                    folderId = link.folderId,
                    isPublic = link.isPublic,
                )
                when {
                    result.success -> sent++
                    result.isNetworkError -> failed.add(link)
                    else -> rejected++  // erreur métier : retiré de la file
                }
            }
            PendingQueue.replace(this@LinksActivity, failed)
            if (sent > 0) {
                Snackbar.make(
                    recyclerView,
                    getString(R.string.queued_synced, sent),
                    Snackbar.LENGTH_SHORT,
                ).show()
                resetAndLoad()
            }
            if (rejected > 0) {
                Snackbar.make(
                    recyclerView,
                    getString(R.string.queued_rejected, rejected),
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }
    }

    // ── Drawer ──────────────────────────────────────────────────────────────

    private fun buildDrawer() {
        renderDrawer()  // rendu immédiat avec les dernières données connues (vides au 1er appel)

        lifecycleScope.launch {
            val tagsDeferred = async(Dispatchers.IO) {
                ApiClient.fetchTags(Prefs.serverUrl(this@LinksActivity), Prefs.apiKey(this@LinksActivity))
            }
            val groupsDeferred = async(Dispatchers.IO) {
                ApiClient.fetchGroups(Prefs.serverUrl(this@LinksActivity), Prefs.apiKey(this@LinksActivity))
            }
            lastTags = tagsDeferred.await()
            lastGroups = groupsDeferred.await()
            renderDrawer()
        }
    }

    /** Reconstruit le contenu du drawer à partir de lastGroups/lastTags (sans refetch réseau). */
    private fun renderDrawer() {
        drawerNav.removeAllViews()
        selectedNavView = null  // les vues précédentes sont détachées, reset la référence

        addNavItem(
            label = "Tous les liens",
            isSelected = currentTag == null && currentGroupId == null,
        ) {
            clearFilter()
        }

        addDivider()

        addNavItem(label = "⚙ Paramètres") {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, MainActivity::class.java))
        }

        addDivider()

        if (lastGroups.isNotEmpty()) {
            addSection("Groupes")
            renderGroups()
        }

        if (lastTags.isNotEmpty()) {
            addSection("Tags")
            for (tag in lastTags) {
                addNavItem(
                    label = "# ${tag.name}",
                    count = tag.count,
                    isSelected = currentTag == tag.name,
                ) {
                    setFilter(tag = tag.name)
                }
            }
        }

        addVersionFooter()
    }

    private fun renderGroups() {
        val collapsed = Prefs.collapsedFolders(this)
        val byId = lastGroups.associateBy { it.id }
        val parentIds = lastGroups.mapNotNull { it.parentId }.toSet()  // dossiers ayant des enfants

        for (group in lastGroups) {
            if (isHiddenByCollapse(group, byId, collapsed)) continue
            val isParent = group.id in parentIds
            val chevron = when {
                !isParent -> null
                group.id in collapsed -> "▸"
                else -> "▾"
            }
            val indent = group.depth * 16
            val prefix = if (group.depth > 0) "↳ " else "📁 "
            addNavItem(
                label = "$prefix${group.name}",
                count = group.count,
                paddingStart = 16 + indent,
                isSelected = currentGroupId == group.id,
                chevron = chevron,
                onChevronClick = if (isParent) {
                    {
                        val c = Prefs.collapsedFolders(this)
                        if (!c.add(group.id)) c.remove(group.id)
                        Prefs.saveCollapsedFolders(this, c)
                        renderDrawer()
                    }
                } else null,
            ) {
                setFilter(groupId = group.id, groupName = group.name)
            }
        }
    }

    /** Un dossier est masqué si l'un de ses ancêtres est replié. */
    private fun isHiddenByCollapse(
        group: ApiClient.GroupItem,
        byId: Map<Int, ApiClient.GroupItem>,
        collapsed: Set<Int>,
    ): Boolean {
        var pid = group.parentId
        while (pid != null) {
            if (pid in collapsed) return true
            pid = byId[pid]?.parentId
        }
        return false
    }

    private fun addNavItem(
        label: String,
        count: Int = 0,
        paddingStart: Int = 16,
        isSelected: Boolean = false,
        chevron: String? = null,
        onChevronClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_drawer_nav, drawerNav, false)
        val tvLabel = view.findViewById<TextView>(R.id.tvLabel)
        val tvCount = view.findViewById<TextView>(R.id.tvCount)
        val tvChevron = view.findViewById<TextView>(R.id.tvChevron)

        if (chevron != null) {
            tvChevron.text = chevron
            tvChevron.visibility = View.VISIBLE
            // Clic sur le chevron = plier/déplier (consomme l'événement, ne filtre pas).
            onChevronClick?.let { cb -> tvChevron.setOnClickListener { cb() } }
        } else {
            tvChevron.visibility = View.GONE
        }

        tvLabel.text = label
        val startPx = (paddingStart * resources.displayMetrics.density).toInt()
        view.setPadding(startPx, view.paddingTop, view.paddingRight, view.paddingBottom)

        if (count > 0) {
            tvCount.text = count.toString()
            tvCount.visibility = View.VISIBLE
        }

        if (isSelected) markSelected(view, tvLabel)

        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        view.setBackgroundResource(ripple.resourceId)

        view.setOnClickListener {
            selectedNavView?.let { prev ->
                prev.setBackgroundResource(ripple.resourceId)
                prev.findViewById<TextView>(R.id.tvLabel)?.let { tv ->
                    tv.setTypeface(null, Typeface.NORMAL)
                    tv.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                }
            }
            markSelected(view, tvLabel)
            drawerLayout.closeDrawer(GravityCompat.START)
            onClick()
        }

        drawerNav.addView(view)
        return view
    }

    private fun markSelected(view: View, tvLabel: TextView) {
        val bg = resolveColor(com.google.android.material.R.attr.colorPrimaryContainer)
        view.setBackgroundColor(bg)
        tvLabel.setTypeface(null, Typeface.BOLD)
        tvLabel.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
        selectedNavView = view
    }

    private fun addSection(title: String) {
        val tv = TextView(this).apply {
            text = title.uppercase()
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (20 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt(),
            )
            letterSpacing = 0.1f
        }
        drawerNav.addView(tv)
    }

    /** Pied du drawer : version + commit, cliquable pour vérifier les mises à jour. */
    private fun addVersionFooter() {
        addDivider()
        val tv = TextView(this).apply {
            text = "Excerpta v${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_COMMIT}"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val h = (16 * resources.displayMetrics.density).toInt()
            val v = (12 * resources.displayMetrics.density).toInt()
            setPadding(h, v, h, v)
            isClickable = true
            setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                checkForUpdate(recyclerView, verbose = true)
            }
        }
        drawerNav.addView(tv)
    }

    private fun addDivider() {
        val v = View(this).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            val m = (8 * resources.displayMetrics.density).toInt()
            lp.setMargins(0, m, 0, m)
            layoutParams = lp
            setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorOutline))
        }
        drawerNav.addView(v)
    }

    private fun resolveColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val c = ta.getColor(0, Color.GRAY)
        ta.recycle()
        return c
    }

    // ── Filtres ─────────────────────────────────────────────────────────────

    private fun clearFilter() {
        currentTag = null
        currentGroupId = null
        currentGroupName = null
        updateFilterChip()
        resetAndLoad()
    }

    private fun setFilter(tag: String? = null, groupId: Int? = null, groupName: String? = null) {
        currentTag = tag
        currentGroupId = groupId
        currentGroupName = groupName
        updateFilterChip()
        resetAndLoad()
    }

    private fun updateFilterChip() {
        chipGroup.removeAllViews()
        val label = when {
            currentTag != null -> "#$currentTag"
            currentGroupName != null -> "📁 $currentGroupName"
            else -> null
        }
        if (label != null) {
            val chip = Chip(this).apply {
                text = label
                isCloseIconVisible = true
                isClickable = true
                setOnCloseIconClickListener { clearFilter() }
                setOnClickListener { clearFilter() }
            }
            chipGroup.addView(chip)
            chipScrollView.visibility = View.VISIBLE
        } else {
            chipScrollView.visibility = View.GONE
        }
    }

    // ── Chargement ──────────────────────────────────────────────────────────

    private fun updateEmpty() {
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
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
                tag = currentTag ?: "",
                groupId = currentGroupId,
            )
            progressView.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            isLoading = false

            if (result == null) {
                if (page == 1 && !append) {
                    val cached = loadCache()
                    if (cached != null) {
                        adapter.submitList(cached)
                        emptyView.visibility = if (cached.isEmpty()) View.VISIBLE else View.GONE
                        Snackbar.make(recyclerView, getString(R.string.offline_notice), Snackbar.LENGTH_INDEFINITE).show()
                        return@launch
                    }
                } else {
                    Snackbar.make(recyclerView, getString(R.string.load_error), Snackbar.LENGTH_SHORT).show()
                }
                emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
                return@launch
            }

            currentPage = result.page
            totalPages = result.totalPages
            val newList = if (append) (adapter.currentList + result.links) else result.links
            adapter.submitList(newList)
            emptyView.visibility = if (newList.isEmpty()) View.VISIBLE else View.GONE

            if (page == 1 && !append && currentTag == null && currentGroupId == null && currentQuery.isBlank()) {
                saveCache(newList)
            }
        }
    }

    private fun saveCache(links: List<ApiClient.LinkItem>) {
        try {
            val arr = org.json.JSONArray()
            links.forEach { item ->
                arr.put(org.json.JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("title", item.title)
                    put("description", item.description)
                    put("favicon_url", item.faviconUrl)
                    put("thumbnail_url", item.thumbnailUrl)
                    put("is_public", item.isPublic)
                    put("created_at", item.createdAt)
                    val tagsArr = org.json.JSONArray()
                    item.tags.forEach { tagsArr.put(it) }
                    put("tags", tagsArr)
                })
            }
            cacheFile.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun loadCache(): List<ApiClient.LinkItem>? {
        return try {
            if (!cacheFile.exists()) return null
            val arr = org.json.JSONArray(cacheFile.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val tagsArr = o.getJSONArray("tags")
                ApiClient.LinkItem(
                    id = o.getInt("id"),
                    url = o.getString("url"),
                    title = o.optString("title", ""),
                    description = o.optString("description", ""),
                    faviconUrl = o.optString("favicon_url", ""),
                    thumbnailUrl = o.optString("thumbnail_url", ""),
                    isPublic = o.optBoolean("is_public", false),
                    createdAt = o.optString("created_at", ""),
                    tags = List(tagsArr.length()) { tagsArr.getString(it) },
                )
            }
        } catch (_: Exception) { null }
    }

    // ── Menu contextuel (appui long) ────────────────────────────────────────

    private fun showLinkMenu(item: ApiClient.LinkItem) {
        val visibilityLabel = if (item.isPublic) getString(R.string.menu_make_private) else getString(R.string.menu_make_public)

        // Liste d'actions construite dynamiquement : libellé + handler.
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += getString(R.string.menu_open) to {
            val uri = android.net.Uri.parse(item.url)
            if (uri.scheme in listOf("http", "https")) {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
        if (item.hasReader) {
            actions += "Vue lecteur" to { openReader(item) }
        }
        if (item.archivedUrl != null) {
            actions += "Voir l'archive" to {
                val uri = android.net.Uri.parse(item.archivedUrl)
                if (uri.scheme in listOf("http", "https")) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
        actions += getString(R.string.menu_copy_url) to {
            val clip = ClipData.newPlainText("url", item.url)
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            Snackbar.make(recyclerView, "URL copiée", Snackbar.LENGTH_SHORT).show()
        }
        actions += visibilityLabel to { togglePublic(item) }
        actions += getString(R.string.menu_delete) to { confirmDelete(item) }

        MaterialAlertDialogBuilder(this)
            .setTitle(item.title.ifBlank { item.url })
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions[which].second()
            }
            .show()
    }

    private fun openReader(item: ApiClient.LinkItem) {
        startActivity(
            Intent(this, ReaderActivity::class.java).apply {
                putExtra(ReaderActivity.EXTRA_LINK_ID, item.id)
                putExtra(ReaderActivity.EXTRA_FALLBACK_TITLE, item.title.ifBlank { item.url })
            }
        )
    }

    private fun togglePublic(item: ApiClient.LinkItem) {
        val newPublic = !item.isPublic
        lifecycleScope.launch {
            val result = ApiClient.patchLink(
                Prefs.serverUrl(this@LinksActivity),
                Prefs.apiKey(this@LinksActivity),
                item.id,
                newPublic,
            )
            Snackbar.make(recyclerView, result.message, Snackbar.LENGTH_SHORT).show()
            if (result.success) {
                val updated = adapter.currentList.map { if (it.id == item.id) it.copy(isPublic = newPublic) else it }
                adapter.submitList(updated)
            }
        }
    }

    private fun confirmDelete(item: ApiClient.LinkItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Supprimer ce lien ?")
            .setMessage(item.title.ifBlank { item.url })
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                lifecycleScope.launch {
                    val result = ApiClient.deleteLink(
                        Prefs.serverUrl(this@LinksActivity),
                        Prefs.apiKey(this@LinksActivity),
                        item.id,
                    )
                    if (result.success) {
                        val updated = adapter.currentList.filter { it.id != item.id }
                        adapter.submitList(updated)
                        emptyView.visibility = if (updated.isEmpty()) View.VISIBLE else View.GONE
                        Snackbar.make(recyclerView, "Lien supprimé", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(recyclerView, result.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    // ── Mise à jour ──────────────────────────────────────────────────────────

    private fun checkForUpdate(anchor: View, verbose: Boolean = false) {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
            when {
                info != null && info.hasUpdate -> {
                    Snackbar.make(anchor, "Mise à jour disponible (${info.remoteCommit})", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Installer") { UpdateChecker.openDownload(this@LinksActivity) }
                        .show()
                }
                verbose && info != null -> {
                    Snackbar.make(anchor, "À jour (v${BuildConfig.VERSION_NAME})", Snackbar.LENGTH_SHORT).show()
                }
                verbose -> {
                    Snackbar.make(anchor, "Vérification impossible (hors ligne ?)", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
}
