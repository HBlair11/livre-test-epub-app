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

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecentlyAddedViewController {

    private var activityMain: MainActivity? = null
    private var activityStandalone: AppCompatActivity? = null
    private var rootView: View? = null

    constructor(activity: MainActivity) {
        this.activityMain = activity
    }

    constructor(activity: AppCompatActivity, rootView: View) {
        this.activityStandalone = activity
        this.rootView = rootView
        setupStandalone(activity, rootView)
    }

    fun setupHeader(shelfContainer: View?, count: Int) {
        val act = activityMain ?: return
        if (shelfContainer == null) return
        val tvHeaderTitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val headerCountText = shelfContainer.findViewById<TextView>(R.id.headerCountText)

        tvHeaderTitle?.text = act.getString(R.string.recently_added_screen_title)
        tvHeaderSubtitle?.text = act.getString(R.string.recently_added_heading_subtitle)
        if (headerCountText != null) {
            headerCountText.visibility = View.VISIBLE
            headerCountText.text = "$count ${if (count == 1) "book" else "books"}"
        }
    }

    private fun setupStandalone(activity: AppCompatActivity, root: View) {
        val tvHeaderTitle = root.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = root.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val headerCountText = root.findViewById<TextView>(R.id.headerCountText)
        val recycler = root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)

        tvHeaderTitle?.text = activity.getString(R.string.recently_added_screen_title)
        tvHeaderSubtitle?.text = activity.getString(R.string.recently_added_heading_subtitle)

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
            AppDatabase.get(activity).bookDao().observeHomeRecentlyAddedTop50().collectLatest { books ->
                bookAdapter.submitList(books)
                headerCountText?.text = "${books.size} ${if (books.size == 1) "book" else "books"}"
            }
        }
    }
}
