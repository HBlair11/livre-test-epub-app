package com.epubreader.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.MainActivity
import com.epubreader.app.R
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.DictionaryHistoryEntity
import com.epubreader.app.util.TtsPronouncer
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VocabularyViewController(private val activity: MainActivity) {

    private var vocabAdapter: VocabularyAdapter? = null
    private var vocabFullList: List<DictionaryHistoryEntity> = emptyList()
    private var vocabFlashcardIndex: Int = 0
    private var vocabFlashcardFlipped: Boolean = false
    private var ttsPronouncer: TtsPronouncer? = null

    fun show(vocabContainer: ViewGroup, isFeatureEnabled: Boolean) {
        val requiredTag = if (isFeatureEnabled) "vocab_full" else "vocab_default"
        if (vocabContainer.tag != requiredTag) {
            vocabContainer.removeAllViews()
            val layoutRes =
                if (isFeatureEnabled) R.layout.view_main_vocabulary else R.layout.view_main_vocabulary_default
            activity.layoutInflater.inflate(layoutRes, vocabContainer, true)
            vocabContainer.tag = requiredTag
            vocabAdapter = null
        }

        val vocabRoot = vocabContainer
        if (ttsPronouncer == null) {
            ttsPronouncer = TtsPronouncer(activity)
        }
        val tabs = vocabRoot.findViewById<TabLayout>(R.id.vocabTabs)
        val wordsContainer = vocabRoot.findViewById<View>(R.id.vocabWordsTabContainer)
        val flashcardContainer = vocabRoot.findViewById<View>(R.id.vocabFlashcardContainer)
        val searchLayout = vocabRoot.findViewById<TextInputLayout>(R.id.vocabSearchLayout)
        val searchEdit = vocabRoot.findViewById<TextInputEditText>(R.id.vocabSearchEdit)
        val chipGroup = vocabRoot.findViewById<ChipGroup>(R.id.vocabFilterChipGroup)
        val recycler = vocabRoot.findViewById<RecyclerView>(R.id.vocabRecycler)
        val emptyText = vocabRoot.findViewById<View>(R.id.vocabEmptyText)
        val actionsRow = vocabRoot.findViewById<View>(R.id.vocabActionsRow)
        val subtitleTv = vocabRoot.findViewById<TextView>(R.id.vocabSubtitleText)

        // Reset session state on entrance
        searchEdit?.text?.clear()
        searchLayout?.isEndIconVisible = false
        chipGroup?.check(R.id.chipAll)
        tabs?.getTabAt(0)?.select()
        wordsContainer?.visibility = View.VISIBLE
        flashcardContainer?.visibility = View.GONE
        vocabFlashcardIndex = 0
        vocabFlashcardFlipped = false
        vocabRoot.findViewById<View>(R.id.flashcardCardView)?.rotationY = 0f
        vocabRoot.findViewById<View>(R.id.flashcardFrontSide)?.visibility = View.VISIBLE
        vocabRoot.findViewById<View>(R.id.flashcardBackSide)?.visibility = View.GONE

        if (vocabAdapter == null || vocabAdapter?.showMastery != isFeatureEnabled) {
            vocabAdapter = VocabularyAdapter(
                showMastery = isFeatureEnabled,
                onMasteryCycle = { item ->
                    val nextMastery = (item.masteryLevel + 1) % 3
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.get(activity.applicationContext).dictionaryHistoryDao()
                            .updateMasteryLevel(item.id, nextMastery)
                    }
                },
                onPronounce = { word ->
                    ttsPronouncer?.speak(word)
                },
                onDelete = { item ->
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.get(activity.applicationContext).dictionaryHistoryDao().delete(item)
                    }
                }
            )
        }
        recycler?.layoutManager = LinearLayoutManager(activity)
        recycler?.adapter = vocabAdapter

        // Tab switcher
        tabs?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
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
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

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

        searchEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                searchLayout?.isEndIconVisible = query.isNotEmpty()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchLayout?.setEndIconOnClickListener {
            searchEdit?.text?.clear()
            searchEdit?.clearFocus()
            hideKeyboard(searchEdit)
            searchLayout.isEndIconVisible = false
            applyFilters()
        }

        searchEdit?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                hideKeyboard(searchEdit)
                searchEdit.clearFocus()
                true
            } else {
                false
            }
        }

        val updateChipStyles = {
            for (i in 0 until (chipGroup?.childCount ?: 0)) {
                val chip = chipGroup?.getChildAt(i) as? Chip
                if (chip != null) {
                    if (chip.isChecked) {
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(activity, R.color.palette_violet)
                        )
                        chip.setTextColor(ContextCompat.getColor(activity, R.color.white))
                    } else {
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(activity, R.color.palette_plum)
                        )
                        chip.setTextColor(ContextCompat.getColor(activity, R.color.palette_mint))
                    }
                }
            }
        }
        updateChipStyles()

        chipGroup?.setOnCheckedStateChangeListener { _, _ ->
            updateChipStyles()
            applyFilters()
        }

        vocabRoot.findViewById<View>(R.id.vocabExportCsv)?.setOnClickListener { exportVocab(0, vocabRoot) }
        vocabRoot.findViewById<View>(R.id.vocabExportMarkdown)?.setOnClickListener { exportVocab(1, vocabRoot) }
        vocabRoot.findViewById<View>(R.id.vocabClearAll)?.setOnClickListener { confirmClearVocab(vocabRoot) }

        // Flashcard controls
        val flashcardCard = vocabRoot.findViewById<View>(R.id.flashcardCardView)
        val flashcardPrev = vocabRoot.findViewById<View>(R.id.flashcardPrevBtn)
        val flashcardNext = vocabRoot.findViewById<View>(R.id.flashcardNextBtn)
        val flashcardLearning = vocabRoot.findViewById<MaterialButton>(R.id.flashcardLearningBtn)
        val flashcardMastered = vocabRoot.findViewById<MaterialButton>(R.id.flashcardMasteredBtn)
        val flashcardAudioBtn = vocabRoot.findViewById<View>(R.id.flashcardAudioBtn)

        flashcardLearning?.setOnClickListener {
            if (vocabFullList.isNotEmpty() && vocabFlashcardIndex in vocabFullList.indices) {
                val current = vocabFullList[vocabFlashcardIndex]
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(activity.applicationContext).dictionaryHistoryDao().updateMasteryLevel(current.id, 1)
                }
            }
        }

        flashcardMastered?.setOnClickListener {
            if (vocabFullList.isNotEmpty() && vocabFlashcardIndex in vocabFullList.indices) {
                val current = vocabFullList[vocabFlashcardIndex]
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(activity.applicationContext).dictionaryHistoryDao().updateMasteryLevel(current.id, 2)
                }
            }
        }

        flashcardAudioBtn?.setOnClickListener {
            if (vocabFullList.isNotEmpty() && vocabFlashcardIndex in vocabFullList.indices) {
                val current = vocabFullList[vocabFlashcardIndex]
                if (ttsPronouncer == null) {
                    ttsPronouncer = TtsPronouncer(activity)
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

        AppDatabase.get(activity).dictionaryHistoryDao().observeAll().asLiveData().observe(activity) { list ->
            vocabFullList = list
            applyFilters()
            if (vocabFlashcardIndex >= list.size) vocabFlashcardIndex = (list.size - 1).coerceAtLeast(0)
            renderFlashcard(vocabRoot)

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
        val flashcardLearning = vocabRoot.findViewById<MaterialButton>(R.id.flashcardLearningBtn)
        val flashcardMastered = vocabRoot.findViewById<MaterialButton>(R.id.flashcardMasteredBtn)

        progressTv?.text =
            vocabRoot.context.getString(R.string.vocab_flashcard_progress, vocabFlashcardIndex + 1, vocabFullList.size)

        wordTv?.text = current.word.uppercase()
        phoneticTv?.text = current.phonetic ?: "/${current.word.lowercase()}/"
        posTv?.text = current.partOfSpeech?.uppercase() ?: "WORD"
        defTv?.text = current.definition ?: activity.getString(R.string.dictionary_not_found)
        exampleTv?.text = if (!current.example.isNullOrBlank()) "\"${current.example}\"" else ""

        updateFlashcardMasteryState(current, flashcardLearning, flashcardMastered, vocabRoot.context)
    }

    private fun updateFlashcardMasteryState(
        current: DictionaryHistoryEntity,
        learningBtn: MaterialButton?,
        masteredBtn: MaterialButton?,
        context: Context
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

    private fun confirmClearVocab(vocabRoot: View) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.vocabulary_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(activity.applicationContext).dictionaryHistoryDao().clear()
                }
                Snackbar.make(vocabRoot, R.string.vocabulary_cleared, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportVocab(format: Int, vocabRoot: View) {
        val words = vocabAdapter?.currentList ?: return
        if (words.isEmpty()) return
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val (fileName, mime, content) = when (format) {
            1 -> Triple("vocabulary-$timestamp.md", "text/markdown", buildVocabMarkdown(words))
            else -> Triple("vocabulary-$timestamp.csv", "text/csv", buildVocabCsv(words))
        }
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val file = File(activity.cacheDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            withContext(Dispatchers.Main) {
                shareVocabFile(file, mime)
                Snackbar.make(vocabRoot, R.string.vocabulary_exported, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildVocabCsv(words: List<DictionaryHistoryEntity>): String = buildString {
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

    private fun buildVocabMarkdown(words: List<DictionaryHistoryEntity>): String = buildString {
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

    private fun shareVocabFile(file: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.vocabulary_title)))
    }

    private fun hideKeyboard(view: View? = null) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val targetToken = view?.windowToken ?: activity.currentFocus?.windowToken
        if (targetToken != null) {
            imm?.hideSoftInputFromWindow(targetToken, 0)
        }
    }

    fun destroy() {
        ttsPronouncer?.shutdown()
        ttsPronouncer = null
    }
}
