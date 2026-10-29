package com.epubreader.app.ui

import android.view.View
import android.widget.TextView
import com.epubreader.app.MainActivity
import com.epubreader.app.R

class LibraryViewController(private val activity: MainActivity) {

    fun setupHeader(shelfContainer: View?, view: ShelfView, count: Int) {
        if (shelfContainer == null) return
        val tvHeaderTitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val headerCountText = shelfContainer.findViewById<TextView>(R.id.headerCountText)

        when (view) {
            is ShelfView.Favorites -> {
                tvHeaderTitle?.text = activity.getString(R.string.nav_favorites)
                tvHeaderSubtitle?.text = "Your starred and favorite books"
            }
            is ShelfView.Reading -> {
                tvHeaderTitle?.text = activity.getString(R.string.nav_currently_reading)
                tvHeaderSubtitle?.text = "Books currently in progress"
            }
            else -> {
                tvHeaderTitle?.text = activity.getString(R.string.nav_library)
                tvHeaderSubtitle?.text = activity.getString(R.string.library_heading_subtitle)
            }
        }
        if (headerCountText != null) {
            headerCountText.visibility = View.VISIBLE
            headerCountText.text = "$count ${if (count == 1) "book" else "books"}"
        }
    }
}
