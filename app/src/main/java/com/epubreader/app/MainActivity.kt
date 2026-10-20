package com.epubreader.app

import com.epubreader.app.util.SystemBarController

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.data.BookEntity
import com.bumptech.glide.Glide
import com.epubreader.app.data.BookRepository
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.databinding.ActivityMainBinding
import com.epubreader.app.epub.BookFileTypes
import com.epubreader.app.epub.EpubImporter
import com.epubreader.app.epub.MetadataRefreshReportStore
import com.epubreader.app.epub.RescanDecision
import com.epubreader.app.ui.BookAdapter
import com.epubreader.app.ui.BookshelfViewModel
import com.epubreader.app.ui.DisplayItem
import com.epubreader.app.ui.DrawerAdapter
import com.epubreader.app.ui.DrawerItem
import com.epubreader.app.ui.HomeBookAdapter
import com.epubreader.app.ui.HomeContent
import com.epubreader.app.ui.HomeGroup
import com.epubreader.app.ui.RowAdapter
import com.epubreader.app.util.CurrentlyReadingUndoSnackbar
import com.epubreader.app.ui.ShelfView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val STATE_SHELF = "state_shelf"
private const val STATE_DETAIL_NAME = "state_detail_name"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var importer: EpubImporter
    private lateinit var drawerAdapter: DrawerAdapter
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var backPressedOnce = false
    private val backResetHandler = Handler(Looper.getMainLooper())

    // Patch 12: the folder scan runs as a tracked background job so it survives
    // in-app navigation (the user can leave the Folders screen while a scan is
    // running and it keeps going). The reference is also used to guard against
    // launching a duplicate concurrent scan when the user taps Scan Now again.
    private var scanJob: Job? = null

    private val viewModel: BookshelfViewModel by viewModels {
        BookshelfViewModel.Factory(BookRepository(applicationContext), PrefsManager(applicationContext))
    }

    private var bookAdapter: BookAdapter? = null
    private var rowAdapter: RowAdapter? = null
    private var currentBooks: List<BookEntity> = emptyList()
    private var touchHelper: ItemTouchHelper? = null
    private var scrollToTopOnNextContent = false

    // ---------------------------------------------------------------- scroll restoration
    // Patch 11: the bookshelf must remember the exact scroll position of the
    // Library / Authors list / Series list / Author-detail books / Series-detail
    // books layout when the user opens a book (or book details) and then comes
    // back via the phone back button or the toolbar back arrow. The remembered
    // position is restored so the clicked title (P / Z / whatever was on screen)
    // is still visible on return.
    //
    // Only the *return-from-reader/detail* path restores scroll. Switching
    // between drawer sections (and sort / view-mode changes) still do a clean
    // top reset exactly like Patch 10 — those paths set scrollToTopOnNextContent
    // which the restore path never touches. The two behaviours are mutually
    // exclusive by design: a return from the reader does NOT set the top-reset
    // flag, so the normal adapter submit path runs and the restore is applied
    // inside that path's commit callback (after the adapter has items).
    //
    // Currently Reading is restored when returning from Book Details, so the
    // bookshelf stays at the user's last scroll location. It remains excluded
    // from the reader-return top-reset path: opening/reading a book from
    // Currently Reading keeps its existing clean top-reset behaviour.
    //
    // Per-view saved scroll anchor. We store BOTH the first-visible row (for
    // exact same-layout restoration when the list order hasn't changed) and the
    // clicked book's id + position + top offset (for re-location when the list
    // re-sorted after reading progress changed). On restore we only relocate by
    // book id if the clicked book's current adapter index differs from its saved
    // index — otherwise we restore the first-visible position + offset exactly.
    private data class ScrollAnchor(
        val firstVisiblePosition: Int,
        val firstVisibleOffset: Int,
        val clickedBookId: Long?,
        val clickedBookPosition: Int,
        val clickedBookTopOffset: Int,
    )

    private val savedScroll = mutableMapOf<String, ScrollAnchor>()
    private var pendingRestoreKey: String? = null

    // Patch 16 (Issue #3): the "Recently Added" temp screen is transient — it is
    // only reachable via the "Show" action on the scan-complete snackbar and only
    // until the user presses back. This holds the shelf the user was viewing
    // before tapping Show, so back / the toolbar back arrow returns there exactly
    // instead of dumping them on the Library. Null when not on the temp screen.
    private var viewBeforeRecentlyAdded: ShelfView? = null
    private var pendingRestoreBookId: Long? = null

    // Patch 11: When opening a book FROM Currently Reading, keep the existing
    // clean top-reset-on-return behaviour. Room may not re-emit in time (or at
    // all) on resume, so this flag forces a scroll-to-top in onResume as a
    // guaranteed fallback when returning from the reader specifically.
    // Book Details uses the normal saved-scroll restoration path instead.
    private var pendingReadingTopReset = false

    private val homeAdapters = mutableMapOf<Int, HomeBookAdapter>()

    /** Cached Home content so the Home screen can be re-rendered instantly when
     *  the user navigates back to it, even if the LiveData already emitted while
     *  a different view was on screen (e.g. the user opened a book from Library
     *  and the DB updated Continue Reading while Home was hidden). */
    private var lastHomeContent: HomeContent? = null

    // A list of shelf views that should be restored on return from a sub-activity.
    // Currently Reading is included for the Book Details return path.
    private fun isRestoreEligible(view: ShelfView): Boolean =
        view is ShelfView.Reading ||
                view is ShelfView.Library ||
                view is ShelfView.AuthorsList ||
                view is ShelfView.SeriesList ||
                view is ShelfView.AuthorDetail ||
                view is ShelfView.SeriesDetail

    private fun viewKey(view: ShelfView): String = when (view) {
        is ShelfView.AuthorDetail -> "author_detail:${view.name}"
        is ShelfView.SeriesDetail -> "series_detail:${view.name}"
        else -> view::class.simpleName ?: "unknown"
    }

    /** Captures the current first-visible row + its pixel offset (and, for book
     *  lists, the clicked book's id + adapter position + top offset) so the exact
     *  same layout can be restored later. If the list re-sorts after reading
     *  progress changes, the clicked title is re-located by id and aligned by
     *  its saved top offset — but only if its index actually moved; otherwise the
     *  exact first-visible position + offset is restored unchanged. */
    private fun captureScrollState(clickedBookId: Long? = null) {
        val view = viewModel.view.value ?: return
        if (!isRestoreEligible(view)) return
        val lm = recycler?.layoutManager as? LinearLayoutManager ?: return
        val adapter = recycler?.adapter ?: return
        if (adapter.itemCount == 0) return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos == RecyclerView.NO_POSITION) return
        val child = lm.findViewByPosition(pos)
        val offset = child?.top ?: 0
        // For book lists, if we know which book was tapped, also capture its own
        // adapter position + top offset so we can re-align it by id if the list
        // reorders. For row lists (authors/series) there is no tapped book.
        val clickedPos = if (clickedBookId != null && adapter is BookAdapter) {
            adapter.currentList.indexOfFirst { it.id == clickedBookId }
        } else {
            -1
        }
        val clickedOffset = if (clickedPos >= 0) {
            lm.findViewByPosition(clickedPos)?.top ?: offset
        } else {
            0
        }
        savedScroll[viewKey(view)] =
            ScrollAnchor(pos, offset, clickedBookId, clickedPos, clickedOffset)
    }

    private fun tryRestoreScroll(): Boolean {
        val view = viewModel.view.value ?: return false
        if (!isRestoreEligible(view)) return false
        // Key guard: only restore if the CURRENT view is the one we queued a
        // restore for. Without this a parent-list restore could fire while a
        // detail list (or a different author/series) is on screen, or vice
        // versa, producing a wrong jump. If it doesn't match yet, keep waiting
        // (don't clear the pending key) — a later emission for the right view
        // will consume it.
        val key = pendingRestoreKey ?: return false
        if (viewKey(view) != key) return false
        val lm = recycler?.layoutManager as? LinearLayoutManager ?: return false
        val adapter = recycler?.adapter ?: return false
        if (adapter.itemCount == 0) return false
        val anchor = savedScroll[key] ?: run { pendingRestoreKey = null; return false }
        // Restore strategy: if the clicked book is still at the SAME adapter
        // index it had when we navigated away (i.e. the list didn't reorder),
        // restore the exact first-visible position + offset — the same rows
        // that were on screen before. Only when the clicked book MOVED (sort by
        // progress / last-read changed its position) do we re-locate it by id
        // and align it to its saved top offset.
        val pos: Int
        val offset: Int
        if (adapter is BookAdapter && anchor.clickedBookId != null && anchor.clickedBookPosition >= 0) {
            val currentClickedIdx = adapter.currentList.indexOfFirst { it.id == anchor.clickedBookId }
            if (currentClickedIdx >= 0 && currentClickedIdx != anchor.clickedBookPosition) {
                // Book moved: re-locate by id, align to its saved top offset.
                pos = currentClickedIdx
                offset = anchor.clickedBookTopOffset
            } else {
                // Unchanged (or book not found): restore exact first-visible.
                pos = anchor.firstVisiblePosition.coerceIn(0, adapter.itemCount - 1)
                offset = anchor.firstVisibleOffset
            }
        } else {
            pos = anchor.firstVisiblePosition.coerceIn(0, adapter.itemCount - 1)
            offset = anchor.firstVisibleOffset
        }
        lm.scrollToPositionWithOffset(pos, offset)
        pendingRestoreKey = null
        return true
    }

    private val openTreeLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri -> uri?.let { scanFolder(it, fromRefresh = false) } }

    private val openMultiFileLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris -> importMultiple(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PrefsManager(applicationContext)
        importer = EpubImporter(applicationContext)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)

        restoreViewFromSavedState(savedInstanceState)
        setupDrawer()
        setupRecycler()
        setupObservers()
        binding.homeContent.homeEmptyAction.setOnClickListener {
            openMultiFileLauncher.launch(BookFileTypes.acceptedMimeTypes)
        }

        // Patch 18 (Addition #3): if launched by the system "Open with" for an
        // .epub, import it and jump straight into the reader.
        handleViewIntent(intent)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        binding.drawerRoot.isOpen -> binding.drawerRoot.close()
                        viewModel.view.value is ShelfView.RecentlyAdded -> {
                            exitRecentlyAdded()
                        }

                        viewModel.isDetailOpen() -> {
                            returnToParentList()
                        }

                        else -> {
                            if (backPressedOnce) {
                                backResetHandler.removeCallbacksAndMessages(null)
                                finish()
                                return
                            }
                            backPressedOnce = true
                            Snackbar.make(binding.root, R.string.back_again_to_exit, Snackbar.LENGTH_SHORT)
                                .addCallback(object : Snackbar.Callback() {
                                    override fun onDismissed(s: Snackbar?, event: Int) {
                                        backPressedOnce = false
                                    }
                                })
                                .show()
                        }
                    }
                }
            },
        )
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::drawerToggle.isInitialized) drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val current = viewModel.view.value ?: ShelfView.Home
        if (current !is ShelfView.RecentlyAdded) {
            outState.putString(STATE_SHELF, current.keyForSavedState())
            when (current) {
                is ShelfView.AuthorDetail -> outState.putString(STATE_DETAIL_NAME, current.name)
                is ShelfView.SeriesDetail -> outState.putString(STATE_DETAIL_NAME, current.name)
                else -> Unit
            }
        }
    }

    private fun restoreViewFromSavedState(savedInstanceState: Bundle?) {
        val key = savedInstanceState?.getString(STATE_SHELF) ?: return
        val detailName = savedInstanceState.getString(STATE_DETAIL_NAME)
        val restored = when (key) {
            BookshelfViewModel.KEY_HOME -> ShelfView.Home
            BookshelfViewModel.KEY_READING -> ShelfView.Reading
            BookshelfViewModel.KEY_LIBRARY -> ShelfView.Library
            BookshelfViewModel.KEY_FAVORITES -> ShelfView.Favorites
            BookshelfViewModel.KEY_AUTHORS -> ShelfView.AuthorsList
            BookshelfViewModel.KEY_SERIES -> ShelfView.SeriesList
            BookshelfViewModel.KEY_FINISHED -> ShelfView.Finished
            BookshelfViewModel.KEY_TBR -> ShelfView.ToBeRead
            BookshelfViewModel.KEY_COLLECTIONS -> ShelfView.Collections
            BookshelfViewModel.KEY_FOLDERS -> ShelfView.Folders
            BookshelfViewModel.KEY_SETTINGS -> ShelfView.Settings
            "author_detail" -> detailName?.let { ShelfView.AuthorDetail(it) }
            "series_detail" -> detailName?.let { ShelfView.SeriesDetail(it) }
            else -> null
        }
        restored?.let { viewModel.setView(it) }
    }

    private fun ShelfView.keyForSavedState(): String = when (this) {
        is ShelfView.Home -> BookshelfViewModel.KEY_HOME
        is ShelfView.Reading -> BookshelfViewModel.KEY_READING
        is ShelfView.Library -> BookshelfViewModel.KEY_LIBRARY
        is ShelfView.Favorites -> BookshelfViewModel.KEY_FAVORITES
        is ShelfView.AuthorsList -> BookshelfViewModel.KEY_AUTHORS
        is ShelfView.SeriesList -> BookshelfViewModel.KEY_SERIES
        is ShelfView.Finished -> BookshelfViewModel.KEY_FINISHED
        is ShelfView.ToBeRead -> BookshelfViewModel.KEY_TBR
        is ShelfView.Collections -> BookshelfViewModel.KEY_COLLECTIONS
        is ShelfView.Folders -> BookshelfViewModel.KEY_FOLDERS
        is ShelfView.Settings -> BookshelfViewModel.KEY_SETTINGS
        is ShelfView.AuthorDetail -> "author_detail"
        is ShelfView.SeriesDetail -> "series_detail"
        else -> BookshelfViewModel.KEY_HOME
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Patch 18 (Addition #3): re-handle a VIEW intent delivered to the
        // already-running (singleTop) instance.
        handleViewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (pendingReadingTopReset) {
            pendingReadingTopReset = false
            recycler?.post {
                (recycler?.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(0, 0)
                recycler?.scrollToPosition(0)
            }
        }
        // Returning from the reader / book-details activity lands here. If a
        // restore was queued and the list content has already re-emitted before
        // this point, try once more now (harmless if there is nothing to restore
        // or the adapter is still empty — tryRestoreScroll guards both).
        if (pendingRestoreKey != null) {
            recycler?.post { tryRestoreScroll() }
        }
        binding.root.post {
            CurrentlyReadingUndoSnackbar.showPending(this, binding.root)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsPronouncer?.shutdown()
        ttsPronouncer = null
    }

    // ---------------------------------------------------------------- drawer
    private fun toggleDrawer() {
        if (binding.drawerRoot.isOpen) binding.drawerRoot.close() else binding.drawerRoot.open()
    }

    private fun setupDrawer() {
        // Patch 16 (Addition #1): drawer widened from 3/5 (60%) to 70% of
        // screen width.
        val w = resources.displayMetrics.widthPixels
        binding.drawerPane.layoutParams =
            (binding.drawerPane.layoutParams as androidx.drawerlayout.widget.DrawerLayout.LayoutParams)
                .apply { width = (w * 70 / 100).coerceAtLeast(280) }

        drawerAdapter = DrawerAdapter { item -> selectDrawer(item) }
        binding.drawerList.layoutManager = LinearLayoutManager(this)
        binding.drawerList.adapter = drawerAdapter
        submitDrawerItems()
    }

    private fun drawerItems(): List<DrawerItem> =
        listOf(
            DrawerItem(getString(R.string.nav_home), R.drawable.ic_home, view = ShelfView.Home),
            DrawerItem(getString(R.string.nav_currently_reading), R.drawable.ic_book, view = ShelfView.Reading),
            DrawerItem(getString(R.string.nav_library), R.drawable.ic_bookshelf, view = ShelfView.Library),
            DrawerItem(getString(R.string.nav_favorites), R.drawable.ic_favorite_border, view = ShelfView.Favorites),
            DrawerItem(getString(R.string.nav_authors), R.drawable.ic_person, view = ShelfView.AuthorsList),
            DrawerItem(getString(R.string.nav_series), R.drawable.ic_series, view = ShelfView.SeriesList),
            DrawerItem(
                getString(R.string.nav_collections),
                R.drawable.ic_collections,
                isPlaceholder = true,
                view = ShelfView.Collections
            ),
            DrawerItem(
                label = "",
                iconRes = 0,
                isDivider = true,
            ),
            DrawerItem(getString(R.string.nav_reading_stats), R.drawable.ic_stats, view = ShelfView.Stats),
            DrawerItem(getString(R.string.nav_vocabulary), R.drawable.ic_vocabulary, view = ShelfView.Vocabulary),
            DrawerItem(getString(R.string.nav_folders), R.drawable.ic_folder, view = ShelfView.Folders),
            DrawerItem(getString(R.string.nav_settings), R.drawable.ic_settings, view = ShelfView.Settings
            ),
        )

    private fun submitDrawerItems() {
        drawerAdapter.submitList(drawerItems())
        drawerAdapter.setSelected(viewModel.view.value)
    }

    private fun titleFor(view: ShelfView?): String =
        when (view) {
            is ShelfView.Home -> getString(R.string.app_name)
            is ShelfView.Reading -> getString(R.string.nav_currently_reading)
            is ShelfView.Library -> getString(R.string.nav_library)
            is ShelfView.Favorites -> getString(R.string.nav_favorites)
            is ShelfView.Finished -> getString(R.string.nav_finished)
            is ShelfView.ToBeRead -> getString(R.string.nav_tbr)
            is ShelfView.RecentlyAdded -> getString(R.string.recently_added_screen_title)
            is ShelfView.AuthorsList -> getString(R.string.nav_authors)
            is ShelfView.SeriesList -> getString(R.string.nav_series)
            is ShelfView.AuthorDetail -> view.name
            is ShelfView.SeriesDetail -> view.name
            is ShelfView.Collections -> getString(R.string.nav_collections)
            is ShelfView.Folders -> getString(R.string.nav_folders)
            is ShelfView.Settings -> getString(R.string.nav_settings)
            is ShelfView.Stats -> getString(R.string.nav_reading_stats)
            is ShelfView.Vocabulary -> getString(R.string.nav_vocabulary)
            null -> getString(R.string.app_name)
        }

    private fun selectDrawer(item: DrawerItem) {
        binding.drawerRoot.close()
        // If the drawer item launches a separate activity (e.g. Reading Stats),
        // start it directly without changing the current shelf view.
        item.launchActivity?.let {
            startActivity(Intent(this, it))
            return
        }
        item.view?.let {
            // If already on this view (e.g. Home), just scroll to top.
            if (viewModel.view.value == it && it is ShelfView.Home) {
                binding.homeContent.homeScroll.post {
                    binding.homeContent.homeScroll.scrollTo(0, 0)
                }
                return
            }
            scrollToTopOnNextContent = true
            pendingRestoreKey = null
            pendingReadingTopReset = false
            viewModel.setView(it)
        }
    }

    private fun setupHomeShelves() {
        val specs = listOf(
            R.id.homeRecentlyAdded to binding.homeContent.homeRecentlyAdded,
            R.id.homeFavorites to binding.homeContent.homeFavorites,
        )
        specs.forEach { (key, recycler) ->
            val adapter = HomeBookAdapter(::openBook, ::showBookOptions)
            recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            recycler.adapter = adapter
            homeAdapters[key] = adapter
        }
    }

    private fun renderHome(content: HomeContent) {
        lastHomeContent = content
        if (viewModel.view.value !is ShelfView.Home) return

        binding.homeContent.homeEmpty.visibility = if (content.hasBooks) View.GONE else View.VISIBLE
        binding.homeContent.homeRecentlyAddedSection.visibility = if (content.recentlyAdded.isEmpty()) View.GONE else View.VISIBLE
        val hasContinueReading = content.continueReading != null
        binding.homeContent.homeContinueSection.visibility = if (hasContinueReading) View.VISIBLE else View.GONE
        binding.homeContent.homeContinueCard.visibility = if (hasContinueReading) View.VISIBLE else View.GONE

        binding.homeContent.homeContinueViewAll.setOnClickListener { viewModel.setView(ShelfView.Reading) }
        binding.homeContent.homeRecentlyAddedViewAll.setOnClickListener { viewModel.setView(ShelfView.RecentlyAdded(content.recentlyAdded.map { it.id })) }
        binding.homeContent.homeFavoritesViewAll.setOnClickListener { viewModel.setView(ShelfView.Favorites) }
        binding.homeContent.homeAllAuthorsBtn.setOnClickListener { viewModel.setView(ShelfView.AuthorsList) }
        binding.homeContent.homeAllSeriesBtn.setOnClickListener { viewModel.setView(ShelfView.SeriesList) }

        content.continueReading?.let { book ->
            val dateStr = android.text.format.DateFormat.format("MMM dd, yyyy", book.lastOpenedDate ?: book.addedDate).toString()
            binding.homeContent.homeContinueDate.text = "Opened $dateStr"
            binding.homeContent.homeContinueTitle.text = book.title

            val authorStr = book.author.ifBlank { getString(R.string.unknown_author) }
            val seriesText = book.series
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { series ->
                    val index = book.seriesIndex
                    if (index != null) {
                        val formattedIndex = if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
                        " • $series #$formattedIndex"
                    } else {
                        " • $series"
                    }
                }.orEmpty()
            binding.homeContent.homeContinueAuthorSeries.text = "by $authorStr$seriesText"

            val location = book.currentLocation?.trim().orEmpty()
            binding.homeContent.homeContinueLocation.text = if (location.isBlank()) {
                ""
            } else {
                "Current section: \"$location\""
            }
            binding.homeContent.homeContinueLocation.visibility = if (location.isBlank()) View.GONE else View.VISIBLE

            val pct = (book.progress * 100).toInt()
            binding.homeContent.homeContinueProgress.text = if (book.progress >= 0.90f) {
                getString(R.string.progress_completed)
            } else {
                "$pct%"
            }
            binding.homeContent.homeContinueProgressBar.progress = if (book.progress >= 0.90f) 100 else pct

            loadHomeCover(book, binding.homeContent.homeContinueCover)
            binding.homeContent.homeContinueResumeBtn.setOnClickListener { openBook(book) }
            binding.homeContent.homeContinueDetailsBtn.setOnClickListener { openBookDetails(book) }
            binding.homeContent.homeContinueCard.setOnClickListener { openBook(book) }
        }

        submitHomeShelf(R.id.homeRecentlyAdded, content.recentlyAdded)
        binding.homeContent.homeFavoritesSection.visibility = if (content.favorites.isEmpty()) View.GONE else View.VISIBLE
        submitHomeShelf(R.id.homeFavorites, content.favorites)

        populateAuthorRows(content.topAuthors)
        populateSeriesRows(content.topSeries)
        binding.homeContent.homeAuthorsSection.visibility = if (content.topAuthors.isEmpty()) View.GONE else View.VISIBLE
        binding.homeContent.homeSeriesSection.visibility = if (content.topSeries.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun populateAuthorRows(authors: List<HomeGroup>) {
        val container = binding.homeContent.homeAuthorRowsContainer
        container.removeAllViews()
        authors.take(4).forEach { group ->
            val row = layoutInflater.inflate(R.layout.item_home_author_series, container, false) as android.view.ViewGroup
            val tvTitle = row.findViewById<android.widget.TextView>(R.id.title)
            val tvCount = row.findViewById<android.widget.TextView>(R.id.count)

            tvTitle.text = group.name
            tvCount.text = "${group.count} ${if (group.count == 1) "book" else "books"}"
            row.setOnClickListener { viewModel.setView(ShelfView.AuthorDetail(group.name)) }
            container.addView(row)
        }
    }

    private fun populateSeriesRows(seriesList: List<HomeGroup>) {
        val container = binding.homeContent.homeSeriesRowsContainer
        container.removeAllViews()
        seriesList.take(4).forEach { group ->
            val row = layoutInflater.inflate(R.layout.item_home_author_series, container, false) as android.view.ViewGroup
            val tvTitle = row.findViewById<android.widget.TextView>(R.id.title)
            val tvCount = row.findViewById<android.widget.TextView>(R.id.count)

            tvTitle.text = group.name
            tvCount.text = "${group.count} ${if (group.count == 1) "book" else "books"}"
            row.setOnClickListener { viewModel.setView(ShelfView.SeriesDetail(group.name)) }
            container.addView(row)
        }
    }

    private fun openBookDetails(book: BookEntity) {
        val intent = android.content.Intent(this, BookDetailsActivity::class.java).apply {
            putExtra(BookDetailsActivity.EXTRA_BOOK_ID, book.id)
        }
        startActivity(intent)
    }

    private fun submitHomeShelf(id: Int, books: List<BookEntity>) {
        homeAdapters[id]?.submitList(books)
    }

    private fun loadHomeCover(book: BookEntity, view: android.widget.ImageView) {
        if (book.coverPath != null) {
            Glide.with(view)
                .load(File(book.coverPath))
                .override(300, 450)
                .centerCrop()
                .placeholder(R.drawable.cover_frame)
                .into(view)
        } else {
            Glide.with(view).clear(view)
            view.setImageResource(R.drawable.cover_frame)
        }
    }

    private val recycler: RecyclerView?
        get() = binding.root.findViewById(R.id.recycler)

    private val refresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        get() = binding.root.findViewById(R.id.refresh) ?: binding.root.findViewById(android.R.id.content)

    // ---------------------------------------------------------------- recycler
    private fun setupRecycler() {
        refresh.setOnRefreshListener { rescanSelectedFolder() }
        setupHomeShelves()
    }

    private fun isBookView(view: ShelfView) =
        view !is ShelfView.Home &&
                view !is ShelfView.AuthorsList &&
                view !is ShelfView.SeriesList &&
                view !is ShelfView.Collections &&
                view !is ShelfView.Folders &&
                view !is ShelfView.Settings &&
                view !is ShelfView.Stats &&
                view !is ShelfView.Vocabulary

    private fun isPlaceholder(view: ShelfView) = view is ShelfView.Collections

    private fun isFoldersView(view: ShelfView) = view is ShelfView.Folders

    /** Returns from an Author/Series detail (books layout) back to its parent
     *  list (Authors or Series). Patch 11: the parent list's last scroll
     *  position is restored — NOT top-reset — so the author/series row the user
     *  originally tapped is still on screen. The toolbar icon flips back to the
     *  hamburger menu (handled by applyView via isDrawerIndicatorEnabled). */
    private fun returnToParentList() {
        val parent = when (viewModel.view.value) {
            is ShelfView.AuthorDetail -> ShelfView.AuthorsList
            is ShelfView.SeriesDetail -> ShelfView.SeriesList
            else -> return
        }
        pendingRestoreKey = viewKey(parent)
        viewModel.clearDetail()
    }

    // Patch 16 (Issue #3): leave the transient "Recently Added" temp screen and
    // return to whatever shelf the user was viewing before they tapped Show on
    // the scan-complete snackbar. Falls back to Library if state was lost (e.g.
    // process death). Crucially this does NOT go through setView()-persistence
    // for the RecentlyAdded view itself (see ViewModel.setView) and restores the
    // real underlying shelf, so lastView stays correct for the next launch.
    private fun exitRecentlyAdded() {
        val target = viewBeforeRecentlyAdded ?: ShelfView.Library
        viewBeforeRecentlyAdded = null
        // Returning to the previous shelf should land at the top of that list
        // (title-ascending), matching the clean-refresh-on-nav-switch contract
        // used by selectDrawer().
        scrollToTopOnNextContent = true
        viewModel.setView(target)
    }

    private fun applyView(view: ShelfView) {
        val isStatsOrVocab = view is ShelfView.Stats || view is ShelfView.Vocabulary
        val headerLayoutRes = when {
            isStatsOrVocab || isFoldersView(view) || view is ShelfView.Settings -> R.layout.header_category_1
            view is ShelfView.Home || view is ShelfView.Reading || view is ShelfView.AuthorsList || view is ShelfView.SeriesList || view is ShelfView.Collections -> R.layout.header_category_2
            else -> R.layout.header_category_3
        }

        val headerContainer = binding.root.findViewById<android.widget.FrameLayout>(R.id.headerContainer)
        val currentHeaderRes = headerContainer?.getTag(R.id.headerContainer) as? Int
        val isDetail = view is ShelfView.AuthorDetail || view is ShelfView.SeriesDetail
        val isRecentlyAdded = view is ShelfView.RecentlyAdded

        if (currentHeaderRes != headerLayoutRes) {
            headerContainer?.removeAllViews()
            val inflatedHeader = layoutInflater.inflate(headerLayoutRes, headerContainer, true)
            headerContainer?.setTag(R.id.headerContainer, headerLayoutRes)

            val activeToolbar = inflatedHeader.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            if (activeToolbar != null) {
                setSupportActionBar(activeToolbar)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.setHomeButtonEnabled(true)
            }

            // Wire header buttons
            inflatedHeader.findViewById<View>(R.id.addImportBtn)?.setOnClickListener {
                openMultiFileLauncher.launch(BookFileTypes.acceptedMimeTypes)
            }
            inflatedHeader.findViewById<View>(R.id.headerBtnSearch)?.setOnClickListener {
                launchSearch()
            }
            inflatedHeader.findViewById<View>(R.id.libBtnSearch)?.setOnClickListener {
                launchSearch()
            }
            inflatedHeader.findViewById<View>(R.id.libBtnViewMode)?.setOnClickListener {
                showViewModeDialog()
            }
            inflatedHeader.findViewById<View>(R.id.libBtnSort)?.setOnClickListener {
                showSortDialog()
            }
        }

        val activeToolbar = headerContainer?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        if (activeToolbar != null) {
            if (isDetail || isRecentlyAdded) {
                activeToolbar.setNavigationIcon(R.drawable.ic_arrow_back)
                activeToolbar.setNavigationOnClickListener {
                    if (isRecentlyAdded) exitRecentlyAdded() else returnToParentList()
                }
            } else {
                activeToolbar.setNavigationIcon(R.drawable.ic_menu)
                activeToolbar.setNavigationOnClickListener {
                    if (binding.drawerRoot.isOpen) binding.drawerRoot.close() else binding.drawerRoot.open()
                }
            }
            activeToolbar.title = titleFor(view)
        }

        drawerAdapter.setSelected(view)
        updateHeaderViewModeIcon()

        val isHome = view is ShelfView.Home
        val isSettings = view is ShelfView.Settings
        val isFolders = isFoldersView(view)
        val isStats = view is ShelfView.Stats
        val isVocab = view is ShelfView.Vocabulary
        val showShelf = !isHome && !isSettings && !isFolders && !isStats && !isVocab

        val homeRoot = binding.root.findViewById<View>(R.id.homeContent)
        val settingsRoot = binding.root.findViewById<View>(R.id.mainSettingsContent)
        val foldersRoot = binding.root.findViewById<View>(R.id.mainFoldersContent)
        val statsRoot = binding.root.findViewById<View>(R.id.mainStatsContent)
        val vocabRoot = binding.root.findViewById<View>(R.id.mainVocabularyContent)
        val shelfContainer = binding.root.findViewById<android.widget.FrameLayout>(R.id.shelfContainer)

        homeRoot?.visibility = if (isHome) View.VISIBLE else View.GONE
        settingsRoot?.visibility = if (isSettings) View.VISIBLE else View.GONE
        foldersRoot?.visibility = if (isFolders) View.VISIBLE else View.GONE
        statsRoot?.visibility = if (isStats) View.VISIBLE else View.GONE
        vocabRoot?.visibility = if (isVocab) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (isPlaceholder(view)) View.VISIBLE else View.GONE

        if (showShelf) {
            shelfContainer?.visibility = View.VISIBLE
            val shelfLayoutRes = when (view) {
                is ShelfView.Reading -> R.layout.view_currently_reading
                is ShelfView.Favorites -> R.layout.view_favorites
                is ShelfView.AuthorsList, is ShelfView.AuthorDetail -> R.layout.view_authors
                is ShelfView.SeriesList, is ShelfView.SeriesDetail -> R.layout.view_series
                is ShelfView.Collections -> R.layout.view_collections
                else -> R.layout.view_library
            }
            val currentShelfRes = shelfContainer?.getTag(R.id.shelfContainer) as? Int
            if (currentShelfRes != shelfLayoutRes) {
                shelfContainer?.removeAllViews()
                layoutInflater.inflate(shelfLayoutRes, shelfContainer, true)
                shelfContainer?.setTag(R.id.shelfContainer, shelfLayoutRes)
            }

            val tvHeaderTitle = shelfContainer?.findViewById<TextView>(R.id.tvHeaderTitle)
            val tvHeaderSubtitle = shelfContainer?.findViewById<TextView>(R.id.tvHeaderSubtitle)
            val ivHeaderIcon = shelfContainer?.findViewById<android.widget.ImageView>(R.id.ivHeaderIcon)

            when (view) {
                is ShelfView.AuthorDetail -> {
                    tvHeaderTitle?.text = "AUTHOR: ${view.name.uppercase()}"
                    tvHeaderSubtitle?.text = ""
                    ivHeaderIcon?.visibility = View.VISIBLE
                    ivHeaderIcon?.setImageResource(R.drawable.ic_person_frame)
                }
                is ShelfView.SeriesDetail -> {
                    tvHeaderTitle?.text = "SERIES: ${view.name.uppercase()}"
                    tvHeaderSubtitle?.text = ""
                    ivHeaderIcon?.visibility = View.VISIBLE
                    ivHeaderIcon?.setImageResource(R.drawable.ic_series)
                }
                is ShelfView.AuthorsList -> {
                    tvHeaderTitle?.text = getString(R.string.authors_heading_title)
                    tvHeaderSubtitle?.text = getString(R.string.authors_heading_subtitle)
                    ivHeaderIcon?.visibility = View.VISIBLE
                    ivHeaderIcon?.setImageResource(R.drawable.ic_person)
                    shelfContainer?.findViewById<TextView>(R.id.headerCountText)?.visibility = View.VISIBLE
                }
                is ShelfView.SeriesList -> {
                    tvHeaderTitle?.text = getString(R.string.series_heading_title)
                    tvHeaderSubtitle?.text = getString(R.string.series_heading_subtitle)
                    ivHeaderIcon?.visibility = View.VISIBLE
                    ivHeaderIcon?.setImageResource(R.drawable.ic_series)
                    shelfContainer?.findViewById<TextView>(R.id.headerCountText)?.visibility = View.VISIBLE
                }
                else -> {}
            }
        } else {
            shelfContainer?.visibility = View.GONE
        }

        if (view is ShelfView.Home) {
            // Re-render the cached Home content so Continue Reading reflects the
            // latest DB state even if the LiveData emitted while Home was hidden.
            lastHomeContent?.let { renderHome(it) }
            // Always scroll Home to the top so Continue Reading is visible.
            binding.homeContent.homeScroll.post {
                binding.homeContent.homeScroll.scrollTo(0, 0)
            }
        }
        if (view is ShelfView.Settings) {
            showSettingsView()
            binding.mainSettingsContent.mainSettingsScroll.post {
                binding.mainSettingsContent.mainSettingsScroll.scrollTo(0, 0)
            }
        }
        if (isFolders) {
            showFoldersView()
            val foldersRoot = binding.root.findViewById<View>(R.id.mainFoldersContent)
            val foldersScroll = foldersRoot?.findViewById<androidx.core.widget.NestedScrollView>(R.id.foldersScroll)
            foldersScroll?.post {
                foldersScroll.scrollTo(0, 0)
            }
        }
        if (view is ShelfView.Stats) {
            showStatsView()
            val statsRoot = binding.root.findViewById<View>(R.id.mainStatsContent)
            val statsScroll = statsRoot?.findViewById<androidx.core.widget.NestedScrollView>(R.id.statsScroll)
            statsScroll?.post {
                statsScroll.scrollTo(0, 0)
            }
        }
        if (view is ShelfView.Vocabulary) {
            showVocabularyView()
        }
        refresh.isEnabled = true // Patch 12: keep the refresh layout always
        // enabled so the scanning spinner stays visible on EVERY view — previously
        // it was disabled on non-book views (Authors/Series/Folders/Settings) and a
        // scan started on Folders appeared to "stop" the moment the user navigated
        // away, because the spinner was no longer drawn. The scan job itself was
        // never cancelled; only the spinner vanished. Now the spinner remains on
        // screen until the scan finishes, and pull-to-refresh also works everywhere.

        // Swipe-to-dismiss only on the Currently Reading list
        touchHelper?.attachToRecyclerView(null)
        if (view is ShelfView.Reading) {
            val cb =
                object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                    override fun onMove(
                        rv: RecyclerView,
                        vh: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder,
                    ) = false

                    override fun onSwiped(
                        viewHolder: RecyclerView.ViewHolder,
                        dir: Int,
                    ) {
                        val pos = viewHolder.bindingAdapterPosition
                        val books = bookAdapter?.currentList ?: return
                        if (pos in books.indices) {
                            val book = books[pos]
                            viewModel.clearCurrentlyReading(book.id)
                            CurrentlyReadingUndoSnackbar.show(this@MainActivity, binding.root, book.id)
                        }
                    }
                }
            touchHelper = ItemTouchHelper(cb).also { r -> recycler?.let { r.attachToRecyclerView(it) } }
        }
    }

    // ---------------------------------------------------------------- observers
    private fun setupObservers() {
        viewModel.view.observe(this) { view -> applyView(view) }

        // View mode + column count apply immediately: reconfigure the adapter without refetching.
        viewModel.viewModeGrid.observe(this) {
            reconfigureAdapter()
            updateHeaderViewModeIcon()
        }
        viewModel.gridColumns.observe(this) { reconfigureAdapter() }

        viewModel.homeContent.observe(this) { content ->
            renderHome(content)
        }

        viewModel.content.observe(this) { items ->
            val view = viewModel.view.value ?: ShelfView.Library
            if (!isPlaceholder(view)) {
                binding.emptyState.visibility = View.GONE
            }

            if (view is ShelfView.Home) return@observe

            if (view is ShelfView.Settings || view is ShelfView.Stats || view is ShelfView.Vocabulary) {
                binding.emptyAction.visibility = View.GONE
                return@observe
            }

            if (isFoldersView(view)) {
                binding.emptyAction.visibility = View.GONE
                showFoldersView()
                return@observe
            }

            if (isPlaceholder(view)) {
                clearDynamicEmptyChildren()
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.gravity = android.view.Gravity.CENTER
                recycler?.visibility = View.GONE
                binding.emptyIcon.visibility = View.GONE
                binding.emptyText.visibility = View.VISIBLE
                binding.emptyText.text = getString(R.string.empty_placeholder)
                binding.emptyHint.text = getString(R.string.coming_soon)
                binding.emptyHint.visibility = View.VISIBLE
                binding.emptyAction.visibility = View.GONE
                return@observe
            }

            // Book list views: drop any Folders/Settings rows added earlier.
            clearDynamicEmptyChildren()

            if (items.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.gravity = android.view.Gravity.CENTER
                recycler?.visibility = View.GONE
                binding.emptyIcon.visibility = View.VISIBLE
                binding.emptyText.visibility = View.VISIBLE
                binding.emptyText.text =
                    when (view) {
                        is ShelfView.Reading -> getString(R.string.empty_reading)
                        is ShelfView.Favorites -> getString(R.string.empty_favorites)
                        is ShelfView.AuthorsList -> getString(R.string.empty_authors)
                        is ShelfView.SeriesList -> getString(R.string.empty_series)
                        else -> getString(R.string.empty_library)
                    }
                binding.emptyHint.text = if (view is ShelfView.Library) getString(R.string.empty_library_hint) else ""
                binding.emptyHint.visibility = if (view is ShelfView.Library) View.VISIBLE else View.GONE
                binding.emptyAction.visibility = if (view is ShelfView.Library) View.VISIBLE else View.GONE
                binding.emptyAction.setOnClickListener {
                    openMultiFileLauncher.launch(BookFileTypes.acceptedMimeTypes)
                }
            } else {
                binding.emptyState.visibility = View.GONE
                recycler?.visibility = View.VISIBLE
                binding.emptyAction.visibility = View.GONE
            }

            val first = items.firstOrNull()
            if (first is DisplayItem.GroupRow) {
                bindRowAdapter(items.filterIsInstance<DisplayItem.GroupRow>())
            } else {
                bindBookAdapter(items.filterIsInstance<DisplayItem.Book>().map { it.book })
            }
        }

        viewModel.scanning.asLiveData().observe(this) { refresh.isRefreshing = it }
        viewModel.scanMessage.asLiveData().observe(this) { msg ->
            msg?.let {
                Snackbar.make(refresh, it, Snackbar.LENGTH_SHORT).show()
                viewModel.setScanMessage(null)
            }
        }
    }

    private fun bindBookAdapter(books: List<BookEntity>) {
        currentBooks = books
        val grid = viewModel.viewModeGrid.value == true
        val cols = viewModel.gridColumns.value ?: 3

        val view = viewModel.view.value
        val countTv = binding.root.findViewById<TextView>(R.id.headerCountText)
        val tvHeaderSubtitle = binding.root.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val count = books.size
        val countText = if (count == 1) "1 book" else "$count books"
        countTv?.text = countText
        countTv?.visibility = View.VISIBLE
        if (view is ShelfView.AuthorDetail || view is ShelfView.SeriesDetail) {
            tvHeaderSubtitle?.visibility = View.GONE
        } else {
            tvHeaderSubtitle?.visibility = View.VISIBLE
            tvHeaderSubtitle?.text = when (view) {
                is ShelfView.AuthorsList -> getString(R.string.authors_heading_subtitle)
                is ShelfView.SeriesList -> getString(R.string.series_heading_subtitle)
                else -> tvHeaderSubtitle?.text
            }
        }

        // Patch 10: when switching shelf views (drawer tap / "Show" action /
        // sort change / grid-column change) the user wants a clean, instant
        // reset to the top — no visible fast-scroll, no mid-screen land, no
        // leftover scroll offset from the previous view. The Patch 9 fix only
        // ran scrollToPosition(0) inside the submitList commit callback, which
        // fires AFTER DiffUtil has already animated item inserts/removes; the
        // user therefore saw the old scroll offset and the item-diff animation
        // for a frame before the jump to 0 landed. Here we take a hard,
        // non-animated reset path: stop any in-flight scroll, disable item
        // animations for the swap, create a FRESH LayoutManager + BookAdapter
        if (scrollToTopOnNextContent) {
            scrollToTopOnNextContent = false
            val previousAnimator = recycler?.itemAnimator
            recycler?.stopScroll()
            recycler?.itemAnimator = null

            val newAdapter = BookAdapter(grid, ::openBook, ::showBookOptions, ::openDetails)
            newAdapter.stateRestorationPolicy =
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            val lm = if (grid) GridLayoutManager(this, cols) else LinearLayoutManager(this)

            bookAdapter = newAdapter
            rowAdapter = null
            recycler?.layoutManager = lm
            recycler?.adapter = newAdapter

            (lm as LinearLayoutManager).scrollToPositionWithOffset(0, 0)

            newAdapter.submitList(books) {
                recycler?.post {
                    (recycler?.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(0, 0)
                    recycler?.itemAnimator = previousAnimator
                }
            }
            return
        }

        if (bookAdapter == null || bookAdapter?.grid != grid) {
            bookAdapter = BookAdapter(grid, ::openBook, ::showBookOptions, ::openDetails)
            rowAdapter = null
            recycler?.adapter = bookAdapter
        } else if (recycler?.adapter != bookAdapter) {
            recycler?.adapter = bookAdapter
        }
        val currentLm = recycler?.layoutManager
        val needsNewLm = if (grid) {
            currentLm !is GridLayoutManager || currentLm.spanCount != cols
        } else {
            currentLm !is LinearLayoutManager || currentLm is GridLayoutManager
        }
        if (needsNewLm) {
            recycler?.layoutManager =
                if (grid) GridLayoutManager(this, cols) else LinearLayoutManager(this)
        }
        if (grid) {
            (recycler?.layoutManager as? GridLayoutManager)?.spanCount = cols
        }
        bookAdapter?.submitList(books) {
            if (pendingRestoreKey != null) {
                recycler?.post { tryRestoreScroll() }
            }
        }
    }

    private fun reconfigureAdapter() {
        val grid = viewModel.viewModeGrid.value == true
        val cols = viewModel.gridColumns.value ?: 3
        if (currentBooks.isNotEmpty() && isBookView(viewModel.view.value ?: ShelfView.Library)) {
            val previousAnimator = recycler?.itemAnimator
            recycler?.stopScroll()
            recycler?.itemAnimator = null

            val newAdapter = BookAdapter(grid, ::openBook, ::showBookOptions, ::openDetails)
            newAdapter.stateRestorationPolicy =
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            val lm = if (grid) GridLayoutManager(this, cols) else LinearLayoutManager(this)

            bookAdapter = newAdapter
            recycler?.layoutManager = lm
            recycler?.adapter = newAdapter
            lm.scrollToPositionWithOffset(0, 0)

            newAdapter.submitList(currentBooks) {
                recycler?.post {
                    (recycler?.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(0, 0)
                    recycler?.itemAnimator = previousAnimator
                }
            }
        }
    }

    private fun bindRowAdapter(rows: List<DisplayItem.GroupRow>) {
        val view = viewModel.view.value
        val icon = if (view is ShelfView.SeriesList) R.drawable.ic_series else R.drawable.ic_person_frame

        val countTv = binding.root.findViewById<TextView>(R.id.headerCountText)
        val tvHeaderSubtitle = binding.root.findViewById<TextView>(R.id.tvHeaderSubtitle)
        countTv?.visibility = View.VISIBLE
        tvHeaderSubtitle?.visibility = View.VISIBLE
        tvHeaderSubtitle?.text = when (view) {
            is ShelfView.SeriesList -> getString(R.string.series_heading_subtitle)
            else -> getString(R.string.authors_heading_subtitle)
        }
        val count = rows.size
        val unit = if (view is ShelfView.SeriesList) "series" else if (count == 1) "author" else "authors"
        countTv?.text = "$count $unit"

        if (scrollToTopOnNextContent) {
            scrollToTopOnNextContent = false
            val previousAnimator = recycler?.itemAnimator
            recycler?.stopScroll()
            recycler?.itemAnimator = null

            val newRowAdapter = RowAdapter(icon, onClick = { (name, _) ->
                captureScrollState()
                pendingRestoreKey = viewKey(viewModel.view.value ?: ShelfView.Library)
                scrollToTopOnNextContent = true
                viewModel.openDetail(name)
            })
            newRowAdapter.stateRestorationPolicy =
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            rowAdapter = newRowAdapter
            bookAdapter = null
            val lm = LinearLayoutManager(this)
            recycler?.layoutManager = lm
            recycler?.adapter = newRowAdapter
            lm.scrollToPositionWithOffset(0, 0)

            newRowAdapter.submitList(rows.map { com.epubreader.app.data.GroupedRow(it.name, it.count) }) {
                recycler?.post {
                    (recycler?.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(0, 0)
                    recycler?.itemAnimator = previousAnimator
                }
            }
            return
        }

        val onClick: (Pair<String, Long?>) -> Unit = { pair ->
            captureScrollState()
            pendingRestoreKey = viewKey(viewModel.view.value ?: ShelfView.Library)
            scrollToTopOnNextContent = true
            viewModel.openDetail(pair.first)
        }
        if (rowAdapter == null) {
            rowAdapter = RowAdapter(icon, onClick = onClick)
            bookAdapter = null
            recycler?.adapter = rowAdapter
            val currentLm = recycler?.layoutManager
            if (currentLm !is LinearLayoutManager || currentLm is GridLayoutManager) {
                recycler?.layoutManager = LinearLayoutManager(this)
            }
        } else {
            rowAdapter?.iconRes = icon
            if (recycler?.adapter != rowAdapter) {
                recycler?.adapter = rowAdapter
            }
            val currentLm = recycler?.layoutManager
            if (currentLm !is LinearLayoutManager || currentLm is GridLayoutManager) {
                recycler?.layoutManager = LinearLayoutManager(this)
            }
        }
        rowAdapter?.submitList(
            rows.map {
                com.epubreader.app.data
                    .GroupedRow(it.name, it.count)
            },
        ) {
            if (pendingRestoreKey != null) {
                recycler?.post { tryRestoreScroll() }
            }
        }
    }

    private fun updateHeaderViewModeIcon() {
        val headerContainer = binding.root.findViewById<android.widget.FrameLayout>(R.id.headerContainer)
        val viewModeBtn = headerContainer?.findViewById<android.widget.ImageButton>(R.id.libBtnViewMode)
        viewModeBtn?.setImageResource(
            if (viewModel.viewModeGrid.value == true) R.drawable.ic_list else R.drawable.ic_grid
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                returnToParentList()
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    // ---------------------------------------------------------------- search (separate screen)
    private fun launchSearch() {
        startActivity(Intent(this, SearchActivity::class.java))
    }

    // ---------------------------------------------------------------- sort (field -> asc/desc -> refresh + scroll top)
    private fun showSortDialog() {
        val options =
            listOf(
                getString(R.string.sort_recently_added) to PrefsManager.SortOption.RECENTLY_ADDED,
                getString(R.string.sort_recently_read) to PrefsManager.SortOption.RECENTLY_READ,
                getString(R.string.sort_title) to PrefsManager.SortOption.TITLE,
                getString(R.string.sort_series) to PrefsManager.SortOption.SERIES,
                getString(R.string.sort_author) to PrefsManager.SortOption.AUTHOR,
            )
        val current = options.indexOfFirst { it.second == viewModel.sort.value }
        AlertDialog
            .Builder(this)
            .setTitle(R.string.action_sort)
            .setSingleChoiceItems(
                options.map { it.first }.toTypedArray(),
                if (current < 0) 0 else current
            ) { d, which ->
                d.dismiss()
                showSortDirectionDialog(options[which].first, options[which].second)
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSortDirectionDialog(
        fieldLabel: String,
        fieldKey: String,
    ) {
        val asc = viewModel.sortAscending.value ?: true
        val labels = if (fieldKey == PrefsManager.SortOption.RECENTLY_ADDED) {
            arrayOf(getString(R.string.sort_newer_first), getString(R.string.sort_older_first))
        } else {
            arrayOf(getString(R.string.sort_ascending), getString(R.string.sort_descending))
        }
        AlertDialog
            .Builder(this)
            .setTitle("$fieldLabel · ${getString(R.string.sort_choose_order)}")
            .setSingleChoiceItems(labels, if (asc) 0 else 1) { d, which ->
                scrollToTopOnNextContent = true
                viewModel.setSort(fieldKey, which == 0)
                d.dismiss()
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- view mode (List / Grid 2 / Grid 3 / Grid 4)
    private fun showViewModeDialog() {
        val grid = viewModel.viewModeGrid.value == true
        val cols = viewModel.gridColumns.value ?: 3
        val labels =
            arrayOf(
                getString(R.string.view_mode_list),
                getString(R.string.view_mode_grid) + " 2",
                getString(R.string.view_mode_grid) + " 3",
                getString(R.string.view_mode_grid) + " 4",
            )
        val checked = if (!grid) 0 else (cols - 1).coerceIn(1, 3)
        AlertDialog
            .Builder(this)
            .setTitle(R.string.action_view_mode)
            .setSingleChoiceItems(labels, checked) { d, which ->
                when (which) {
                    0 -> {
                        viewModel.setViewMode(false)
                    }

                    else -> {
                        viewModel.setViewMode(true)
                        viewModel.setGridColumns(which + 1)
                    }
                }
                d.dismiss()
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- book options
    private fun showBookOptions(book: BookEntity): Boolean {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        labels += getString(R.string.option_open)
        actions += { openBook(book) }
        labels += getString(R.string.option_details)
        actions += { openDetails(book) }
        labels += if (book.isFavorite) getString(R.string.option_favorite_remove) else getString(R.string.option_favorite_add)
        actions += { viewModel.toggleFavorite(book) }
        if (viewModel.view.value is ShelfView.Reading) {
            labels += getString(R.string.option_remove_reading)
            actions += { viewModel.clearCurrentlyReading(book.id) }
        }
        labels += getString(R.string.option_remove)
        actions += { confirmDeleteBook(book) }
        AlertDialog
            .Builder(this)
            .setTitle(book.title)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
        return true
    }

    private fun confirmDeleteBook(book: BookEntity) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.option_remove)
            .setMessage(getString(R.string.option_remove) + ": " + book.title)
            .setPositiveButton(R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    importer.deleteImported(book)
                    BookRepository(applicationContext).deleteBook(book)
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- FAB + folder


    private fun onFabClicked() {
        when (val view = viewModel.view.value ?: ShelfView.Library) {
            is ShelfView.Library -> openMultiFileLauncher.launch(BookFileTypes.acceptedMimeTypes)
            is ShelfView.Folders -> showFolderOptionsDialog()
            else -> {}
        }
    }

    private fun showFolderOptionsDialog() {
        val items = arrayOf(
            getString(R.string.folder_select),
            getString(R.string.folder_scan_now),
            getString(R.string.folder_refresh_metadata),
            getString(R.string.folder_remove)
        )

        AlertDialog
            .Builder(this)
            .setTitle(R.string.folder_options)
            .setItems(items) { _, which ->

                when (which) {

                    0 -> {
                        openTreeLauncher.launch(null)
                    }

                    1 -> {
                        rescanSelectedFolder()
                    }

                    2 -> {
                        refreshMetadataSelectedFolder()
                    }

                    3 -> {
                        prefs.selectedFolderUri = null
                        showFoldersEmptyState()

                        Snackbar
                            .make(
                                refresh,
                                R.string.folder_none,
                                Snackbar.LENGTH_SHORT
                            )
                            .show()
                    }
                }
            }
            .setNegativeButton(
                R.string.cancel,
                null
            )
            .show()
    }

    private fun folderDisplayName(uriString: String): String {
        return try {
            val treeUri = Uri.parse(uriString)
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            docId.substringAfterLast(':').ifBlank { docId }
        } catch (_: Exception) {
            uriString
        }
    }

    /**
     * Re-parses every EPUB currently in the selected folder for metadata only.
     *
     * This intentionally does NOT use RescanDecision because the whole point
     * of this operation is to re-read metadata even when the file's size and
     * modified timestamp have not changed.
     *
     * The importer updates only metadata columns. Reading state and cached
     * EPUB content remain untouched.
     */

    private fun refreshMetadataSelectedFolder() {

        val uriString = prefs.selectedFolderUri

        if (uriString == null) {
            Snackbar
                .make(
                    binding.root,
                    R.string.scan_no_folder,
                    Snackbar.LENGTH_LONG
                )
                .setAction(R.string.folder_select) {
                    openTreeLauncher.launch(null)
                }
                .show()

            return
        }

        if (scanJob?.isActive == true) {
            Snackbar
                .make(
                    refresh,
                    R.string.scan_already_running,
                    Snackbar.LENGTH_SHORT
                )
                .show()

            return
        }

        val treeUri = Uri.parse(uriString)

        // Match Scan Now's immediate visual feedback, but only for a brief
        // moment. The metadata refresh itself remains a background job and does
        // not keep the spinner visible or restrict navigation.
        viewModel.setScanning(true)
        backResetHandler.postDelayed({
            viewModel.setScanning(false)
        }, 1200L)

        // Metadata refresh is deliberately a silent background job. Unlike the
        // user-facing folder scan, it must not activate the SwipeRefresh spinner
        // or otherwise restrict navigation while it runs. The tracked Job keeps
        // duplicate refreshes from starting, and the completion snackbar appears
        // wherever the user is in the app when the work finishes.
        scanJob =
            lifecycleScope.launch(Dispatchers.IO) {

                try {

                    val files =
                        importer.listEpubFiles(treeUri)

                    val result =
                        importer.refreshMetadata(files)

                    MetadataRefreshReportStore.latest = result

                    withContext(Dispatchers.Main) {

                        val snackbar =
                            Snackbar.make(
                                refresh,
                                getString(
                                    R.string.metadata_refresh_summary,
                                    result.updated,
                                    result.unchanged,
                                    result.skipped,
                                    result.failed,
                                ),
                                Snackbar.LENGTH_LONG
                            )

                        if (result.items.isNotEmpty()) {
                            snackbar.setAction(R.string.scan_show) {
                                startActivity(
                                    Intent(
                                        this@MainActivity,
                                        MetadataRefreshActivity::class.java
                                    )
                                )
                            }
                        }

                        snackbar.show()
                    }

                } catch (_: Exception) {

                    withContext(Dispatchers.Main) {

                        Snackbar
                            .make(
                                refresh,
                                R.string.scan_failed,
                                Snackbar.LENGTH_LONG
                            )
                            .show()
                    }

                } finally {
                    // No scanning-state UI is used for metadata refresh.
                }
            }
    }

    // ---------------------------------------------------------------- scanning
    private fun rescanSelectedFolder() {
        val uriString = prefs.selectedFolderUri
        if (uriString == null) {
            refresh.isRefreshing = false
            Snackbar.make(binding.root, R.string.scan_no_folder, Snackbar.LENGTH_LONG)
                .setAction(R.string.folder_select) { openTreeLauncher.launch(null) }
                .show()
            return
        }
        scanFolder(Uri.parse(uriString), fromRefresh = true)
    }

    /** Scans the selected SAF tree. Re-scans are fast because (a) every epub is
     *  listed in one bulk cursor pass per directory instead of per-file
     *  DocumentFile metadata calls, (b) existing books are matched against an
     *  in-memory fingerprint map (single DB query) and skipped when size + mtime
     *  are unchanged, and (c) all new/changed books are committed in one
     *  transaction so the library list refreshes once at the end instead of
     *  once per book (no visible layout thrash while scanning). */
    private fun scanFolder(treeUri: Uri, fromRefresh: Boolean) {
        // Patch 12: don't start a second scan while one is already running — two
        // concurrent scans on the same importer/DB would conflict and the second
        // would silently fail, which looked like "Scan Now doesn't do anything."
        if (scanJob?.isActive == true) {
            Snackbar.make(refresh, R.string.scan_already_running, Snackbar.LENGTH_SHORT).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        prefs.selectedFolderUri = treeUri.toString()
        viewModel.setScanning(true)
        // Tracked job: lives in the Activity lifecycle scope, which is NOT tied
        // to any one shelf view. Navigating Library -> Settings -> Folders (or
        // anywhere else) does NOT cancel this job, so the scan keeps running and
        // the spinner stays visible until it finishes.
        scanJob = lifecycleScope.launch(Dispatchers.IO) {
            // Patch 12: wrap the whole scan in try/catch/finally so the scanning
            // spinner is ALWAYS cleared — even if listEpubFiles / fingerprint map /
            // commitImports throws. Without this, an exception would leave
            // setScanning(true) forever and the spinner stuck.
            try {
                // (1) One cursor pass per directory — name + size + mtime for every epub.
                val files = importer.listEpubFiles(treeUri)
                // (2) One DB query → in-memory fingerprint map for the fast skip path.
                val fingerprints = importer.sourceFingerprintMap()
                // (3) Prepare (copy + parse + cover) every new/changed book WITHOUT
                //     touching the database. Heavy file IO stays outside the DB lock.
                val prepared = mutableListOf<EpubImporter.PreparedImport>()
                for (file in files) {
                    val matches = fingerprints[file.name].orEmpty()
                    // A stable source URI is the primary identity. Legacy rows with
                    // no source URI are deliberately processed once so their identity
                    // is upgraded; they must never be skipped solely by filename.
                    if (matches.any { fp ->
                            fp.sourceUri == file.uri.toString() &&
                                    RescanDecision.shouldSkip(
                                        existingFileSize = fp.fileSize,
                                        existingMtime = fp.sourceLastModified,
                                        sourceSize = file.size,
                                        sourceMtime = file.lastModified,
                                        cachedFileExists = File(fp.path).exists(),
                                    )
                        }
                    ) continue
                    try {
                        importer.prepareImport(file.uri, file.name, file.size, file.lastModified)
                            ?.let { prepared += it }
                    } catch (_: Exception) {
                    }
                }
                // (4) Commit all new/changed books in ONE transaction so the library's
                //     Room Flow re-emits a single time at the end.
                val newIds = importer.commitImports(prepared)
                withContext(Dispatchers.Main) {
                    showScanResult(newIds)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(refresh, R.string.scan_failed, Snackbar.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    viewModel.setScanning(false)
                }
            }
        }
    }

    private fun importMultiple(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModel.setScanning(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val newIds = mutableListOf<Long>()
            for (uri in uris) {
                try {
                    val result = importer.importUriResult(uri)
                    if (result.isNew && result.bookId != null) newIds.add(result.bookId)
                } catch (_: Exception) {
                }
            }
            withContext(Dispatchers.Main) {
                viewModel.setScanning(false)
                showScanResult(newIds)
            }
        }
    }

    // Patch 16 (Issue #3): now takes the actual ids of the newly imported books
    // (previously just a count) so the "Show" action can open the Recently
    // Added temp screen scoped to exactly those books.
    private fun showScanResult(newIds: List<Long>) {
        val newCount = newIds.size
        val text = if (newCount > 0)
            getString(R.string.scan_complete_new, newCount)
        else
            getString(R.string.scan_complete_none)
        val snackbar = Snackbar.make(refresh, text, Snackbar.LENGTH_LONG)
        if (newCount > 0) {
            snackbar.setAction(R.string.scan_show) {
                // Patch 16 (Issue #3): instead of dumping the user on the Library
                // and making them scroll to find the new book(s), remember the
                // shelf they're currently on and open the transient Recently
                // Added screen scoped to just the newly imported ids. The screen
                // is destroyed (state discarded) on back — see exitRecentlyAdded().
                viewBeforeRecentlyAdded = viewModel.view.value ?: ShelfView.Library
                scrollToTopOnNextContent = true
                viewModel.setView(ShelfView.RecentlyAdded(newIds))
            }
        }
        snackbar.show()
    }

    private fun showFoldersEmptyState() {
        showFoldersView()
    }

    private fun showFoldersView() {
        recycler?.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.homeContent.root.visibility = View.GONE
        binding.mainSettingsContent.root.visibility = View.GONE

        val foldersRoot = binding.root.findViewById<View>(R.id.mainFoldersContent) ?: return
        foldersRoot.visibility = View.VISIBLE

        val tvFolderTitle = foldersRoot.findViewById<android.widget.TextView>(R.id.tvFolderTitle)
        val tvFolderHint = foldersRoot.findViewById<android.widget.TextView>(R.id.tvFolderHint)
        val btnScanNow = foldersRoot.findViewById<View>(R.id.btnScanNow)
        val btnRefreshMetadata = foldersRoot.findViewById<View>(R.id.btnRefreshMetadata)
        val btnSelectFolder = foldersRoot.findViewById<View>(R.id.btnSelectFolder)
        val btnRemoveFolder = foldersRoot.findViewById<View>(R.id.btnRemoveFolder)

        val uri = prefs.selectedFolderUri
        tvFolderTitle?.text = if (uri != null) {
            getString(R.string.folder_selected, folderDisplayName(uri))
        } else {
            getString(R.string.folder_none)
        }
        tvFolderHint?.text = getString(R.string.folder_hint)

        if (uri != null) {
            btnScanNow?.visibility = View.VISIBLE
            btnRefreshMetadata?.visibility = View.VISIBLE
            btnRemoveFolder?.visibility = View.VISIBLE

            btnScanNow?.setOnClickListener { rescanSelectedFolder() }
            btnRefreshMetadata?.setOnClickListener { refreshMetadataSelectedFolder() }
            btnSelectFolder?.setOnClickListener { openTreeLauncher.launch(null) }
            btnRemoveFolder?.setOnClickListener {
                prefs.selectedFolderUri = null
                showFoldersView()
                Snackbar.make(refresh, R.string.folder_none, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            btnScanNow?.visibility = View.GONE
            btnRefreshMetadata?.visibility = View.GONE
            btnRemoveFolder?.visibility = View.GONE

            btnSelectFolder?.setOnClickListener { openTreeLauncher.launch(null) }
        }
    }

    private fun showSettingsView() {
        recycler?.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.homeContent.root.visibility = View.GONE
        binding.mainSettingsContent.root.visibility = View.VISIBLE

        // 1. Daily Reading Goal Section
        when (prefs.dailyGoalMinutes) {
            15 -> binding.mainSettingsContent.goal15.isChecked = true
            20 -> binding.mainSettingsContent.goal20.isChecked = true
            30 -> binding.mainSettingsContent.goal30.isChecked = true
            45 -> binding.mainSettingsContent.goal45.isChecked = true
            60 -> binding.mainSettingsContent.goal60.isChecked = true
            else -> binding.mainSettingsContent.goal30.isChecked = true
        }

        val chipGroup = binding.mainSettingsContent.chipGroupGoal
        val updateChipStyles = {
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip
                if (chip != null) {
                    if (chip.isChecked) {
                        chip.setChipBackgroundColorResource(R.color.accent_pop)
                        chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.black))
                        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.accent_pop)))
                    } else {
                        chip.setChipBackgroundColorResource(R.color.dark_surface_alt)
                        chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
                        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.palette_slate)))
                    }
                }
            }
        }
        updateChipStyles()

        binding.mainSettingsContent.chipGroupGoal.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            if (checkedId != View.NO_ID) {
                prefs.dailyGoalMinutes = when (checkedId) {
                    R.id.goal15 -> 15
                    R.id.goal20 -> 20
                    R.id.goal30 -> 30
                    R.id.goal45 -> 45
                    R.id.goal60 -> 60
                    else -> 30
                }
            }
            updateChipStyles()
        }
        binding.mainSettingsContent.goalDashboardSwitch.isChecked = prefs.showGoalOnDashboard
        binding.mainSettingsContent.goalDashboardSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.showGoalOnDashboard = checked
        }

        // 2. Scanned Folder Section
        val folder = prefs.selectedFolderUri
        binding.mainSettingsContent.tvScannedFolderValue.text = if (folder != null) folderDisplayName(folder) else getString(R.string.folder_none)
        binding.mainSettingsContent.cardFolder.setOnClickListener {
            viewModel.setView(ShelfView.Folders)
        }

        // 4. Screen On Settings Section
        binding.mainSettingsContent.screenOnSwitch.isChecked = prefs.keepScreenOn
        binding.mainSettingsContent.screenOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.keepScreenOn = isChecked
        }

        // 5. Flashcards & Mastery Tracking Feature
        binding.mainSettingsContent.vocabMasterySwitch.isChecked = prefs.enableVocabMasteryAndFlashcards
        binding.mainSettingsContent.vocabMasterySwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.enableVocabMasteryAndFlashcards = isChecked
        }

        // 5. Full Backup and Restore Section
        binding.mainSettingsContent.btnExportBackup.setOnClickListener {
            Toast.makeText(this, R.string.backup_export_success, Toast.LENGTH_LONG).show()
        }
        binding.mainSettingsContent.btnImportBackup.setOnClickListener {
            Toast.makeText(this, R.string.backup_import_success, Toast.LENGTH_LONG).show()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev?.action == android.view.MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is android.widget.EditText) {
                val outRect = android.graphics.Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    hideKeyboard(v)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideKeyboard(view: View? = null) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        val targetToken = view?.windowToken ?: currentFocus?.windowToken
        if (targetToken != null) {
            imm?.hideSoftInputFromWindow(targetToken, 0)
        }
    }

    private fun themeColor(attr: Int): Int {
        // Resolve a theme color attribute to a concrete ARGB int. Use
        // obtainStyledAttributes (not TypedValue.data) because textColorPrimary /
        // textColorSecondary are ColorStateList attrs whose .data can be wrong.
        val ta = obtainStyledAttributes(intArrayOf(attr))
        try {
            return ta.getColor(0, 0xFF000000.toInt())
        } finally {
            ta.recycle()
        }
    }

    private fun clearDynamicEmptyChildren() {
        dynamicEmptyChildren.forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        dynamicEmptyChildren.clear()
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    private val dynamicEmptyChildren = mutableListOf<android.view.View>()

    /**
     * Patch 18 (Addition #3): handles an incoming ACTION_VIEW intent for an
     * .epub (from the system "Open with" sheet / a file manager). Validates
     * the file type client-side (defense in depth — the intent filter already
     * scopes to epub MIME + .epub path), imports it, and opens the reader.
     */
    private fun handleViewIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (!BookFileTypes.isBookFile(uri, contentResolver)) {
            Snackbar.make(binding.root, R.string.import_unsupported_file, Snackbar.LENGTH_SHORT).show()
            return
        }
        viewModel.setScanning(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val bookId = try {
                importer.importUri(uri)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                viewModel.setScanning(false)
                if (bookId != null) {
                    startActivity(
                        Intent(this@MainActivity, ReaderActivity::class.java)
                            .putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                } else {
                    Snackbar.make(binding.root, R.string.import_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openBook(book: BookEntity) {
        viewModel.markOpened(book.id)
        // Patch 11 / Fix #4: capture this view's scroll position before navigating
        // away. Currently Reading restores this position when returning from Book
        // Details, while the reader path below continues to use its existing
        // top-reset behaviour.
        captureScrollState(book.id)
        viewModel.view.value?.let {
            if (it is ShelfView.Reading) {
                pendingReadingTopReset = true
            } else if (isRestoreEligible(it)) {
                pendingRestoreKey = viewKey(it)
            }
        }
        startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id))
    }

    private fun openDetails(book: BookEntity) {
        captureScrollState(book.id)
        viewModel.view.value?.let {
            if (isRestoreEligible(it)) {
                pendingRestoreKey = viewKey(it)
            }
        }
        startActivity(
            Intent(this, BookDetailsActivity::class.java).putExtra(
                BookDetailsActivity.EXTRA_BOOK_ID,
                book.id
            )
        )
    }

    private fun showStatsView() {
        val statsRoot = binding.root.findViewById<View>(R.id.mainStatsContent) ?: return
        statsRoot.findViewById<View>(R.id.cardStatsGoal)?.setOnClickListener { showDailyGoalDialog() }
        val now = System.currentTimeMillis()
        val weekStart = java.util.Calendar.getInstance().apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        }.timeInMillis
        val yearStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY); set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                val db = AppDatabase.get(applicationContext)
                val dao = db.readingSessionDao()
                val days = dao.activeDays()
                var streak = 0
                if (days.isNotEmpty()) {
                    val keys = days.toHashSet()
                    val cursor = java.util.Calendar.getInstance().apply {
                        timeInMillis = now
                        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                    }
                    while (true) {
                        val key = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cursor.time)
                        if (!keys.contains(key)) break
                        streak++
                        cursor.add(java.util.Calendar.DAY_OF_YEAR, -1)
                    }
                }
                StatsValues(
                    weekSeconds = dao.activeSecondsSince(weekStart),
                    yearSeconds = dao.activeSecondsSinceYear(yearStart),
                    todaySeconds = dao.activeSecondsSince(todayStart),
                    chapters = dao.chaptersSince(yearStart),
                    pages = dao.pagesSince(yearStart),
                    finished = db.bookDao().getFinishedCount(),
                    streak = streak,
                    todayPages = dao.pagesSince(todayStart)
                )
            }
            val isGoalEnabled = prefs.showGoalOnDashboard
            val goalFractionTv = statsRoot.findViewById<TextView>(R.id.statsGoalFraction)
            val goalProgressRow = statsRoot.findViewById<View>(R.id.statsGoalProgressRow)
            val goalDisabledInfo = statsRoot.findViewById<TextView>(R.id.statsGoalDisabledInfo)

            if (isGoalEnabled) {
                goalFractionTv?.visibility = View.VISIBLE
                goalProgressRow?.visibility = View.VISIBLE
                goalDisabledInfo?.visibility = View.GONE

                val goalType = prefs.goalType
                if (goalType == "pages") {
                    val todayPages = stats.todayPages
                    val goalPages = prefs.dailyGoalPages.coerceAtLeast(1)
                    val percent = ((todayPages * 100) / goalPages).coerceIn(0, 100)
                    goalFractionTv?.text = "$todayPages of $goalPages pages completed today"
                    statsRoot.findViewById<ProgressBar>(R.id.statsGoalProgressBar)?.progress = percent
                    statsRoot.findViewById<TextView>(R.id.statsGoalPercent)?.text = "$percent%"
                } else {
                    val todayMinutes = stats.todaySeconds / 60
                    val goalMinutes = prefs.dailyGoalMinutes.coerceAtLeast(1)
                    val percent = ((todayMinutes * 100) / goalMinutes).coerceIn(0, 100)
                    goalFractionTv?.text = "$todayMinutes of $goalMinutes minutes completed today"
                    statsRoot.findViewById<ProgressBar>(R.id.statsGoalProgressBar)?.progress = percent
                    statsRoot.findViewById<TextView>(R.id.statsGoalPercent)?.text = "$percent%"
                }
            } else {
                goalFractionTv?.visibility = View.GONE
                goalProgressRow?.visibility = View.GONE
                goalDisabledInfo?.visibility = View.VISIBLE
            }

            statsRoot.findViewById<TextView>(R.id.statsWeek)?.text = formatStatsMinutes(stats.weekSeconds)
            statsRoot.findViewById<TextView>(R.id.statsYear)?.text = formatStatsMinutes(stats.yearSeconds)
            statsRoot.findViewById<TextView>(R.id.statsFinished)?.text = stats.finished.toString()
            statsRoot.findViewById<TextView>(R.id.statsStreak)?.text = "${stats.streak} ${if (stats.streak == 1) "day" else getString(R.string.stats_days)}"
            statsRoot.findViewById<TextView>(R.id.statsChapters)?.text = stats.chapters.toString()
            statsRoot.findViewById<TextView>(R.id.statsPages)?.text = stats.pages.toString()
        }
    }

    private data class StatsValues(
        val weekSeconds: Int, val yearSeconds: Int, val todaySeconds: Int, val chapters: Int, val pages: Int, val finished: Int, val streak: Int, val todayPages: Int
    )

    private fun showDailyGoalDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_daily_goal, null, false)
        val toggleGroup = dialogView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.goalTypeToggleGroup)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.goalRadioGroup)
        val customLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.goalCustomInputLayout)
        val customEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.goalCustomEdit)
        val saveBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.goalSaveBtn)
        val remindLaterBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.goalRemindLaterBtn)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        var currentType = prefs.goalType
        var selectedValue = if (currentType == "minutes") prefs.dailyGoalMinutes else prefs.dailyGoalPages

        if (currentType == "pages") {
            toggleGroup?.check(R.id.btnPages)
        } else {
            toggleGroup?.check(R.id.btnMinutes)
        }

        val populateOptions: (String) -> Unit = { type ->
            radioGroup?.removeAllViews()
            val options = if (type == "minutes") {
                listOf(
                    Triple(10, "10 min", "Quick Read • Perfect for busy days."),
                    Triple(20, "20 min", "The Sweet Spot • Great for building a lasting habit. (Recommended)"),
                    Triple(30, "30 min", "Deep Dive • For steady, focused progress."),
                    Triple(45, "45 min", "Bookworm • Swap out screen time for reading."),
                    Triple(60, "60 min", "Marathon • For dedicated, heavy readers.")
                )
            } else {
                listOf(
                    Triple(5, "5 pages", "Easy Start"),
                    Triple(10, "10 pages", "Casual Pace"),
                    Triple(20, "20 pages", "Steady Progress (Recommended)"),
                    Triple(30, "30 pages", "Active Reader"),
                    Triple(50, "50 pages", "Fast Track")
                )
            }

            var matchedPreset = false
            options.forEach { (value, label, subtext) ->
                val rb = android.widget.RadioButton(this).apply {
                    text = "$label\n$subtext"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    setPadding(12, 12, 12, 12)
                    tag = value
                }
                radioGroup?.addView(rb)
                if (value == selectedValue) {
                    rb.isChecked = true
                    matchedPreset = true
                }
            }

            val customRb = android.widget.RadioButton(this).apply {
                text = "Custom\nEnter ${if (type == "minutes") "minutes" else "pages"}..."
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setPadding(12, 12, 12, 12)
                tag = -1
            }
            radioGroup?.addView(customRb)

            if (!matchedPreset) {
                customRb.isChecked = true
                customLayout?.visibility = View.VISIBLE
                customEdit?.setText(selectedValue.toString())
                customLayout?.hint = if (type == "minutes") "Enter minutes..." else "Enter pages..."
            } else {
                customLayout?.visibility = View.GONE
            }

            radioGroup?.setOnCheckedChangeListener { _: android.widget.RadioGroup, checkedId: Int ->
                val checkedRb = dialogView.findViewById<android.widget.RadioButton>(checkedId)
                if (checkedRb?.tag == -1) {
                    customLayout?.visibility = View.VISIBLE
                    customEdit?.hint = if (type == "minutes") "Enter minutes..." else "Enter pages..."
                } else {
                    customLayout?.visibility = View.GONE
                    val tagObj = checkedRb?.tag
                    if (tagObj is Int && tagObj > 0) {
                        selectedValue = tagObj
                    }
                }
            }
        }

        populateOptions(currentType)

        toggleGroup?.addOnButtonCheckedListener { _: com.google.android.material.button.MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
            if (isChecked) {
                currentType = if (checkedId == R.id.btnPages) "pages" else "minutes"
                selectedValue = if (currentType == "minutes") prefs.dailyGoalMinutes else prefs.dailyGoalPages
                populateOptions(currentType)
            }
        }

        remindLaterBtn?.setOnClickListener {
            dialog.dismiss()
        }

        saveBtn?.setOnClickListener {
            val checkedId = radioGroup?.checkedRadioButtonId ?: -1
            val checkedRb = dialogView.findViewById<android.widget.RadioButton>(checkedId)
            if (checkedRb?.tag == -1) {
                val customVal = customEdit?.text?.toString()?.toIntOrNull()
                if (customVal != null && customVal > 0) {
                    selectedValue = customVal
                }
            }

            prefs.goalType = currentType
            if (currentType == "minutes") {
                prefs.dailyGoalMinutes = selectedValue
            } else {
                prefs.dailyGoalPages = selectedValue
            }

            dialog.dismiss()
            showStatsView()
            Snackbar.make(binding.root, "Daily reading goal updated!", Snackbar.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun formatStatsMinutes(seconds: Int): String {
        val minutes = seconds / 60
        return if (minutes < 60) getString(R.string.stats_minutes_value, minutes)
        else getString(R.string.stats_hours_value, minutes / 60, minutes % 60)
    }

    private var vocabAdapter: com.epubreader.app.ui.VocabularyAdapter? = null
    private var vocabFullList: List<com.epubreader.app.data.DictionaryHistoryEntity> = emptyList()
    private var vocabFlashcardIndex: Int = 0
    private var vocabFlashcardFlipped: Boolean = false
    private var ttsPronouncer: com.epubreader.app.util.TtsPronouncer? = null

    private fun showVocabularyView() {
        val isFeatureEnabled = prefs.enableVocabMasteryAndFlashcards
        val vocabContainer = binding.root.findViewById<android.view.ViewGroup>(R.id.mainVocabularyContent) ?: return

        val requiredTag = if (isFeatureEnabled) "vocab_full" else "vocab_default"
        if (vocabContainer.tag != requiredTag) {
            vocabContainer.removeAllViews()
            val layoutRes = if (isFeatureEnabled) R.layout.view_main_vocabulary else R.layout.view_main_vocabulary_default
            layoutInflater.inflate(layoutRes, vocabContainer, true)
            vocabContainer.tag = requiredTag
            vocabAdapter = null
        }

        val vocabRoot = vocabContainer
        if (ttsPronouncer == null) {
            ttsPronouncer = com.epubreader.app.util.TtsPronouncer(this)
        }
        val tabs = vocabRoot.findViewById<com.google.android.material.tabs.TabLayout>(R.id.vocabTabs)
        val wordsContainer = vocabRoot.findViewById<View>(R.id.vocabWordsTabContainer)
        val flashcardContainer = vocabRoot.findViewById<View>(R.id.vocabFlashcardContainer)
        val searchEdit = vocabRoot.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.vocabSearchEdit)
        val chipGroup = vocabRoot.findViewById<com.google.android.material.chip.ChipGroup>(R.id.vocabFilterChipGroup)
        val recycler = vocabRoot.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.vocabRecycler)
        val emptyText = vocabRoot.findViewById<View>(R.id.vocabEmptyText)
        val actionsRow = vocabRoot.findViewById<View>(R.id.vocabActionsRow)

        if (vocabAdapter == null || vocabAdapter?.showMastery != isFeatureEnabled) {
            vocabAdapter = com.epubreader.app.ui.VocabularyAdapter(
                showMastery = isFeatureEnabled,
                onMasteryCycle = { item ->
                    val nextMastery = (item.masteryLevel + 1) % 3
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.get(applicationContext).dictionaryHistoryDao().updateMasteryLevel(item.id, nextMastery)
                    }
                },
                onPronounce = { word ->
                    ttsPronouncer?.speak(word)
                },
                onDelete = { item ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.get(applicationContext).dictionaryHistoryDao().delete(item)
                    }
                }
            )
        }
        recycler?.layoutManager = LinearLayoutManager(this)
        recycler?.adapter = vocabAdapter

        // Dual Tab Switcher
        tabs?.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                hideKeyboard(searchEdit)
                searchEdit?.clearFocus()
                if (tab?.position == 1) {
                    wordsContainer?.visibility = View.GONE
                    flashcardContainer?.visibility = View.VISIBLE
                    renderFlashcard(vocabRoot)
                } else {
                    wordsContainer?.visibility = View.VISIBLE
                    flashcardContainer?.visibility = View.GONE
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        val subtitleTv = vocabRoot.findViewById<TextView>(R.id.vocabSubtitleText)
        // Live Search Filter
        val applyFilters = {
            val query = searchEdit?.text?.toString()?.trim()?.lowercase() ?: ""
            val checkedId = chipGroup?.checkedChipId
            val filtered = vocabFullList.filter { item ->
                val matchesQuery = query.isBlank() ||
                    item.word.lowercase().contains(query) ||
                    (item.definition?.lowercase()?.contains(query) == true) ||
                    (item.bookTitle?.lowercase()?.contains(query) == true)

                val matchesChip = when (checkedId) {
                    R.id.chipNew -> item.masteryLevel == 0
                    R.id.chipLearning -> item.masteryLevel == 1
                    R.id.chipMastered -> item.masteryLevel == 2
                    else -> true
                }
                matchesQuery && matchesChip
            }
            vocabAdapter?.submitList(filtered)
            emptyText?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            recycler?.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            actionsRow?.visibility = if (vocabFullList.isEmpty()) View.GONE else View.VISIBLE

            val total = vocabFullList.size
            val count = filtered.size
            val wordOrWords = if (count == 1) "word" else "words"
            subtitleTv?.text = if (filtered.size == vocabFullList.size) {
                vocabRoot.context.getString(R.string.vocab_heading_subtitle, total, if (total == 1) "word" else "words")
            } else {
                "$count matching $wordOrWords (filtered from $total)"
            }
        }

        searchEdit?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilters() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val updateChipStyles = {
            for (i in 0 until (chipGroup?.childCount ?: 0)) {
                val chip = chipGroup?.getChildAt(i) as? com.google.android.material.chip.Chip
                if (chip != null) {
                    if (chip.isChecked) {
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.accent_pop))
                        chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.black))
                    } else {
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.palette_plum))
                        chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.palette_mint))
                    }
                }
            }
        }
        updateChipStyles()

        chipGroup?.setOnCheckedStateChangeListener { _, _ ->
            updateChipStyles()
            applyFilters()
        }

        vocabRoot.findViewById<View>(R.id.vocabExportCsv)?.setOnClickListener { exportVocab(0) }
        vocabRoot.findViewById<View>(R.id.vocabExportMarkdown)?.setOnClickListener { exportVocab(1) }
        vocabRoot.findViewById<View>(R.id.vocabClearAll)?.setOnClickListener { confirmClearVocab() }

        // Flashcard controls
        val flashcardCard = vocabRoot.findViewById<View>(R.id.flashcardCardView)
        val flashcardPrev = vocabRoot.findViewById<View>(R.id.flashcardPrevBtn)
        val flashcardNext = vocabRoot.findViewById<View>(R.id.flashcardNextBtn)
        val flashcardLearning = vocabRoot.findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardLearningBtn)
        val flashcardMastered = vocabRoot.findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardMasteredBtn)
        val flashcardAudioBtn = vocabRoot.findViewById<View>(R.id.flashcardAudioBtn)

        flashcardLearning?.setOnClickListener {
            if (vocabFullList.isNotEmpty() && vocabFlashcardIndex in vocabFullList.indices) {
                val current = vocabFullList[vocabFlashcardIndex]
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().updateMasteryLevel(current.id, 1)
                }
            }
        }

        flashcardMastered?.setOnClickListener {
            if (vocabFullList.isNotEmpty() && vocabFlashcardIndex in vocabFullList.indices) {
                val current = vocabFullList[vocabFlashcardIndex]
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().updateMasteryLevel(current.id, 2)
                }
            }
        }

        flashcardAudioBtn?.setOnClickListener {
            if (vocabFullList.isNotEmpty() && vocabFlashcardIndex in vocabFullList.indices) {
                val current = vocabFullList[vocabFlashcardIndex]
                if (ttsPronouncer == null) {
                    ttsPronouncer = com.epubreader.app.util.TtsPronouncer(this)
                }
                val textToSpeak = if (vocabFlashcardFlipped && !current.definition.isNullOrBlank()) {
                    "${current.word}. ${current.definition}"
                } else {
                    current.word
                }
                ttsPronouncer?.speak(textToSpeak)
            }
        }

        flashcardCard?.setOnClickListener {
            vocabFlashcardFlipped = !vocabFlashcardFlipped
            val front = vocabRoot.findViewById<View>(R.id.flashcardFrontSide)
            val back = vocabRoot.findViewById<View>(R.id.flashcardBackSide)
            val scale = vocabRoot.resources.displayMetrics.density
            flashcardCard.cameraDistance = 8000f * scale

            val targetRotation = if (vocabFlashcardFlipped) 180f else 0f
            flashcardCard.animate()
                .rotationY(targetRotation)
                .setDuration(350)
                .setUpdateListener { _ ->
                    val currentRotation = flashcardCard.rotationY
                    if (vocabFlashcardFlipped) {
                        if (currentRotation >= 90f) {
                            front?.visibility = View.GONE
                            back?.visibility = View.VISIBLE
                            back?.rotationY = 180f
                        }
                    } else {
                        if (currentRotation <= 90f) {
                            back?.visibility = View.GONE
                            front?.visibility = View.VISIBLE
                            front?.rotationY = 0f
                        }
                    }
                }
                .start()
        }

        flashcardPrev?.setOnClickListener {
            if (vocabFullList.isNotEmpty()) {
                vocabFlashcardIndex = (vocabFlashcardIndex - 1 + vocabFullList.size) % vocabFullList.size
                vocabFlashcardFlipped = false
                flashcardCard?.rotationY = 0f
                vocabRoot.findViewById<View>(R.id.flashcardFrontSide)?.visibility = View.VISIBLE
                vocabRoot.findViewById<View>(R.id.flashcardBackSide)?.visibility = View.GONE
                renderFlashcard(vocabRoot)
            }
        }

        flashcardNext?.setOnClickListener {
            if (vocabFullList.isNotEmpty()) {
                vocabFlashcardIndex = (vocabFlashcardIndex + 1) % vocabFullList.size
                vocabFlashcardFlipped = false
                flashcardCard?.rotationY = 0f
                vocabRoot.findViewById<View>(R.id.flashcardFrontSide)?.visibility = View.VISIBLE
                vocabRoot.findViewById<View>(R.id.flashcardBackSide)?.visibility = View.GONE
                renderFlashcard(vocabRoot)
            }
        }

        flashcardLearning?.setOnClickListener {
            if (vocabFullList.isNotEmpty() && vocabFlashcardIndex in vocabFullList.indices) {
                val current = vocabFullList[vocabFlashcardIndex]
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().updateMasteryLevel(current.id, 1)
                }
            }
        }

        flashcardMastered?.setOnClickListener {
            if (vocabFullList.isNotEmpty() && vocabFlashcardIndex in vocabFullList.indices) {
                val current = vocabFullList[vocabFlashcardIndex]
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().updateMasteryLevel(current.id, 2)
                }
            }
        }

        AppDatabase.get(this).dictionaryHistoryDao().observeAll().asLiveData().observe(this) { list ->
            vocabFullList = list
            applyFilters()
            if (vocabFlashcardIndex >= list.size) vocabFlashcardIndex = (list.size - 1).coerceAtLeast(0)
            renderFlashcard(vocabRoot)

            val subtitleTv = vocabRoot.findViewById<TextView>(R.id.vocabSubtitleText)
            val count = list.size
            val wordOrWords = if (count == 1) "word" else "words"
            subtitleTv?.text = vocabRoot.context.getString(R.string.vocab_heading_subtitle, count, wordOrWords)
        }
    }

    private fun renderFlashcard(vocabRoot: View) {
        if (vocabFullList.isEmpty()) return
        val current = vocabFullList[vocabFlashcardIndex.coerceIn(0, vocabFullList.size - 1)]
        val progressTv = vocabRoot.findViewById<TextView>(R.id.flashcardProgressText)
        val wordTv = vocabRoot.findViewById<TextView>(R.id.flashcardWord)
        val phoneticTv = vocabRoot.findViewById<TextView>(R.id.flashcardPhonetic)
        val posTv = vocabRoot.findViewById<TextView>(R.id.flashcardPos)
        val defTv = vocabRoot.findViewById<TextView>(R.id.flashcardDef)
        val exampleTv = vocabRoot.findViewById<TextView>(R.id.flashcardExample)
        val flashcardLearning = vocabRoot.findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardLearningBtn)
        val flashcardMastered = vocabRoot.findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardMasteredBtn)

        progressTv?.text = vocabRoot.context.getString(R.string.vocab_flashcard_progress, vocabFlashcardIndex + 1, vocabFullList.size)

        wordTv?.text = current.word.uppercase()
        phoneticTv?.text = current.phonetic ?: "/${current.word.lowercase()}/"
        posTv?.text = current.partOfSpeech?.uppercase() ?: "WORD"
        defTv?.text = current.definition ?: getString(R.string.dictionary_not_found)
        exampleTv?.text = if (!current.example.isNullOrBlank()) "\"${current.example}\"" else ""

        updateFlashcardMasteryState(current, flashcardLearning, flashcardMastered, vocabRoot.context)
    }

    private fun updateFlashcardMasteryState(
        current: com.epubreader.app.data.DictionaryHistoryEntity,
        learningBtn: com.google.android.material.button.MaterialButton?,
        masteredBtn: com.google.android.material.button.MaterialButton?,
        context: Context
    ) {
        val accentPop = ContextCompat.getColor(context, R.color.accent_pop)
        val black = ContextCompat.getColor(context, android.R.color.black)
        val white = ContextCompat.getColor(context, android.R.color.white)
        val transparent = android.graphics.Color.TRANSPARENT
        val strokeColorState = android.content.res.ColorStateList.valueOf(accentPop)

        val isLearning = current.masteryLevel == 1
        val isMastered = current.masteryLevel == 2

        learningBtn?.strokeColor = strokeColorState
        if (isLearning) {
            learningBtn?.backgroundTintList = android.content.res.ColorStateList.valueOf(accentPop)
            learningBtn?.setTextColor(black)
            learningBtn?.text = context.getString(R.string.vocab_mark_learning)
        } else {
            learningBtn?.backgroundTintList = android.content.res.ColorStateList.valueOf(transparent)
            learningBtn?.setTextColor(white)
            learningBtn?.text = context.getString(R.string.vocab_mark_learning)
        }

        masteredBtn?.strokeColor = strokeColorState
        if (isMastered) {
            masteredBtn?.backgroundTintList = android.content.res.ColorStateList.valueOf(accentPop)
            masteredBtn?.setTextColor(black)
            masteredBtn?.text = context.getString(R.string.vocab_mastered_badge)
        } else {
            masteredBtn?.backgroundTintList = android.content.res.ColorStateList.valueOf(transparent)
            masteredBtn?.setTextColor(white)
            masteredBtn?.text = context.getString(R.string.vocab_mark_mastered)
        }
    }

    private fun confirmClearVocab() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.vocabulary_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().clear()
                }
                Snackbar.make(binding.root, R.string.vocabulary_cleared, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportVocab(format: Int) {
        val words = vocabAdapter?.currentList ?: return
        if (words.isEmpty()) return
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val (fileName, mime, content) = when (format) {
            1 -> Triple("vocabulary-$timestamp.md", "text/markdown", buildVocabMarkdown(words))
            else -> Triple("vocabulary-$timestamp.csv", "text/csv", buildVocabCsv(words))
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val file = java.io.File(cacheDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            withContext(Dispatchers.Main) {
                shareVocabFile(file, mime)
                Snackbar.make(binding.root, R.string.vocabulary_exported, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildVocabCsv(words: List<com.epubreader.app.data.DictionaryHistoryEntity>): String = buildString {
        append("word,part_of_speech,definition,example,source_book,mastery\n")
        words.forEach { item ->
            val masteryStr = when (item.masteryLevel) {
                1 -> "Learning"
                2 -> "Mastered"
                else -> "New"
            }
            append(csvCell(item.word)).append(',')
                .append(csvCell(item.partOfSpeech.orEmpty())).append(',')
                .append(csvCell(item.definition.orEmpty())).append(',')
                .append(csvCell(item.example.orEmpty())).append(',')
                .append(csvCell(item.bookTitle.orEmpty())).append(',')
                .append(csvCell(masteryStr)).append('\n')
        }
    }

    private fun csvCell(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun buildVocabMarkdown(words: List<com.epubreader.app.data.DictionaryHistoryEntity>): String = buildString {
        append("# Vocabulary Sanctuary\n\n")
        append("| Word | Part of Speech | Definition | Context Usage | Source Book | Mastery |\n")
        append("| --- | --- | --- | --- | --- | --- |\n")
        words.forEach { item ->
            val masteryStr = when (item.masteryLevel) {
                1 -> "Learning"
                2 -> "Mastered"
                else -> "New"
            }
            append("| **").append(item.word.replace("|", "\\|")).append("** | ")
                .append(item.partOfSpeech.orEmpty().replace("|", "\\|")).append(" | ")
                .append(item.definition.orEmpty().replace("|", "\\|").replace("\n", " ")).append(" | ")
                .append(item.example.orEmpty().replace("|", "\\|").replace("\n", " ")).append(" | ")
                .append(item.bookTitle.orEmpty().replace("|", "\\|")).append(" | ")
                .append(masteryStr).append(" |\n")
        }
    }

    private fun shareVocabFile(file: java.io.File, mime: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.vocabulary_title)))
    }
}
