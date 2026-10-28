package com.epubreader.app.ui

import android.view.View
import android.widget.TextView
import com.epubreader.app.MainActivity
import com.epubreader.app.R

class RecentlyAddedViewController(private val activity: MainActivity) {

    fun setupHeader(shelfContainer: View?, count: Int) {
        if (shelfContainer == null) return
        val tvHeaderTitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val headerCountText = shelfContainer.findViewById<TextView>(R.id.headerCountText)

        tvHeaderTitle?.text = activity.getString(R.string.recently_added_screen_title)
        tvHeaderSubtitle?.text = activity.getString(R.string.recently_added_heading_subtitle)
        if (headerCountText != null) {
            headerCountText.visibility = View.VISIBLE
            headerCountText.text = "$count ${if (count == 1) "book" else "books"}"
        }
    }
}
