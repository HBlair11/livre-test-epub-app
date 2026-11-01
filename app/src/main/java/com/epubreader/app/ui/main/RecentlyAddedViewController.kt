package com.epubreader.app.ui

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.epubreader.app.MainActivity
import com.epubreader.app.R
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecentlyAddedViewController {

    private var activityMain: MainActivity? = null
    private var activityStandalone: AppCompatActivity? = null
    private var rootView: View? = null

    constructor(activity: MainActivity) {
        this.activityMain = activity
    }

    constructor(activity: AppCompatActivity, rootView: View, ids: List<Long> = emptyList(), sourceResId: Int = R.string.nav_home) {
        this.activityStandalone = activity
        this.rootView = rootView
        setupStandalone(activity, rootView, ids, sourceResId)
    }

    fun setupHeader(shelfContainer: View?, count: Int, isScanNow: Boolean = false) {
        val act = activityMain ?: return
        if (shelfContainer == null) return
        val tvHeaderTitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val headerCountText = shelfContainer.findViewById<TextView>(R.id.headerCountText)

        tvHeaderTitle?.text = act.getString(R.string.recently_added_screen_title)
        tvHeaderSubtitle?.text = if (isScanNow) act.getString(R.string.recently_added_scanned_heading_subtitle) else act.getString(R.string.recently_added_heading_subtitle)
        if (headerCountText != null) {
            headerCountText.visibility = View.VISIBLE
            headerCountText.text = "$count ${if (count == 1) "book" else "books"}"
        }
    }

    private fun setupStandalone(activity: AppCompatActivity, root: View, ids: List<Long>, sourceResId: Int) {
        val headerContainer = root.findViewById<android.widget.FrameLayout>(R.id.headerContainer)
        headerContainer?.removeAllViews()
        val inflated = android.view.LayoutInflater.from(activity).inflate(R.layout.header_secondary_category_3, headerContainer, true)
        val toolbar = inflated.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        activity.setSupportActionBar(toolbar)
        toolbar?.setNavigationOnClickListener {
            activity.finish()
        }

        val toolbarSubtitle = inflated.findViewById<TextView>(R.id.toolbarSubtitle)
        val toolbarTitle = inflated.findViewById<TextView>(R.id.toolbarTitle)
        toolbarSubtitle?.text = activity.getString(sourceResId)
        toolbarTitle?.text = activity.getString(R.string.header_recently_added)

        val tvHeaderTitle = root.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = root.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val headerCountText = root.findViewById<TextView>(R.id.headerCountText)
        val recycler = root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)

        tvHeaderTitle?.text = activity.getString(R.string.recently_added_screen_title)
        tvHeaderSubtitle?.text = if (ids.isNotEmpty() || sourceResId != R.string.nav_home) activity.getString(R.string.recently_added_scanned_heading_subtitle) else activity.getString(R.string.recently_added_heading_subtitle)

        val bookAdapter = BookAdapter(
            grid = false,
            onClick = { book ->
                val intent = Intent(activity, com.epubreader.app.ReaderActivity::class.java).apply {
                    putExtra("book_id", book.id)
                }
                activity.startActivity(intent)
            },
            onLongClick = { false },
            onDetails = { book ->
                val intent = Intent(activity, BookDetailsActivity::class.java).apply {
                    putExtra(BookDetailsActivity.EXTRA_BOOK_ID, book.id)
                }
                activity.startActivity(intent)
            }
        )

        recycler?.layoutManager = LinearLayoutManager(activity)
        recycler?.adapter = bookAdapter

        activity.lifecycleScope.launch {
            val bookFlow = if (ids.isNotEmpty()) {
                AppDatabase.get(activity).bookDao().observeByIds(ids)
            } else {
                AppDatabase.get(activity).bookDao().observeHomeRecentlyAddedTop50()
            }
            bookFlow.collectLatest { books ->
                val sorted = books.sortedWith(compareByDescending<BookEntity> { it.addedDate }.thenByDescending { it.id })
                bookAdapter.submitList(sorted)
                headerCountText?.text = "${sorted.size} ${if (sorted.size == 1) "book" else "books"}"
            }
        }
    }
}
