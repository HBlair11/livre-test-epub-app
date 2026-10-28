package com.epubreader.app

import com.epubreader.app.util.SystemBarController

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookRepository
import com.epubreader.app.databinding.ActivitySearchBinding
import com.epubreader.app.ui.BookAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var repo: BookRepository
    private val adapter =
        BookAdapter(grid = false, onClick = { openBook(it) }, onLongClick = { false }, onDetails = { openDetails(it) })

    private var currentBooks: List<BookEntity> = emptyList()
    private var searchJob: Job? = null
    private var lastSearchedQuery = ""

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        repo = BookRepository(applicationContext)
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)
        setSupportActionBar(binding.searchToolbar)
        binding.searchToolbar.setNavigationOnClickListener {
            dismissSearchKeyboard()
            finish()
        }

        binding.searchRecycler.layoutManager = LinearLayoutManager(this)
        binding.searchRecycler.adapter = adapter

        binding.recentScrollView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                v.parent.requestDisallowInterceptTouchEvent(true)
            } else if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            false
        }

        binding.btnClearHistory.setOnClickListener {
            clearAllRecentSearches()
        }

        binding.searchLayout.setEndIconOnClickListener {
            binding.searchEdit.setText("")
            dismissSearchKeyboard()
            binding.searchEdit.clearFocus()
            performSearch("")
        }

        binding.searchEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateVisibility(binding.searchEdit.text?.toString()?.trim() ?: "")
            }
        }

        // Keyboard does NOT auto-open on entry (no requestFocus()).

        binding.searchEdit.setOnEditorActionListener { _, _, _ ->
            val q = binding.searchEdit.text?.toString()?.trim() ?: ""
            if (q.isNotBlank()) {
                saveRecentSearch(q)
                performSearch(q)
            } else {
                performSearch("")
            }
            dismissSearchKeyboard()
            false
        }

        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                if (text.isBlank()) {
                    performSearch("")
                } else {
                    if (binding.recentSearchesLayout.visibility != View.GONE) {
                        binding.recentSearchesLayout.visibility = View.GONE
                    }
                    if (binding.searchEmpty.visibility != View.GONE) {
                        binding.searchEmpty.visibility = View.GONE
                    }
                }
            }
        })

        binding.filterChipGroup.setOnCheckedStateChangeListener { _, _ ->
            updateChipStyles()
            val q = binding.searchEdit.text?.toString()?.trim() ?: ""
            if (q.isNotBlank()) {
                filterAndShowResults(currentBooks, q)
            }
        }

        updateChipStyles()
        updateVisibility("")
    }

    override fun onResume() {
        super.onResume()
        updateVisibility(binding.searchEdit.text?.toString()?.trim() ?: "")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (binding.searchEdit.hasFocus()) {
                val location = IntArray(2)
                binding.searchLayout.getLocationOnScreen(location)
                val left = location[0]
                val top = location[1]
                val right = left + binding.searchLayout.width
                val bottom = top + binding.searchLayout.height

                if (ev.rawX < left || ev.rawX > right || ev.rawY < top || ev.rawY > bottom) {
                    binding.searchEdit.clearFocus()
                    dismissSearchKeyboard()
                    updateVisibility(binding.searchEdit.text?.toString()?.trim() ?: "")
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun updateChipStyles() {
        val checkedId = binding.filterChipGroup.checkedChipId
        for (i in 0 until binding.filterChipGroup.childCount) {
            val chip = binding.filterChipGroup.getChildAt(i) as? Chip
            if (chip != null) {
                if (chip.id == checkedId) {
                    chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.palette_violet)
                    )
                    chip.setTextColor(ContextCompat.getColor(this, R.color.palette_eggplant))
                } else {
                    chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.palette_plum)
                    )
                    chip.setTextColor(ContextCompat.getColor(this, R.color.palette_slate))
                }
            }
        }
    }

    private fun performSearch(q: String) {
        lastSearchedQuery = q
        searchJob?.cancel()
        if (q.isBlank()) {
            currentBooks = emptyList()
            adapter.submitList(emptyList())
            updateVisibility("")
            return
        }
        searchJob = lifecycleScope.launch {
            repo.search(q).collectLatest { results ->
                currentBooks = results
                filterAndShowResults(results, q)
            }
        }
    }

    private fun filterAndShowResults(books: List<BookEntity>, q: String) {
        val checkedId = binding.filterChipGroup.checkedChipId
        val filtered = books.filter { book ->
            when (checkedId) {
                R.id.chipTitle -> book.title.contains(q, ignoreCase = true)
                R.id.chipAuthor -> book.author.contains(q, ignoreCase = true)
                R.id.chipSeries -> book.series?.contains(q, ignoreCase = true) == true
                else -> true
            }
        }

        adapter.submitList(filtered)
        updateVisibility(q, filtered.isEmpty() && q.isNotBlank())
    }

    private fun updateVisibility(q: String, hasNoResults: Boolean = false) {
        val recentList = loadRecentSearches()
        if (q.isBlank()) {
            binding.searchRecycler.visibility = View.GONE
            binding.searchNoResultsLayout.visibility = View.GONE

            if (recentList.isNotEmpty()) {
                binding.recentSearchesLayout.visibility = View.VISIBLE
                binding.searchEmpty.visibility = View.GONE
                updateRecentSearchesUi()
            } else {
                binding.recentSearchesLayout.visibility = View.GONE
                binding.searchEmpty.visibility = View.VISIBLE
                centralizeEmptyArea()
            }
        } else {
            binding.recentSearchesLayout.visibility = View.GONE
            binding.searchEmpty.visibility = View.GONE

            if (hasNoResults) {
                binding.searchNoResultsLayout.visibility = View.VISIBLE
                binding.searchRecycler.visibility = View.GONE
            } else {
                binding.searchNoResultsLayout.visibility = View.GONE
                binding.searchRecycler.visibility = View.VISIBLE
            }
        }
    }

    private fun centralizeEmptyArea() {
        binding.searchEmpty.post {
            val parent = binding.searchEmpty.parent as? View ?: return@post
            val parentHeight = parent.height
            val recentVis = binding.recentSearchesLayout.visibility == View.VISIBLE
            val recentHeight = if (recentVis) 140 else 0
            val searchHeaderHeight = binding.searchLayout.height + binding.filterChipGroup.height + 36

            val availableHeight = parentHeight - searchHeaderHeight - recentHeight
            val contentHeight = binding.searchEmpty.measuredHeight

            if (availableHeight > contentHeight) {
                val topPadding = (availableHeight - contentHeight) / 2
                binding.searchEmpty.setPadding(0, topPadding.coerceAtLeast(16), 0, 16)
            }
        }
    }

    private fun updateRecentSearchesUi() {
        val recentList = loadRecentSearches()
        val scrollView = binding.recentScrollView
        val chipGroup = binding.recentChipGroup
        chipGroup.removeAllViews()

        val q = binding.searchEdit.text?.toString()?.trim() ?: ""

        if (recentList.isEmpty() || q.isNotBlank()) {
            binding.recentSearchesLayout.visibility = View.GONE
            if (q.isBlank()) {
                binding.searchEmpty.visibility = View.VISIBLE
                centralizeEmptyArea()
            }
        } else {
            binding.recentSearchesLayout.visibility = View.VISIBLE
            binding.searchEmpty.visibility = View.GONE
            for (term in recentList) {
                val itemView = layoutInflater.inflate(R.layout.item_recent_search, chipGroup, false)
                val queryText = itemView.findViewById<TextView>(R.id.recentQueryText)
                val deleteBtn = itemView.findViewById<View>(R.id.recentDeleteBtn)

                queryText.text = term

                itemView.setOnClickListener {
                    binding.searchEdit.setText(term)
                    binding.searchEdit.setSelection(term.length)
                    binding.searchLayout.endIconMode = TextInputLayout.END_ICON_NONE
                    binding.searchLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                    dismissSearchKeyboard()
                    binding.searchEdit.clearFocus()
                    saveRecentSearch(term)
                    performSearch(term)
                }

                deleteBtn.setOnClickListener {
                    removeRecentSearch(term)
                }

                chipGroup.addView(itemView)
            }

            chipGroup.post {
                if (chipGroup.childCount > 0) {
                    val singleChipHeight = chipGroup.getChildAt(0).measuredHeight
                    val verticalSpacing = chipGroup.chipSpacingVertical

                    val maxFourLinesHeight = (singleChipHeight * 4) + (verticalSpacing * 3)

                    val params = scrollView.layoutParams
                    if (chipGroup.measuredHeight > maxFourLinesHeight) {
                        params.height = maxFourLinesHeight
                    } else {
                        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    scrollView.layoutParams = params
                }
            }
        }
    }

    private fun loadRecentSearches(): List<String> {
        val prefs = getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("recent_searches_json", "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {}
        return list
    }

    private fun saveRecentSearch(queryStr: String) {
        if (queryStr.isBlank()) return
        val list = loadRecentSearches().toMutableList()
        list.remove(queryStr)
        list.add(0, queryStr)
        if (list.size > 20) list.removeAt(list.size - 1)
        val arr = org.json.JSONArray()
        for (item in list) {
            arr.put(item)
        }
        getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("recent_searches_json", arr.toString())
            .apply()
    }

    private fun removeRecentSearch(queryStr: String) {
        val list = loadRecentSearches().toMutableList()
        list.remove(queryStr)
        val arr = org.json.JSONArray()
        for (item in list) {
            arr.put(item)
        }
        getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("recent_searches_json", arr.toString())
            .apply()
        updateRecentSearchesUi()
    }

    private fun clearAllRecentSearches() {
        getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("recent_searches_json")
            .apply()
        updateRecentSearchesUi()
    }

    private fun dismissSearchKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEdit.windowToken, 0)
        binding.searchEdit.clearFocus()
    }

    private fun openBook(book: BookEntity) {
        dismissSearchKeyboard()
        lifecycleScope.launch { repo.markOpened(book.id) }
        startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id))
    }

    private fun openDetails(book: BookEntity) {
        startActivity(
            Intent(this, BookDetailsActivity::class.java).putExtra(
                BookDetailsActivity.EXTRA_BOOK_ID,
                book.id
            )
        )
    }
}
