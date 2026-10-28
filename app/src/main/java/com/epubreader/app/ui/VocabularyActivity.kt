package com.epubreader.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.epubreader.app.R
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.DictionaryHistoryEntity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VocabularyActivity : AppCompatActivity() {

    private lateinit var vocabAdapter: VocabularyAdapter
    private var vocabFullList: List<DictionaryHistoryEntity> = emptyList()
    private var vocabFlashcardIndex: Int = 0
    private var vocabFlashcardFlipped: Boolean = false
    private var ttsPronouncer: com.epubreader.app.util.TtsPronouncer? = null

    // Layout references
    private var vocabTabs: com.google.android.material.tabs.TabLayout? = null
    private var wordsContainer: View? = null
    private var flashcardContainer: View? = null
    private var searchLayout: TextInputLayout? = null
    private var searchEdit: com.google.android.material.textfield.TextInputEditText? = null
    private var chipGroup: com.google.android.material.chip.ChipGroup? = null
    private var recycler: androidx.recyclerview.widget.RecyclerView? = null
    private var emptyText: View? = null
    private var actionsRow: View? = null
    private var subtitleTv: TextView? = null

    // Shared filter update layout loop
    private val applyFilters = {
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
        vocabAdapter.submitList(filtered)
        emptyText?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recycler?.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        actionsRow?.visibility = if (vocabFullList.isEmpty()) View.GONE else View.VISIBLE

        val total = vocabFullList.size
        val count = filtered.size
        val wordOrWords =
            if (count == 1) getString(R.string.vocab_label_book) else getString(R.string.vocab_label_books)
        subtitleTv?.text = if (filtered.size == vocabFullList.size) {
            getString(
                R.string.vocab_heading_subtitle,
                total,
                if (total == 1) getString(R.string.vocab_label_book) else getString(R.string.vocab_label_books)
            )
        } else {
            "$count matching $wordOrWords (filtered from $total)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = com.epubreader.app.data.PrefsManager(this)
        val isFeatureEnabled = prefs.enableVocabMasteryAndFlashcards
        val layoutRes = if (isFeatureEnabled) R.layout.view_main_vocabulary else R.layout.view_main_vocabulary_default
        setContentView(layoutRes)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            ?.setNavigationOnClickListener { finish() }

        // Bind layout views
        vocabTabs = findViewById(R.id.vocabTabs)
        wordsContainer = findViewById(R.id.vocabWordsTabContainer)
        flashcardContainer = findViewById(R.id.vocabFlashcardContainer)
        searchLayout = findViewById<TextInputLayout>(R.id.vocabSearchLayout)
        searchEdit = findViewById(R.id.vocabSearchEdit)
        chipGroup = findViewById(R.id.vocabFilterChipGroup)
        recycler = findViewById(R.id.vocabRecycler)
        emptyText = findViewById(R.id.vocabEmptyText)
        actionsRow = findViewById(R.id.vocabActionsRow)
        subtitleTv = findViewById(R.id.vocabSubtitleText)

        if (ttsPronouncer == null) {
            ttsPronouncer = com.epubreader.app.util.TtsPronouncer(this)
        }

        vocabAdapter = VocabularyAdapter(
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

        recycler?.layoutManager = LinearLayoutManager(this)
        recycler?.adapter = vocabAdapter

        // Rule 3 — Tab toggling resets screen directly
        vocabTabs?.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                resetVocabScreenToDefault()

                if (tab?.position == 1) {
                    wordsContainer?.visibility = View.GONE
                    flashcardContainer?.visibility = View.VISIBLE
                    renderFlashcard()
                } else {
                    wordsContainer?.visibility = View.VISIBLE
                    flashcardContainer?.visibility = View.GONE
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        // Rule 1 — Explicitly show/hide cancel button ONLY when typing text content changes
        searchEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                searchLayout?.isEndIconVisible = query.isNotEmpty()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchEdit?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                hideKeyboard(searchEdit)
                searchEdit?.clearFocus()
                true
            } else {
                false
            }
        }

        // Rule 4 — Clicking Cancel Icon Clears Input Text, Hides Keyboard, & Removes Focus explicitly
        searchLayout?.setEndIconOnClickListener {
            resetVocabScreenToDefault()
        }

        chipGroup?.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? com.google.android.material.chip.Chip
                if (chip != null) {
                    if (chip.id == checkedId) {
                        chip.chipBackgroundColor =
                            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.palette_violet))
                        chip.setTextColor(ContextCompat.getColor(this, R.color.black))
                    } else {
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.palette_plum)
                        )
                        chip.setTextColor(ContextCompat.getColor(this, R.color.palette_mint))
                    }
                }
            }
            applyFilters()
        }

        for (i in 0 until (chipGroup?.childCount ?: 0)) {
            val chip = chipGroup?.getChildAt(i) as? com.google.android.material.chip.Chip
            if (chip != null) {
                if (chip.isChecked) {
                    chip.chipBackgroundColor =
                        android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.palette_violet))
                    chip.setTextColor(ContextCompat.getColor(this, R.color.black))
                } else {
                    chip.chipBackgroundColor =
                        android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.palette_plum))
                    chip.setTextColor(ContextCompat.getColor(this, R.color.palette_mint))
                }
            }
        }

        findViewById<View>(R.id.vocabExportCsv)?.setOnClickListener { export(0) }
        findViewById<View>(R.id.vocabExportMarkdown)?.setOnClickListener { export(1) }
        findViewById<View>(R.id.vocabClearAll)?.setOnClickListener { confirmClearVocab() }

        // 3D Card Configuration layout loops
        val flashcardCard = findViewById<View>(R.id.flashcardCardView)
        val flashcardPrev = findViewById<View>(R.id.flashcardPrevBtn)
        val flashcardNext = findViewById<View>(R.id.flashcardNextBtn)
        val flashcardLearning = findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardLearningBtn)
        val flashcardMastered = findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardMasteredBtn)
        val flashcardAudioBtn = findViewById<View>(R.id.flashcardAudioBtn)

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
            val front = findViewById<View>(R.id.flashcardFrontSide)
            val back = findViewById<View>(R.id.flashcardBackSide)
            val scale = resources.displayMetrics.density
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
                findViewById<View>(R.id.flashcardFrontSide)?.visibility = View.VISIBLE
                findViewById<View>(R.id.flashcardBackSide)?.visibility = View.GONE
                renderFlashcard()
            }
        }

        flashcardNext?.setOnClickListener {
            if (vocabFullList.isNotEmpty()) {
                vocabFlashcardIndex = (vocabFlashcardIndex + 1) % vocabFullList.size
                vocabFlashcardFlipped = false
                flashcardCard?.rotationY = 0f
                findViewById<View>(R.id.flashcardFrontSide)?.visibility = View.VISIBLE
                findViewById<View>(R.id.flashcardBackSide)?.visibility = View.GONE
                renderFlashcard()
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
            renderFlashcard()
        }

        // CRITICAL INITIALIZATION FIX FOR CUSTOM MODE: Force hide icon on screen startup
        searchLayout?.isEndIconVisible = false
    }

    // Rule 3 & 4 — Clean helper reset loop that handles visibility states directly
    private var isResettingScreen = false

    private fun resetVocabScreenToDefault() {
        if (isResettingScreen) return
        isResettingScreen = true
        try {
            searchEdit?.text?.clear()
            chipGroup?.check(R.id.chipAll)
            searchEdit?.clearFocus()
            hideKeyboard(searchEdit)
            searchLayout?.isEndIconVisible = false // Explicitly hide close icon on clean resets
            applyFilters()

            // Reset tabs to Word Sanctuary (Tab 0) & update container visibility
            if (vocabTabs?.selectedTabPosition != 0) {
                vocabTabs?.getTabAt(0)?.select()
            }
            wordsContainer?.visibility = View.VISIBLE
            flashcardContainer?.visibility = View.GONE

            // Reset flashcard progress, flip state, and 3D card rotation back to Card 1
            vocabFlashcardIndex = 0
            vocabFlashcardFlipped = false
            val flashcardCard = findViewById<View>(R.id.flashcardCardView)
            flashcardCard?.rotationY = 0f
            findViewById<View>(R.id.flashcardFrontSide)?.visibility = View.VISIBLE
            findViewById<View>(R.id.flashcardBackSide)?.visibility = View.GONE
            renderFlashcard()
        } finally {
            isResettingScreen = false
        }
    }

    private fun renderFlashcard() {
        if (vocabFullList.isEmpty()) return
        val current = vocabFullList[vocabFlashcardIndex.coerceIn(0, vocabFullList.size - 1)]
        val progressTv = findViewById<TextView>(R.id.flashcardProgressText)
        val wordTv = findViewById<TextView>(R.id.flashcardWord)
        val phoneticTv = findViewById<TextView>(R.id.flashcardPhonetic)
        val posTv = findViewById<TextView>(R.id.flashcardPos)
        val defTv = findViewById<TextView>(R.id.flashcardDef)
        val exampleTv = findViewById<TextView>(R.id.flashcardExample)
        val flashcardLearning = findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardLearningBtn)
        val flashcardMastered = findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardMasteredBtn)

        progressTv?.text = getString(R.string.vocab_flashcard_progress, vocabFlashcardIndex + 1, vocabFullList.size)
        wordTv?.text = current.word.uppercase()
        phoneticTv?.text = current.phonetic ?: "/${current.word.lowercase()}/"
        posTv?.text = current.partOfSpeech?.uppercase() ?: "WORD"
        defTv?.text = current.definition ?: getString(R.string.dictionary_not_found)
        exampleTv?.text =
            if (!current.example.isNullOrBlank()) getString(R.string.vocab_quote_format, current.example) else ""

        updateFlashcardMasteryState(current, flashcardLearning, flashcardMastered, this)
    }

    private fun updateFlashcardMasteryState(
        current: DictionaryHistoryEntity,
        learningBtn: com.google.android.material.button.MaterialButton?,
        masteredBtn: com.google.android.material.button.MaterialButton?,
        context: android.content.Context
    ) {
        val accentPop = ContextCompat.getColor(context, R.color.palette_violet)
        val black = ContextCompat.getColor(context, R.color.black)
        val white = ContextCompat.getColor(context, R.color.white)
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
        AlertDialog.Builder(this)
            .setTitle(R.string.vocabulary_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().clear()
                }
                Snackbar.make(findViewById(android.R.id.content), R.string.vocabulary_cleared, Snackbar.LENGTH_SHORT)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun export(format: Int) {
        val words = vocabAdapter.currentList
        if (words.isEmpty()) return
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val (fileName, mime, content) = when (format) {
            1 -> Triple("vocabulary-$timestamp.md", "text/markdown", buildMarkdown(words))
            else -> Triple("vocabulary-$timestamp.csv", "text/csv", buildCsv(words))
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(cacheDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            withContext(Dispatchers.Main) {
                share(file, mime)
                Snackbar.make(findViewById(android.R.id.content), R.string.vocabulary_exported, Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun buildCsv(words: List<DictionaryHistoryEntity>): String = buildString {
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

    private fun buildMarkdown(words: List<DictionaryHistoryEntity>): String = buildString {
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

    private fun share(file: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(
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

    // Rule 2 — Clicking Outside Drops Focus, Closes Keyboards, & Explicitly RETAINS close icon state if text is active
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is android.widget.EditText) {
                val outRect = android.graphics.Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    hideKeyboard(v)

                    // Enforce Rule 2 manually: explicitly maintain visibility based on actual text buffer contents
                    val textStr = searchEdit?.text?.toString()?.trim() ?: ""
                    searchLayout?.isEndIconVisible = textStr.isNotEmpty()
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

    // Rule 3 (Continued) — Moving away from screen or returning completely flushes the query states back to baseline
    override fun onPause() {
        super.onPause()
        resetVocabScreenToDefault()
    }

    override fun onResume() {
        super.onResume()
        resetVocabScreenToDefault()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        resetVocabScreenToDefault()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsPronouncer?.shutdown()
        ttsPronouncer = null
    }
}
