package com.epubreader.app.ui

import android.view.View
import android.widget.TextView
import com.epubreader.app.MainActivity
import com.epubreader.app.R

class AuthorsViewController(private val activity: MainActivity) {

    fun setupHeader(shelfContainer: View?, isDetail: Boolean, authorName: String?, count: Int) {
        if (shelfContainer == null) return
        val tvHeaderTitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val headerCountText = shelfContainer.findViewById<TextView>(R.id.headerCountText)

        if (isDetail && authorName != null) {
            tvHeaderTitle?.text = "AUTHOR: ${authorName.uppercase()}"
            tvHeaderSubtitle?.text = ""
            headerCountText?.visibility = View.GONE
        } else {
            tvHeaderTitle?.text = activity.getString(R.string.authors_heading_title)
            tvHeaderSubtitle?.text = activity.getString(R.string.authors_heading_subtitle)
            if (headerCountText != null) {
                headerCountText.visibility = View.VISIBLE
                headerCountText.text = "$count ${if (count == 1) "author" else "authors"}"
            }
        }
    }
}
