package com.epubreader.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.ReaderActivity
import com.epubreader.app.R
import com.epubreader.app.epub.TocEntry

class TocViewController(
    private val activity: ReaderActivity,
    private val rootContainer: ViewGroup,
    private val onTocItemClick: (TocEntry) -> Unit
) {

    private var tocRv: RecyclerView? = null
    private var tocEmpty: TextView? = null
    private var tocContainer: View? = null
    private var isInitialized = false

    fun initView() {
        if (isInitialized) return
        isInitialized = true

        val tocView = LayoutInflater.from(activity).inflate(R.layout.overlay_list, rootContainer, false)
        tocContainer = tocView
        tocRv = tocView.findViewById(R.id.recycler)
        tocEmpty = tocView.findViewById(R.id.emptyText)
        tocRv!!.layoutManager = LinearLayoutManager(activity)
        tocRv!!.adapter = TocAdapter { entry ->
            onTocItemClick(entry)
        }
        rootContainer.addView(tocView)
    }

    fun show(toc: List<TocEntry>?) {
        initView()
        tocContainer?.visibility = View.VISIBLE
        val isEmpty = toc.isNullOrEmpty()
        tocEmpty?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        tocEmpty?.text = activity.getString(R.string.no_toc)

        val adapter = tocRv?.adapter as? TocAdapter
        adapter?.submitList(toc ?: emptyList())
    }

    fun hide() {
        tocContainer?.visibility = View.GONE
    }
}
