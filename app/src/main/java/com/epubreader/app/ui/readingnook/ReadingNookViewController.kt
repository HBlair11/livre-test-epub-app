package com.epubreader.app.ui.readingnook

import android.content.Intent
import android.graphics.Color
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.epubreader.app.R
import com.epubreader.app.ReaderActivity
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.ReadingJournalEntity
import com.epubreader.app.data.ReadingStampEntity
import com.epubreader.app.databinding.ActivityReadingNookBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class ReadingNookViewController(
    private val activity: AppCompatActivity,
    private val binding: ActivityReadingNookBinding,
    private val bookId: Long
) {
    private val db = AppDatabase.get(activity.applicationContext)
    private var book: BookEntity? = null
    private var currentTab = 0 // 0: Overview, 1: Journal, 2: Stamps, 3: Highlights
    private var isJournalFormVisible = false
    private var isStampFormVisible = false
    private var selectedStampType = "Favorite Moment"

    data class StampTheme(
        val titleColorHex: String,
        val bgColorHex: String,
        val borderColorHex: String
    )

    private fun getStampTheme(stampType: String): StampTheme = when (stampType) {
        "Started Reading" -> StampTheme("#D88C9A", "#FFE4F3", "#66D88C9A")
        "Resumed" -> StampTheme("#8E7DBE", "#F2EEFF", "#668E7DBE")
        "Favorite Moment" -> StampTheme("#B45309", "#FFFBEB", "#FDE68A")
        "Memorable Passage" -> StampTheme("#047857", "#ECFDF5", "#A7F3D0")
        "Revisited" -> StampTheme("#0369A1", "#F0F9FF", "#BAE6FD")
        "Finished Reading" -> StampTheme("#9F1239", "#FFE4E6", "#FDA4AF")
        "Reread in Progress" -> StampTheme("#5A4650", "#F1E3D3", "#66B48EAE")
        else -> StampTheme("#B45309", "#FFFBEB", "#FDE68A")
    }

    private fun Int.dpToPx(): Int = (this * activity.resources.displayMetrics.density).toInt()

    fun setup() {
        setupHeader()
        loadBook()
        setupTabs()
    }

    fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            val view = activity.currentFocus
            if (view is TextInputEditText) {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                val left = location[0]
                val top = location[1]
                val right = left + view.width
                val bottom = top + view.height

                if (ev.rawX < left || ev.rawX > right || ev.rawY < top || ev.rawY > bottom) {
                    view.clearFocus()
                    val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        }
        return false
    }

    private fun setupHeader() {
        val inflated = activity.layoutInflater.inflate(R.layout.header_secondary_category_2, binding.headerContainer, true)
        val toolbar = inflated.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setHomeButtonEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { activity.finish() }
    }

    fun load() {
        loadBook()
    }

    private fun loadBook() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            book = db.bookDao().getById(bookId)
            val journalList = db.readingJournalDao().getForBook(bookId)
            val stampList = db.readingStampDao().getForBook(bookId)
            val highlights = db.highlightDao().getForBook(bookId)
            val sessions = db.readingSessionDao().getForBook(bookId)

            withContext(Dispatchers.Main) {
                val b = book ?: return@withContext
                // Toolbar title
                val title1 = activity.findViewById<TextView>(R.id.toolbarSubtitle)
                val title2 = activity.findViewById<TextView>(R.id.toolbarTitle)
                title1?.text = activity.getString(R.string.reading_nook_screen_title)
                title2?.text = b.title

                // Hero profile card
                binding.nookTitle.text = b.title
                val authorSeries = StringBuilder(b.author.ifBlank { "Unknown Author" })
                if (!b.series.isNullOrBlank()) {
                    val formattedIndex = b.seriesIndex?.let { index ->
                        if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
                    }
                    authorSeries.append(" • ").append(b.series)
                    formattedIndex?.let { authorSeries.append(" (#").append(it).append(")") }
                }
                binding.nookAuthorSeries.text = authorSeries.toString()

                if (!b.coverPath.isNullOrBlank() && File(b.coverPath).exists()) {
                    Glide.with(activity).load(File(b.coverPath)).into(binding.nookCover)
                } else {
                    binding.nookCover.setImageResource(R.drawable.ic_book)
                }

                // Progress & Completion Rule (>90% = Finished / 100%)
                val isFinished = b.progress >= 0.90f
                val percent = if (isFinished) 100 else (b.progress * 100f).toInt().coerceIn(0, 100)
                binding.nookProgressPercent.text = if (isFinished) "Completed" else "$percent%"
                binding.nookProgressBar.progress = if (isFinished) 100 else percent

                // Current Location (Center-aligned below progress section)
                binding.nookCurrentLocation.text = if (!b.currentLocation.isNullOrBlank()) "Current location: \"${b.currentLocation}\"" else ""

                // Quick Stats (2 lines, 2 items per line with icons)
                val totalMins = sessions.sumOf { it.activeSeconds } / 60

                // 1. Time Stat
                binding.nookStatTime.text = if (totalMins == 1) "$totalMins min read" else "$totalMins mins read"

                // 2. Highlights Stat
                binding.nookStatHighlights.text = if (highlights.size == 1) "1 highlight" else "${highlights.size} highlights"

                // 3. Reading Stamps Stat
                binding.nookStatStamps.text = if (stampList.size == 1) "1 reading stamp" else "${stampList.size} reading stamps"

                // 4. Journal Entries Stat
                binding.nookStatJournals.text = if (journalList.size == 1) "1 journal entry" else "${journalList.size} journal entries"

                // Status Chips (Authoritative, non-clickable state handled by app)
                val unread = !isFinished && !b.isCurrentlyReading && b.progress == 0f
                val reading = !isFinished && (b.isCurrentlyReading || b.progress > 0f)
                styleStatusChip(binding.chipStatusUnread, unread)
                styleStatusChip(binding.chipStatusReading, reading)
                styleStatusChip(binding.chipStatusFinished, isFinished)

                // Render current tab content
                renderTabContent(journalList, stampList, highlights, sessions)
            }
        }
    }

    private fun styleStatusChip(chip: TextView, active: Boolean) {
        if (active) {
            chip.setBackgroundResource(R.drawable.chip_bg_purple)
            chip.setTextColor(ContextCompat.getColor(activity, R.color.black))
        } else {
            chip.setBackgroundResource(R.drawable.chip_bg)
            chip.setTextColor(ContextCompat.getColor(activity, R.color.white))
        }
    }

    private fun scrollToTab(btn: View) {
        val scrollView = activity.findViewById<android.widget.HorizontalScrollView>(R.id.nookTabsScroll) ?: return
        scrollView.post {
            val scrollX = btn.left - (scrollView.width - btn.width) / 2
            scrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun setupTabs() {
        val tabs = listOf(binding.tabOverview, binding.tabJournal, binding.tabStamps, binding.tabHighlights)
        tabs.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                currentTab = index
                isJournalFormVisible = false
                isStampFormVisible = false
                updateTabStyles()
                scrollToTab(btn)
                loadBook()
            }
        }
        updateTabStyles()
    }

    private fun updateTabStyles() {
        val tabs = listOf(binding.tabOverview, binding.tabJournal, binding.tabStamps, binding.tabHighlights)
        tabs.forEachIndexed { index, btn ->
            if (index == currentTab) {
                btn.setBackgroundColor(ContextCompat.getColor(activity, R.color.palette_violet))
                btn.setTextColor(Color.WHITE)
                btn.iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE)
                scrollToTab(btn)
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(ContextCompat.getColor(activity, R.color.palette_plum))
                btn.iconTint = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.palette_plum))
            }
        }
    }

    private fun renderTabContent(
        journalList: List<ReadingJournalEntity>,
        stampList: List<ReadingStampEntity>,
        highlights: List<com.epubreader.app.data.HighlightEntity>,
        sessions: List<com.epubreader.app.data.ReadingSessionEntity>
    ) {
        binding.nookTabContentContainer.removeAllViews()
        val inflater = LayoutInflater.from(activity)

        when (currentTab) {
            0 -> {
                val v = inflater.inflate(R.layout.nook_tab_overview, binding.nookTabContentContainer, false)
                setupOverviewTab(v, journalList, stampList, sessions)
                binding.nookTabContentContainer.addView(v)
            }
            1 -> {
                val v = inflater.inflate(R.layout.nook_tab_journal, binding.nookTabContentContainer, false)
                setupJournalTab(v, journalList)
                binding.nookTabContentContainer.addView(v)
            }
            2 -> {
                val v = inflater.inflate(R.layout.nook_tab_stamps, binding.nookTabContentContainer, false)
                setupStampsTab(v, stampList)
                binding.nookTabContentContainer.addView(v)
            }
            3 -> {
                val v = inflater.inflate(R.layout.nook_tab_highlights, binding.nookTabContentContainer, false)
                setupHighlightsTab(v, highlights)
                binding.nookTabContentContainer.addView(v)
            }
        }
    }

    private fun setupOverviewTab(
        v: View,
        journalList: List<ReadingJournalEntity>,
        stampList: List<ReadingStampEntity>,
        sessions: List<com.epubreader.app.data.ReadingSessionEntity>
    ) {
        val btnAddStamp = v.findViewById<TextView>(R.id.btnAddStamp)
        val stampsContainer = v.findViewById<LinearLayout>(R.id.overviewStampsContainer)
        val btnNewEntry = v.findViewById<TextView>(R.id.btnNewJournalEntry)
        val journalContainer = v.findViewById<LinearLayout>(R.id.overviewJournalContainer)
        val historyListLayout = v.findViewById<LinearLayout>(R.id.overviewHistoryListLayout)
        val btnToggleHistory = v.findViewById<TextView>(R.id.btnToggleHistory)

        btnAddStamp.setOnClickListener { currentTab = 2; isJournalFormVisible = false; isStampFormVisible = true; updateTabStyles(); loadBook() }
        btnNewEntry.setOnClickListener { currentTab = 1; isJournalFormVisible = true; isStampFormVisible = false; updateTabStyles(); loadBook() }

        val filteredSessions = sessions.filter { it.activeSeconds >= 300 }.take(5)

        if (filteredSessions.isEmpty()) {
            btnToggleHistory?.visibility = View.GONE
        } else {
            btnToggleHistory?.visibility = View.VISIBLE
            var isHistoryHidden = false
            btnToggleHistory?.setOnClickListener {
                isHistoryHidden = !isHistoryHidden
                historyListLayout.visibility = if (isHistoryHidden) View.GONE else View.VISIBLE
                btnToggleHistory.text = if (isHistoryHidden) {
                    activity.getString(R.string.reading_session_show)
                } else {
                    activity.getString(R.string.reading_session_hide)
                }
            }
        }

        stampsContainer.removeAllViews()
        if (stampList.isEmpty()) {
            val emptyTv = TextView(activity).apply {
                text = activity.getString(R.string.nook_no_stamps)
                setTextColor(ContextCompat.getColor(activity, R.color.palette_slate))
                textSize = 13f
            }
            stampsContainer.addView(emptyTv)
        } else {
            stampList.take(2).forEach { stamp ->
                val card = LayoutInflater.from(activity).inflate(R.layout.item_stamp_card, stampsContainer, false) as com.google.android.material.card.MaterialCardView
                card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110.dpToPx()).apply {
                    bottomMargin = 8.dpToPx()
                }
                val theme = getStampTheme(stamp.stampType)
                card.setCardBackgroundColor(Color.parseColor(theme.bgColorHex))
                card.strokeColor = Color.parseColor(theme.borderColorHex)
                card.findViewById<TextView>(R.id.stampCardTitle).apply {
                    text = stamp.stampType
                    setTextColor(Color.parseColor(theme.titleColorHex))
                }
                val noteTv = card.findViewById<TextView>(R.id.stampCardNote)
                if (!stamp.note.isNullOrBlank()) {
                    noteTv.visibility = View.VISIBLE
                    noteTv.text = "\"${stamp.note}\""
                } else {
                    noteTv.visibility = View.GONE
                }
                card.findViewById<TextView>(R.id.stampCardDate).text = DateFormat.getMediumDateFormat(activity).format(Date(stamp.createdAt))
                card.findViewById<View>(R.id.btnDeleteStamp).visibility = View.GONE
                card.setOnClickListener { currentTab = 2; updateTabStyles(); loadBook() }
                stampsContainer.addView(card)
            }
        }

        journalContainer.removeAllViews()
        if (journalList.isEmpty()) {
            val emptyTv = TextView(activity).apply {
                text = activity.getString(R.string.nook_no_journal)
                setTextColor(ContextCompat.getColor(activity, R.color.palette_slate))
                textSize = 13f
            }
            journalContainer.addView(emptyTv)
        } else {
            journalList.take(2).forEach { entry ->
                val entryView = LayoutInflater.from(activity).inflate(R.layout.item_overview_journal, journalContainer, false)
                entryView.findViewById<TextView>(R.id.entryTitle).text = entry.title
                entryView.findViewById<TextView>(R.id.entryDate).text = DateFormat.getMediumDateFormat(activity).format(Date(entry.createdAt))
                entryView.findViewById<TextView>(R.id.entryPreview).text = entry.content
                entryView.setOnClickListener { currentTab = 1; updateTabStyles(); loadBook() }
                journalContainer.addView(entryView)
            }
        }

        historyListLayout.removeAllViews()
        if (filteredSessions.isEmpty()) {
            val emptyTv = TextView(activity).apply {
                text = activity.getString(R.string.nook_no_sessions)
                setTextColor(ContextCompat.getColor(activity, R.color.palette_slate))
                textSize = 13f
            }
            historyListLayout.addView(emptyTv)
        } else {
            filteredSessions.forEach { session ->
                val sessView = LayoutInflater.from(activity).inflate(R.layout.item_overview_session, historyListLayout, false)
                sessView.findViewById<TextView>(R.id.sessionDate).text = DateFormat.getMediumDateFormat(activity).format(Date(session.startedAt))
                val mins = session.activeSeconds / 60
                sessView.findViewById<TextView>(R.id.sessionDuration).text = "$mins mins"
                sessView.findViewById<TextView>(R.id.sessionAdvanced).text = "Advanced ${session.chaptersAdvanced} chapters"
                historyListLayout.addView(sessView)
            }
        }
    }

    private fun setupJournalTab(v: View, journalList: List<ReadingJournalEntity>) {
        val btnExport = v.findViewById<View>(R.id.btnExportJournal)
        btnExport?.visibility = if (journalList.isNotEmpty()) View.VISIBLE else View.GONE
        btnExport?.setOnClickListener { exportJournal(journalList) }

        val btnWrite = v.findViewById<MaterialButton>(R.id.btnWriteJournalEntry)
        val formLayout = v.findViewById<View>(R.id.journalFormLayout)
        val listContainer = v.findViewById<LinearLayout>(R.id.journalListContainer)
        val btnSave = v.findViewById<MaterialButton>(R.id.btnSaveJournal)
        val btnCancel = v.findViewById<MaterialButton>(R.id.btnCancelJournal)
        val btnDiscard = v.findViewById<MaterialButton>(R.id.btnDiscardJournal)
        val editTitle = v.findViewById<TextInputEditText>(R.id.inputJournalTitle)
        val editContent = v.findViewById<TextInputEditText>(R.id.inputJournalContent)

        formLayout.visibility = if (isJournalFormVisible) View.VISIBLE else View.GONE
        btnWrite.setOnClickListener { isJournalFormVisible = true; formLayout.visibility = View.VISIBLE }
        val cancelJournal = { isJournalFormVisible = false; formLayout.visibility = View.GONE; editTitle.text?.clear(); editContent.text?.clear() }
        btnCancel.setOnClickListener { cancelJournal() }
        btnDiscard?.setOnClickListener { cancelJournal() }

        btnSave.setOnClickListener {
            val title = editTitle.text?.toString()?.trim().orEmpty()
            val content = editContent.text?.toString()?.trim().orEmpty()
            if (title.isBlank() && content.isBlank()) return@setOnClickListener

            activity.lifecycleScope.launch(Dispatchers.IO) {
                db.readingJournalDao().insert(ReadingJournalEntity(bookId = bookId, title = title.ifBlank { "Reflection" }, content = content))
                withContext(Dispatchers.Main) {
                    isJournalFormVisible = false
                    formLayout.visibility = View.GONE
                    editTitle.text?.clear()
                    editContent.text?.clear()
                    loadBook()
                }
            }
        }

        listContainer.removeAllViews()
        if (journalList.isEmpty()) {
            val emptyTv = TextView(activity).apply {
                text = activity.getString(R.string.nook_no_journal_entries)
                setTextColor(ContextCompat.getColor(activity, R.color.palette_slate))
                textSize = 14f
                setPadding(0, 32, 0, 32)
            }
            listContainer.addView(emptyTv)
        } else {
            journalList.forEach { entry ->
                val card = LayoutInflater.from(activity).inflate(R.layout.item_journal_card, listContainer, false)
                card.findViewById<TextView>(R.id.cardTitle).text = entry.title
                card.findViewById<TextView>(R.id.cardDate).text = DateFormat.getMediumDateFormat(activity).format(Date(entry.createdAt))
                card.findViewById<TextView>(R.id.cardContent).text = entry.content
                card.findViewById<View>(R.id.btnDeleteEntry).setOnClickListener {
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        db.readingJournalDao().delete(entry)
                        withContext(Dispatchers.Main) { loadBook() }
                    }
                }
                listContainer.addView(card)
            }
        }
    }

    private fun setupStampsTab(v: View, stampList: List<ReadingStampEntity>) {
        val btnAffix = v.findViewById<MaterialButton>(R.id.btnAffixStamp)
        val formLayout = v.findViewById<View>(R.id.stampFormLayout)
        val listContainer = v.findViewById<LinearLayout>(R.id.stampListContainer)
        val btnSaveStamp = v.findViewById<MaterialButton>(R.id.btnSaveStamp)
        val btnCancelStamp = v.findViewById<MaterialButton>(R.id.btnCancelStamp)
        val btnCancelStampAlt = v.findViewById<MaterialButton>(R.id.btnCancelStampAlt)
        val noteInput = v.findViewById<TextInputEditText>(R.id.inputStampNote)

        formLayout.visibility = if (isStampFormVisible) View.VISIBLE else View.GONE
        btnAffix.setOnClickListener { isStampFormVisible = true; formLayout.visibility = View.VISIBLE }
        val cancelStamp = { isStampFormVisible = false; formLayout.visibility = View.GONE; noteInput.text?.clear() }
        btnCancelStamp.setOnClickListener { cancelStamp() }
        btnCancelStampAlt?.setOnClickListener { cancelStamp() }

        val stampTypes = listOf(
            Triple(activity.getString(R.string.nook_stamp_started), activity.getString(R.string.nook_stamp_desc_started), "Started Reading"),
            Triple(activity.getString(R.string.nook_stamp_resumed), activity.getString(R.string.nook_stamp_desc_resumed), "Resumed"),
            Triple(activity.getString(R.string.nook_stamp_favorite), activity.getString(R.string.nook_stamp_desc_favorite), "Favorite Moment"),
            Triple(activity.getString(R.string.nook_stamp_memorable), activity.getString(R.string.nook_stamp_desc_memorable), "Memorable Passage"),
            Triple(activity.getString(R.string.nook_stamp_revisited), activity.getString(R.string.nook_stamp_desc_revisited), "Revisited"),
            Triple(activity.getString(R.string.nook_stamp_finished), activity.getString(R.string.nook_stamp_desc_finished), "Finished Reading"),
            Triple(activity.getString(R.string.nook_stamp_reread), activity.getString(R.string.nook_stamp_desc_reread), "Reread in Progress")
        )

        val stampSelectorContainer = v.findViewById<LinearLayout>(R.id.stampSelectorContainer)
        stampSelectorContainer?.orientation = LinearLayout.VERTICAL
        stampSelectorContainer?.removeAllViews()

        val inflater = LayoutInflater.from(activity)

        for (i in stampTypes.indices step 2) {
            val row = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 8.dpToPx()
                }
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }

            val item1 = stampTypes[i]
            val card1 = inflater.inflate(R.layout.item_stamp_selector_card, row, false).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 4.dpToPx(), 0)
                }
            }
            bindStampSelectorCard(card1, item1.first, item1.second, item1.third == selectedStampType) {
                selectedStampType = item1.third
                setupStampsTab(v, stampList)
                formLayout.visibility = View.VISIBLE
            }
            row.addView(card1)

            if (i + 1 < stampTypes.size) {
                val item2 = stampTypes[i + 1]
                val card2 = inflater.inflate(R.layout.item_stamp_selector_card, row, false).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(4.dpToPx(), 0, 0, 0)
                    }
                }
                bindStampSelectorCard(card2, item2.first, item2.second, item2.third == selectedStampType) {
                    selectedStampType = item2.third
                    setupStampsTab(v, stampList)
                    formLayout.visibility = View.VISIBLE
                }
                row.addView(card2)
            } else {
                val spacer = View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(spacer)
            }
            stampSelectorContainer?.addView(row)
        }

        btnSaveStamp.setOnClickListener {
            val note = noteInput.text?.toString()?.trim()
            activity.lifecycleScope.launch(Dispatchers.IO) {
                db.readingStampDao().insert(ReadingStampEntity(bookId = bookId, stampType = selectedStampType, note = note))
                withContext(Dispatchers.Main) {
                    isStampFormVisible = false
                    formLayout.visibility = View.GONE
                    noteInput.text?.clear()
                    loadBook()
                }
            }
        }

        listContainer.removeAllViews()
        if (stampList.isEmpty()) {
            val emptyTv = TextView(activity).apply {
                text = activity.getString(R.string.nook_no_stamps_affixed)
                setTextColor(ContextCompat.getColor(activity, R.color.palette_slate))
                textSize = 14f
                setPadding(0, 32, 0, 32)
            }
            listContainer.addView(emptyTv)
        } else {
            stampList.forEach { stamp ->
                val card = LayoutInflater.from(activity).inflate(R.layout.item_stamp_card, listContainer, false) as com.google.android.material.card.MaterialCardView
                val theme = getStampTheme(stamp.stampType)
                card.setCardBackgroundColor(Color.parseColor(theme.bgColorHex))
                card.strokeColor = Color.parseColor(theme.borderColorHex)
                card.findViewById<TextView>(R.id.stampCardTitle).apply {
                    text = stamp.stampType
                    setTextColor(Color.parseColor(theme.titleColorHex))
                }
                val noteTv = card.findViewById<TextView>(R.id.stampCardNote)
                if (!stamp.note.isNullOrBlank()) {
                    noteTv.visibility = View.VISIBLE
                    noteTv.text = "\"${stamp.note}\""
                } else {
                    noteTv.visibility = View.GONE
                }
                card.findViewById<TextView>(R.id.stampCardDate).text = "Just now"
                card.findViewById<View>(R.id.btnDeleteStamp).setOnClickListener {
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        db.readingStampDao().delete(stamp)
                        withContext(Dispatchers.Main) { loadBook() }
                    }
                }
                listContainer.addView(card)
            }
        }
    }

    private fun bindStampSelectorCard(cardView: View, title: String, desc: String, isSelected: Boolean, onClick: () -> Unit) {
        val theme = getStampTheme(title)
        val card = cardView as com.google.android.material.card.MaterialCardView
        card.setCardBackgroundColor(if (isSelected) Color.parseColor(theme.bgColorHex) else Color.parseColor("#FAF7F5"))
        card.strokeWidth = if (isSelected) (2 * activity.resources.displayMetrics.density).toInt() else (1 * activity.resources.displayMetrics.density).toInt()
        card.strokeColor = Color.parseColor(if (isSelected) "#D88C9A" else "#F1E3D3")
        card.setOnClickListener { onClick() }

        card.findViewById<TextView>(R.id.selectorTitle).apply {
            text = title
            setTextColor(Color.parseColor(theme.titleColorHex))
        }
        card.findViewById<TextView>(R.id.selectorDesc).text = desc
    }

    private fun setupHighlightsTab(v: View, highlights: List<com.epubreader.app.data.HighlightEntity>) {
        val btnReader = v.findViewById<MaterialButton>(R.id.btnGoToReader)
        val listContainer = v.findViewById<LinearLayout>(R.id.highlightsListContainer)

        btnReader.setOnClickListener {
            activity.startActivity(
                Intent(activity, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
            )
        }

        listContainer.removeAllViews()
        if (highlights.isEmpty()) {
            val emptyTv = TextView(activity).apply {
                text = activity.getString(R.string.nook_no_highlights)
                setTextColor(ContextCompat.getColor(activity, R.color.palette_slate))
                textSize = 14f
                setPadding(0, 32, 0, 32)
            }
            listContainer.addView(emptyTv)
        } else {
            highlights.forEach { h ->
                val card = LayoutInflater.from(activity).inflate(R.layout.item_nook_highlight_card, listContainer, false)
                card.findViewById<TextView>(R.id.highlightQuote).text = "\"${h.text}\""
                card.findViewById<TextView>(R.id.highlightDate).text = DateFormat.getMediumDateFormat(activity).format(Date(h.createdAt))
                val noteView = card.findViewById<TextView>(R.id.highlightNote)
                val noteBox = card.findViewById<View>(R.id.highlightNoteBox)
                if (!h.note.isNullOrBlank()) {
                    noteBox.visibility = View.VISIBLE
                    noteView.text = h.note
                } else {
                    noteBox.visibility = View.GONE
                }
                val accentLine = card.findViewById<View>(R.id.highlightAccentLine)
                accentLine?.setBackgroundColor(h.color)
                listContainer.addView(card)
            }
        }
    }

    private fun exportJournal(journalList: List<ReadingJournalEntity>) {
        val b = book ?: return
        val text = buildString {
            append("Reading Journal for: ").append(b.title).append("\n\n")
            journalList.forEach { entry ->
                append("• ").append(entry.title).append(" (").append(DateFormat.getMediumDateFormat(activity).format(Date(entry.createdAt))).append(")\n")
                append(entry.content).append("\n\n")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Journal: ${b.title}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "Export Journal"))
    }
}
