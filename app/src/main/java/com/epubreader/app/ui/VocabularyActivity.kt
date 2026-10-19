package com.epubreader.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_main_vocabulary)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            ?.setNavigationOnClickListener { finish() }

        val tabs = findViewById<com.google.android.material.tabs.TabLayout>(R.id.vocabTabs)
        val wordsContainer = findViewById<View>(R.id.vocabWordsTabContainer)
        val flashcardContainer = findViewById<View>(R.id.vocabFlashcardContainer)
        val searchEdit = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.vocabSearchEdit)
        val chipGroup = findViewById<com.google.android.material.chip.ChipGroup>(R.id.vocabFilterChipGroup)
        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.vocabRecycler)
        val emptyText = findViewById<View>(R.id.vocabEmptyText)
        val actionsRow = findViewById<View>(R.id.vocabActionsRow)
        val subtitleTv = findViewById<TextView>(R.id.vocabSubtitleText)

        vocabAdapter = VocabularyAdapter(
            onMasteryCycle = { item ->
                val nextMastery = (item.masteryLevel + 1) % 3
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().updateMasteryLevel(item.id, nextMastery)
                }
            },
            onPronounce = { word ->
                Toast.makeText(this, getString(R.string.vocab_pronouncing, word), Toast.LENGTH_SHORT).show()
            },
            onDelete = { item ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().delete(item)
                }
            }
        )

        recycler?.layoutManager = LinearLayoutManager(this)
        recycler?.adapter = vocabAdapter

        // Dual Tab Switcher
        tabs?.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
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

        // Live Search & Chip Filters
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
            vocabAdapter.submitList(filtered)
            emptyText?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            recycler?.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            actionsRow?.visibility = if (vocabFullList.isEmpty()) View.GONE else View.VISIBLE

            val total = vocabFullList.size
            val count = filtered.size
            val wordOrWords = if (count == 1) getString(R.string.vocab_label_book) else getString(R.string.vocab_label_books)
            subtitleTv?.text = if (filtered.size == vocabFullList.size) {
                getString(R.string.vocab_heading_subtitle, total, if (total == 1) getString(R.string.vocab_label_book) else getString(R.string.vocab_label_books))
            } else {
                "$count matching $wordOrWords (filtered from $total)"
            }
        }

        searchEdit?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilters() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        chipGroup?.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? com.google.android.material.chip.Chip
                if (chip != null) {
                    if (chip.id == checkedId) {
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_pop))
                        chip.setTextColor(ContextCompat.getColor(this, R.color.black))
                    } else {
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.palette_plum))
                        chip.setTextColor(ContextCompat.getColor(this, R.color.palette_mint))
                    }
                }
            }
            applyFilters()
        }

        // Initialize chip styles
        for (i in 0 until (chipGroup?.childCount ?: 0)) {
            val chip = chipGroup?.getChildAt(i) as? com.google.android.material.chip.Chip
            if (chip != null) {
                if (chip.isChecked) {
                    chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_pop))
                    chip.setTextColor(ContextCompat.getColor(this, R.color.black))
                } else {
                    chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.palette_plum))
                    chip.setTextColor(ContextCompat.getColor(this, R.color.palette_mint))
                }
            }
        }

        findViewById<View>(R.id.vocabExportCsv)?.setOnClickListener { export(0) }
        findViewById<View>(R.id.vocabExportMarkdown)?.setOnClickListener { export(1) }
        findViewById<View>(R.id.vocabClearAll)?.setOnClickListener { confirmClearVocab() }

        // Flashcard controls
        val flashcardCard = findViewById<View>(R.id.flashcardCardView)
        val flashcardPrev = findViewById<View>(R.id.flashcardPrevBtn)
        val flashcardNext = findViewById<View>(R.id.flashcardNextBtn)
        val flashcardLearning = findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardLearningBtn)
        val flashcardMastered = findViewById<com.google.android.material.button.MaterialButton>(R.id.flashcardMasteredBtn)

        flashcardCard?.setOnClickListener {
            vocabFlashcardFlipped = !vocabFlashcardFlipped
            flashcardCard.animate().rotationY(if (vocabFlashcardFlipped) 180f else 0f).setDuration(350).start()
            val front = findViewById<View>(R.id.flashcardFrontSide)
            val back = findViewById<View>(R.id.flashcardBackSide)
            flashcardCard.postDelayed({
                front?.visibility = if (vocabFlashcardFlipped) View.GONE else View.VISIBLE
                back?.visibility = if (vocabFlashcardFlipped) View.VISIBLE else View.GONE
                if (vocabFlashcardFlipped) back?.rotationY = 180f
            }, 175)
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

        progressTv?.text = getString(R.string.vocab_flashcard_progress, vocabFlashcardIndex + 1, vocabFullList.size)
        wordTv?.text = current.word.uppercase()
        phoneticTv?.text = current.phonetic ?: "/${current.word.lowercase()}/"
        posTv?.text = current.partOfSpeech?.uppercase() ?: "WORD"
        defTv?.text = current.definition ?: getString(R.string.dictionary_not_found)
        exampleTv?.text = if (!current.example.isNullOrBlank()) getString(R.string.vocab_quote_format, current.example) else ""
    }

    private fun confirmClearVocab() {
        AlertDialog.Builder(this)
            .setTitle(R.string.vocabulary_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(applicationContext).dictionaryHistoryDao().clear()
                }
                Snackbar.make(findViewById(android.R.id.content), R.string.vocabulary_cleared, Snackbar.LENGTH_SHORT).show()
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
                Snackbar.make(findViewById(android.R.id.content), R.string.vocabulary_exported, Snackbar.LENGTH_SHORT).show()
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
}
