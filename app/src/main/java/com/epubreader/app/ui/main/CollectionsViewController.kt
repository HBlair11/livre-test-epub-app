package com.epubreader.app.ui

import android.view.View
import android.widget.TextView
import com.epubreader.app.MainActivity
import com.epubreader.app.R

class CollectionsViewController(private val activity: MainActivity) {

    fun setupHeader(shelfContainer: View?, count: Int) {
        if (shelfContainer == null) return
        val tvHeaderTitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = shelfContainer.findViewById<TextView>(R.id.tvHeaderSubtitle)
        val headerCountText = shelfContainer.findViewById<TextView>(R.id.headerCountText)

        tvHeaderTitle?.text = "COLLECTIONS"
        tvHeaderSubtitle?.text = "Manage custom book collections"
        if (headerCountText != null) {
            headerCountText.visibility = View.VISIBLE
            headerCountText.text = "$count ${if (count == 1) "collection" else "collections"}"
        }
    }
}
